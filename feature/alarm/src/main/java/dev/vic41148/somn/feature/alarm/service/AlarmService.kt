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
import kotlinx.coroutines.launch

/**
 * Service that manages alarm playback with gradual volume increase.
 */
class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var volumeJob: Job? = null

    companion object {
        const val CHANNEL_ID = "alarm_channel"
        const val NOTIFICATION_ID = 2001

        private val _isAlarmFiring = MutableStateFlow(false)
        val isAlarmFiring: StateFlow<Boolean> = _isAlarmFiring.asStateFlow()

        private val _currentAlarmLabel = MutableStateFlow("")
        val currentAlarmLabel: StateFlow<String> = _currentAlarmLabel.asStateFlow()

        private val _currentCaptchaType = MutableStateFlow("NONE")
        val currentCaptchaType: StateFlow<String> = _currentCaptchaType.asStateFlow()

        fun dismiss(context: Context) {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = "DISMISS"
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "DISMISS" -> {
                stopAlarm()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            "SNOOZE" -> {
                stopAlarm()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                // Snooze handled by the UI/ViewModel
            }
            else -> {
                startForeground(NOTIFICATION_ID, createNotification())

                val label = intent?.getStringExtra(AlarmReceiver.EXTRA_ALARM_LABEL) ?: "Alarm"
                val vibrationEnabled = intent?.getBooleanExtra(AlarmReceiver.EXTRA_VIBRATION, true) ?: true
                val gradualSeconds = intent?.getIntExtra(AlarmReceiver.EXTRA_GRADUAL_SECONDS, 60) ?: 60
                val captchaType = intent?.getStringExtra(AlarmReceiver.EXTRA_CAPTCHA_TYPE) ?: "NONE"

                _currentAlarmLabel.value = label
                _currentCaptchaType.value = captchaType
                _isAlarmFiring.value = true

                startAlarm(vibrationEnabled, gradualSeconds)
            }
        }
        return START_NOT_STICKY
    }

    private fun startAlarm(vibrationEnabled: Boolean, gradualSeconds: Int) {
        // Play default alarm sound with gradual volume
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

            // Gradually increase volume
            volumeJob = serviceScope.launch {
                val steps = gradualSeconds * 2  // Update every 500ms
                for (i in 1..steps) {
                    val volume = i.toFloat() / steps
                    mediaPlayer?.setVolume(volume, volume)
                    delay(500)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Vibration
        if (vibrationEnabled) {
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
            .setFullScreenIntent(null, true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopAlarm()
        serviceScope.cancel()
        super.onDestroy()
    }
}
