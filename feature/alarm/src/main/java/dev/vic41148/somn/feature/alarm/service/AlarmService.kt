package dev.vic41148.somn.feature.alarm.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.vic41148.somn.core.data.repository.UserProfileRepository
import dev.vic41148.somn.feature.alarm.receiver.AlarmReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Service that manages alarm playback with gradual volume increase.
 * Uses Hilt for injection.
 */
@AndroidEntryPoint
class AlarmService : Service() {

    @javax.inject.Inject
    lateinit var userProfileRepository: UserProfileRepository

    @javax.inject.Inject
    lateinit var preferencesRepository: dev.vic41148.somn.core.data.repository.SomnPreferencesRepository

    @javax.inject.Inject
    lateinit var backupRepository: dev.vic41148.somn.core.data.repository.BackupRepository

    @javax.inject.Inject
    lateinit var alarmRepository: dev.vic41148.somn.core.data.repository.AlarmRepository

    @javax.inject.Inject
    lateinit var alarmScheduler: dev.vic41148.somn.core.domain.repository.AlarmScheduler

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var volumeJob: Job? = null
    private var wakeConfirmJob: Job? = null
    private var maxSnoozeCount = 3
    private var wakeConfirmAttempts = 0
    private var lastVibrationEnabled = true
    private var lastGradualSeconds = 60
    private var currentAlarmId: Long = -1L

    /** WAKE-01/02: lifecycle of one alarm-firing episode, including the post-dismiss wake check. */
    enum class AlarmPhase { FIRING, AWAITING_WAKE_CONFIRMATION, DISMISSED }

    companion object {
        const val CHANNEL_ID = "alarm_channel"
        const val NOTIFICATION_ID = 2001

        /** Fail-open cap — after this many missed wake confirmations, dismiss outright rather than ring forever. */
        private const val MAX_WAKE_CONFIRM_ATTEMPTS = 3

        private val _isAlarmFiring = MutableStateFlow(false)
        val isAlarmFiring: StateFlow<Boolean> = _isAlarmFiring.asStateFlow()

        private val _currentAlarmLabel = MutableStateFlow("")
        val currentAlarmLabel: StateFlow<String> = _currentAlarmLabel.asStateFlow()

        private val _currentCaptchaType = MutableStateFlow("NONE")
        val currentCaptchaType: StateFlow<String> = _currentCaptchaType.asStateFlow()

        private val _canSnooze = MutableStateFlow(true)
        val canSnooze: StateFlow<Boolean> = _canSnooze.asStateFlow()

        private val _phase = MutableStateFlow(AlarmPhase.FIRING)
        val phase: StateFlow<AlarmPhase> = _phase.asStateFlow()

        private val _wakeConfirmDeadlineMillis = MutableStateFlow<Long?>(null)
        val wakeConfirmDeadlineMillis: StateFlow<Long?> = _wakeConfirmDeadlineMillis.asStateFlow()

        private var snoozeCount = 0

        /** Hard-dismiss, bypassing wake verification entirely. Kept for callers that need an immediate stop. */
        fun dismiss(context: Context) {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = "DISMISS"
            }
            context.startService(intent)
        }

        /** WAKE-01: normal dismiss path — stops the ring and, if wake verification is enabled, starts the confirmation window instead of stopping the service outright. */
        fun requestDismiss(context: Context) {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = "REQUEST_DISMISS"
            }
            context.startService(intent)
        }

        /** User confirmed they're awake within the window — completes the dismiss. */
        fun confirmAwake(context: Context) {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = "CONFIRM_AWAKE"
            }
            context.startService(intent)
        }

        fun snooze(context: Context, durationMinutes: Int = 9) {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = "SNOOZE"
                putExtra("snooze_minutes", durationMinutes)
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        serviceScope.launch {
            preferencesRepository.maxSnoozeCount.collect {
                maxSnoozeCount = it
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "DISMISS" -> {
                serviceScope.launch { performFullDismiss() }
            }
            "REQUEST_DISMISS" -> {
                serviceScope.launch { handleRequestDismiss() }
            }
            "CONFIRM_AWAKE" -> {
                handleConfirmAwake()
            }
            "SNOOZE" -> {
                snoozeCount++
                if (snoozeCount >= maxSnoozeCount) {
                    _canSnooze.value = false
                }
                val snoozeMinutes = intent.getIntExtra("snooze_minutes", 9)
                val alarmIdForSnooze = currentAlarmId
                stopAlarm()
                stopForeground(STOP_FOREGROUND_REMOVE)
                // The comment here used to say "snooze handled by the UI/ViewModel" — it wasn't;
                // neither AlarmViewModel.snoozeAlarm nor anything else ever actually scheduled a
                // re-fire. Tapping Snooze silently killed the alarm forever instead of ringing
                // again after the snooze duration. Re-arm a one-shot trigger for this same alarm
                // id before stopping the service.
                if (alarmIdForSnooze != -1L) {
                    serviceScope.launch(Dispatchers.IO) {
                        val alarm = alarmRepository.getAlarm(alarmIdForSnooze)
                        if (alarm != null) {
                            AlarmReceiver.scheduleAlarm(
                                context = this@AlarmService,
                                alarmId = alarm.id,
                                timeInMillis = System.currentTimeMillis() + snoozeMinutes * 60_000L,
                                label = alarm.label,
                                vibration = alarm.vibrationEnabled,
                                gradualSeconds = alarm.gradualVolumeSeconds,
                                captchaType = alarm.captchaType.name
                            )
                        }
                        stopSelf()
                    }
                } else {
                    stopSelf()
                }
            }
            else -> {
                val notificationManager = getSystemService(NotificationManager::class.java)
                if (android.os.Build.VERSION.SDK_INT >= 34) {
                    if (!notificationManager.canUseFullScreenIntent()) {
                        android.util.Log.w("AlarmService", "Full-screen intent permission revoked by system!")
                        // Optionally show a different high-priority notification or prompt user if possible
                    }
                }
                
                startForeground(NOTIFICATION_ID, createNotification())

                val label = intent?.getStringExtra(AlarmReceiver.EXTRA_ALARM_LABEL) ?: "Alarm"
                val vibrationEnabled = intent?.getBooleanExtra(AlarmReceiver.EXTRA_VIBRATION, true) ?: true
                val gradualSeconds = intent?.getIntExtra(AlarmReceiver.EXTRA_GRADUAL_SECONDS, 60) ?: 60
                val captchaType = intent?.getStringExtra(AlarmReceiver.EXTRA_CAPTCHA_TYPE) ?: "NONE"
                val alarmId = intent?.getLongExtra(AlarmReceiver.EXTRA_ALARM_ID, -1L) ?: -1L

                currentAlarmId = alarmId
                if (alarmId != -1L) {
                    serviceScope.launch(Dispatchers.IO) {
                        val alarm = alarmRepository.getAlarm(alarmId)
                        if (alarm != null) {
                            if (alarm.repeatDays.isEmpty()) {
                                alarmRepository.setEnabled(alarmId, false)
                            } else {
                                // AlarmManager.setAlarmClock() is a one-shot trigger — nothing
                                // else ever re-armed a repeating alarm for its next occurrence
                                // after it fired, so a "repeat every weekday" alarm rang exactly
                                // once, ever, until the user manually re-toggled/edited it or
                                // rebooted the device (which re-schedules via BootReceiver).
                                alarmScheduler.schedule(alarm)
                            }
                        }
                    }
                }

                _currentAlarmLabel.value = label
                _currentCaptchaType.value = captchaType
                _isAlarmFiring.value = true
                _phase.value = AlarmPhase.FIRING
                wakeConfirmJob?.cancel()
                wakeConfirmJob = null
                wakeConfirmAttempts = 0
                lastVibrationEnabled = vibrationEnabled
                lastGradualSeconds = gradualSeconds

                startAlarm(vibrationEnabled, gradualSeconds)
            }
        }
        return START_NOT_STICKY
    }

    private fun startAlarm(vibrationEnabled: Boolean, gradualSeconds: Int) {
        serviceScope.launch {
            val profile = userProfileRepository.getProfile()
            val asdMode = profile?.neurodivergentProfile?.asdMode == true

            // If ASD mode is on, force vibration only, no sound
            val playSound = !asdMode
            var soundStarted = false

            if (playSound) {
                // prepare()/start() block synchronously — run off Dispatchers.Main so a slow or
                // stuck ringtone provider can't ANR the exact moment the alarm is meant to fire.
                soundStarted = withContext(Dispatchers.IO) {
                    try {
                        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

                        mediaPlayer = MediaPlayer().apply {
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_ALARM)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .build()
                            )
                            setDataSource(this@AlarmService, alarmUri)
                            isLooping = true
                            setVolume(0f, 0f)
                            prepare()
                            start()
                        }
                        true
                    } catch (e: Exception) {
                        android.util.Log.e("AlarmService", "Alarm sound failed to start, falling back to vibration", e)
                        mediaPlayer?.release()
                        mediaPlayer = null
                        false
                    }
                }

                if (soundStarted) {
                    // Gradually increase volume
                    volumeJob = launch {
                        val steps = gradualSeconds * 2  // Update every 500ms
                        for (i in 1..steps) {
                            val volume = i.toFloat() / steps
                            mediaPlayer?.setVolume(volume, volume)
                            delay(500)
                        }
                    }
                }
            }

            // Vibration — also forced on when sound failed to start (or was never attempted due
            // to ASD mode) so a broken/missing ringtone never leaves the alarm completely silent.
            val finalVibrationEnabled = vibrationEnabled || asdMode || !soundStarted
            if (finalVibrationEnabled) {
                vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vm.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }

                val pattern = longArrayOf(0, 500, 500, 500, 500, 500)
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            }
        }
    }

    /** WAKE-01: stops the ring and either starts a wake-confirmation countdown or dismisses outright. */
    private suspend fun handleRequestDismiss() {
        val enabled = preferencesRepository.wakeVerificationEnabled.first()
        if (!enabled || wakeConfirmAttempts >= MAX_WAKE_CONFIRM_ATTEMPTS) {
            performFullDismiss()
            return
        }

        val windowSeconds = preferencesRepository.wakeVerificationWindowSeconds.first()
        stopAlarm()
        _phase.value = AlarmPhase.AWAITING_WAKE_CONFIRMATION
        _wakeConfirmDeadlineMillis.value = System.currentTimeMillis() + windowSeconds * 1000L

        wakeConfirmJob = serviceScope.launch {
            delay(windowSeconds * 1000L)
            // WAKE-02: window elapsed without confirmation — re-ring via the CAPTCHA engine,
            // unless the fail-open cap has been hit, in which case dismiss rather than ring forever.
            wakeConfirmAttempts++
            _wakeConfirmDeadlineMillis.value = null
            if (wakeConfirmAttempts >= MAX_WAKE_CONFIRM_ATTEMPTS) {
                performFullDismiss()
            } else {
                reRing()
            }
        }
    }

    private fun handleConfirmAwake() {
        wakeConfirmJob?.cancel()
        wakeConfirmJob = null
        _wakeConfirmDeadlineMillis.value = null
        serviceScope.launch { performFullDismiss() }
    }

    private fun reRing() {
        _phase.value = AlarmPhase.FIRING
        _isAlarmFiring.value = true
        startAlarm(lastVibrationEnabled, lastGradualSeconds)
    }

    private suspend fun performFullDismiss() {
        wakeConfirmJob?.cancel()
        wakeConfirmJob = null
        snoozeCount = 0
        _canSnooze.value = true
        _phase.value = AlarmPhase.DISMISSED
        _wakeConfirmDeadlineMillis.value = null
        stopAlarm()
        stopForeground(STOP_FOREGROUND_REMOVE)
        backupRepository.performSilentBackup()
        stopSelf()
    }

    private fun stopAlarm() {
        volumeJob?.cancel()
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null

        vibrator?.cancel()
        vibrator = null

        _isAlarmFiring.value = false
        _currentAlarmLabel.value = ""
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Alarm",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alarm notifications"
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⏰ Alarm")
            .setContentText("Time to wake up!")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(
                android.app.PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, dev.vic41148.somn.feature.alarm.ui.AlarmActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                ),
                true
            )
            .setContentIntent(
                android.app.PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, dev.vic41148.somn.feature.alarm.ui.AlarmActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopAlarm()
        serviceScope.cancel()
        super.onDestroy()
    }
}
