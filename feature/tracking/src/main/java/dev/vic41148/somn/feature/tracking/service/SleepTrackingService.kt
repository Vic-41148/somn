package dev.vic41148.somn.feature.tracking.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import android.content.pm.PackageManager
import dev.vic41148.somn.core.audio.AudioCollector
import dev.vic41148.somn.core.audio.AudioEventClassifier
import dev.vic41148.somn.core.audio.BreathingRateEstimator
import dev.vic41148.somn.core.audio.SnoringNudgeController
import dev.vic41148.somn.core.audio.SonarCollector
import dev.vic41148.somn.core.audio.YamnetAudioClassifier
import dev.vic41148.somn.core.data.repository.AlarmRepository
import dev.vic41148.somn.core.data.repository.SleepRepository
import dev.vic41148.somn.core.data.repository.SomnPreferencesRepository
import dev.vic41148.somn.core.domain.model.SleepEpoch
import dev.vic41148.somn.core.domain.model.SleepStage
import dev.vic41148.somn.core.domain.model.Alarm
import dev.vic41148.somn.core.domain.model.TrackingMode
import dev.vic41148.somn.core.domain.usecase.ClassifySleepStageUseCase
import dev.vic41148.somn.core.domain.usecase.SmartAlarmUseCase
import dev.vic41148.somn.feature.alarm.receiver.AlarmReceiver
import dev.vic41148.somn.feature.alarm.service.AlarmService
import dev.vic41148.somn.core.audio.sensor.AccelerometerCollector
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that manages the overnight sleep tracking session.
 *
 * Supports two movement sensor modes:
 *  - [TrackingMode.ACCELEROMETER] — phone accelerometer (default, low battery)
 *  - [TrackingMode.SONAR] — ultrasonic Doppler sonar (contactless, higher battery)
 *
 * The active mode is selected at session start via [ACTION_START] extras and
 * never switched mid-session (except automatic SNR-based fallback from sonar).
 */
@AndroidEntryPoint
class SleepTrackingService : Service() {

    @Inject lateinit var sleepRepository: SleepRepository
    @Inject lateinit var alarmRepository: AlarmRepository
    @Inject lateinit var smartAlarmUseCase: SmartAlarmUseCase
    @Inject lateinit var preferencesRepository: SomnPreferencesRepository

    private var snoreNudgeEnabled = true

    private val classifyStage = ClassifySleepStageUseCase()
    // Rebuilt per-session in startTracking() once yamnetClassificationEnabled is read — starts
    // ZCR-only so there's never a window where this is uninitialized.
    private var audioEventClassifier = AudioEventClassifier()
    private var yamnetClassifier: YamnetAudioClassifier? = null
    private val breathingRateEstimator = BreathingRateEstimator()
    private lateinit var accelerometerCollector: AccelerometerCollector
    private lateinit var audioCollector: AudioCollector
    private lateinit var snoringNudgeController: SnoringNudgeController
    private lateinit var sonarCollector: SonarCollector

    private var brpmSum = 0
    private var brpmCount = 0
    private var skipNextEpoch = false

    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var collectionJob: Job? = null
    private var audioJob: Job? = null

    private var nextAlarm: Alarm? = null
    private var nextAlarmTimeMillis: Long? = null

    companion object {
        const val CHANNEL_ID      = "sleep_tracking_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START    = "dev.vic41148.somn.action.START_TRACKING"
        const val ACTION_STOP     = "dev.vic41148.somn.action.STOP_TRACKING"
        const val EXTRA_SESSION_ID    = "session_id"
        const val EXTRA_TRACKING_MODE = "tracking_mode"

        private val _trackingState = MutableStateFlow(TrackingState.IDLE)
        val trackingState: StateFlow<TrackingState> = _trackingState.asStateFlow()

        private val _currentSessionId = MutableStateFlow<Long?>(null)
        val currentSessionId: StateFlow<Long?> = _currentSessionId.asStateFlow()

        private val _currentAvgBrpm = MutableStateFlow<Float?>(null)
        val currentAvgBrpm: StateFlow<Float?> = _currentAvgBrpm.asStateFlow()

        private val _activeTrackingMode = MutableStateFlow(TrackingMode.ACCELEROMETER)
        val activeTrackingMode: StateFlow<TrackingMode> = _activeTrackingMode.asStateFlow()

        private val _sonarCalibrationState = MutableStateFlow(
            SonarCollector.SonarCalibrationState.IDLE)
        val sonarCalibrationState: StateFlow<SonarCollector.SonarCalibrationState> =
            _sonarCalibrationState.asStateFlow()

        /** True if the microphone failed to initialize this session — audio events/BRPM/snoring nudge won't fire. */
        private val _audioRecordingFailed = MutableStateFlow(false)
        val audioRecordingFailed: StateFlow<Boolean> = _audioRecordingFailed.asStateFlow()

        fun startTracking(
            context: Context,
            sessionId: Long,
            mode: TrackingMode = TrackingMode.ACCELEROMETER
        ) {
            val intent = Intent(context, SleepTrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_TRACKING_MODE, mode.name)
            }
            context.startForegroundService(intent)
        }

        fun stopTracking(context: Context) {
            val intent = Intent(context, SleepTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        accelerometerCollector = AccelerometerCollector(this)
        audioCollector         = AudioCollector(this)
        snoringNudgeController = SnoringNudgeController(this)
        sonarCollector         = SonarCollector(this)
        createNotificationChannel()
        serviceScope.launch {
            preferencesRepository.snoreNudgeEnabled.collect { snoreNudgeEnabled = it }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1)
                val modeName  = intent.getStringExtra(EXTRA_TRACKING_MODE) ?: TrackingMode.ACCELEROMETER.name
                val mode      = try { TrackingMode.valueOf(modeName) } catch (_: Exception) { TrackingMode.ACCELEROMETER }
                if (sessionId != -1L) {
                    startForeground(NOTIFICATION_ID, createNotification())
                    startTracking(sessionId, mode)
                }
            }
            ACTION_STOP -> {
                stopTracking()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startTracking(sessionId: Long, mode: TrackingMode) {
        _currentSessionId.value = sessionId
        _trackingState.value    = TrackingState.TRACKING
        _currentAvgBrpm.value   = null
        _activeTrackingMode.value = mode
        _audioRecordingFailed.value = false
        brpmSum       = 0
        brpmCount     = 0
        skipNextEpoch = false

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "Somn::TrackingWakeLock"
        ).apply { acquire(8 * 60 * 60 * 1000L) }

        serviceScope.launch {
            nextAlarm = alarmRepository.getNextAlarm()
            if (nextAlarm != null) {
                val calendar = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, nextAlarm!!.hour)
                    set(java.util.Calendar.MINUTE, nextAlarm!!.minute)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                    if (before(java.util.Calendar.getInstance())) add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
                nextAlarmTimeMillis = calendar.timeInMillis
            }
        }

        // ── Audio pipeline (mic-based events + breathing) ──────────────────────
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            // Only start AudioCollector if NOT in sonar mode (sonar owns the mic)
            if (mode != TrackingMode.SONAR) {
                serviceScope.launch {
                    // Task 14 (AUDIO-01): rebuild the classifier per-session so a mid-session
                    // preference change doesn't need a service restart to take effect. Read
                    // before starting collection so no buffer races the flag on session start.
                    yamnetClassifier?.close()
                    yamnetClassifier = null
                    val useYamnet = try {
                        preferencesRepository.yamnetClassificationEnabled.first()
                    } catch (e: Exception) {
                        false
                    }
                    audioEventClassifier = if (useYamnet) {
                        val classifier = YamnetAudioClassifier(this@SleepTrackingService)
                        yamnetClassifier = classifier
                        AudioEventClassifier(yamnetClassify = classifier::classify)
                    } else {
                        AudioEventClassifier()
                    }

                    audioCollector.start()
                    audioJob = serviceScope.launch { audioCollector.collectLoop() }
                    serviceScope.launch {
                        audioCollector.audioFlow.collect { buffer ->
                            handleAudioBuffer(buffer, sessionId)
                        }
                    }
                }
                serviceScope.launch {
                    audioCollector.recordingFailed.collect {
                        android.util.Log.w("SleepTrackingService",
                            "Microphone failed to initialize — session $sessionId will have no audio events/BRPM")
                        _audioRecordingFailed.value = true
                    }
                }
            }
        }

        // ── Movement collection: sonar OR accelerometer ────────────────────────
        when (mode) {
            TrackingMode.SONAR -> startSonarCollection(sessionId)
            TrackingMode.ACCELEROMETER -> startAccelerometerCollection(sessionId)
        }
    }

    // ─── Sonar movement pipeline ───────────────────────────────────────────────

    private fun startSonarCollection(sessionId: Long) {
        sonarCollector.start()

        // Forward calibration state to companion so UI can observe
        serviceScope.launch {
            sonarCollector.calibrationFlow.collect { state ->
                _sonarCalibrationState.value = state
            }
        }

        // Handle SNR fallback → switch transparently to accelerometer
        serviceScope.launch {
            sonarCollector.fallbackRequired.collect {
                android.util.Log.w("SleepTrackingService",
                    "${SonarCollector.EVENT_TAG_FALLBACK}: switching to accelerometer")
                sonarCollector.stop()
                _activeTrackingMode.value = TrackingMode.ACCELEROMETER
                startAccelerometerCollection(sessionId)
            }
        }

        // Collect sonar epochs (null during calibration — skip those)
        collectionJob = serviceScope.launch {
            sonarCollector.epochFlow.collect { sonarEpoch ->
                sonarEpoch ?: return@collect   // null = calibration window, skip

                val accelEpoch = AccelerometerCollector.EpochData(
                    timestampMillis     = sonarEpoch.timestampMillis,
                    movementMagnitude   = sonarEpoch.movementMagnitude,
                    movementVariability = sonarEpoch.movementVariability
                )
                handleEpoch(accelEpoch, sessionId)
            }
        }
    }

    // ─── Accelerometer movement pipeline ──────────────────────────────────────

    private fun startAccelerometerCollection(sessionId: Long) {
        accelerometerCollector.start()

        // Phone-lifted → immediate AWAKE epoch
        serviceScope.launch {
            accelerometerCollector.liftEvents.collect { liftEvent ->
                if (liftEvent.type == AccelerometerCollector.LiftEventType.PHONE_LIFTED ||
                    liftEvent.type == AccelerometerCollector.LiftEventType.SIGNIFICANT_MOTION) {
                    sleepRepository.insertEpoch(
                        SleepEpoch(
                            sessionId          = sessionId,
                            timestampMillis    = liftEvent.timestampMillis,
                            stage              = SleepStage.AWAKE,
                            movementMagnitude  = 2.0f,
                            movementVariability = 1.0f
                        )
                    )
                    skipNextEpoch = true
                }
            }
        }

        collectionJob = serviceScope.launch {
            accelerometerCollector.epochFlow.collect { epochData ->
                if (skipNextEpoch) { skipNextEpoch = false; return@collect }
                handleEpoch(epochData, sessionId)
            }
        }
    }

    // ─── Shared epoch handler ──────────────────────────────────────────────────

    private suspend fun handleEpoch(epochData: AccelerometerCollector.EpochData, sessionId: Long) {
        val stage = classifyStage(epochData.movementMagnitude, epochData.movementVariability)
        val epoch = SleepEpoch(
            sessionId           = sessionId,
            timestampMillis     = epochData.timestampMillis,
            stage               = stage,
            movementMagnitude   = epochData.movementMagnitude,
            movementVariability = epochData.movementVariability
        )
        sleepRepository.insertEpoch(epoch)

        nextAlarmTimeMillis?.let { alarmTime ->
            if (smartAlarmUseCase.shouldWakeEarly(System.currentTimeMillis(), alarmTime,
                    nextAlarm?.wakeWindowMinutes ?: 30, stage)) {
                fireSmartAlarm()
            }
        }
    }

    // ─── Audio buffer handler ──────────────────────────────────────────────────

    private suspend fun handleAudioBuffer(buffer: ShortArray, sessionId: Long) {
        val result = audioEventClassifier.processBuffer(buffer, sessionId, System.currentTimeMillis())
        if (result != null) {
            var (event, rawBuffer) = result
            if (event.type == dev.vic41148.somn.core.domain.model.AudioEventType.TALK) {
                val dir = java.io.File(filesDir, "sleep_talk")
                if (!dir.exists()) dir.mkdirs()
                val wavFile = java.io.File(dir, "talk_${sessionId}_${event.timestampMillis}.wav")
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    writeWavFile(wavFile, rawBuffer, AudioCollector.SAMPLE_RATE)
                    event = event.copy(clipPath = wavFile.absolutePath)
                    sleepRepository.insertAudioEvent(event)
                }
            } else {
                sleepRepository.insertAudioEvent(event)
                if (event.type == dev.vic41148.somn.core.domain.model.AudioEventType.SNORE && snoreNudgeEnabled) {
                    snoringNudgeController.nudge()
                }
            }
        }

        val brpm = breathingRateEstimator.estimateBrpm(buffer)
        if (brpm != null) {
            brpmSum   += brpm
            brpmCount++
            _currentAvgBrpm.value = brpmSum.toFloat() / brpmCount
            android.util.Log.i("SleepTrackingService",
                "BRPM: $brpm / avg=${_currentAvgBrpm.value}")
            if (brpm < 8) fireLowBreathRateAlarm(brpm)
        }
    }

    // ─── Alarm helpers ─────────────────────────────────────────────────────────

    private fun fireSmartAlarm() {
        val alarm = nextAlarm ?: return
        nextAlarmTimeMillis = null
        AlarmReceiver.cancelAlarm(this, alarm.id)
        val si = Intent(this, AlarmService::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_ALARM_ID,       alarm.id)
            putExtra(AlarmReceiver.EXTRA_ALARM_LABEL,    "Smart Wake (Early)")
            putExtra(AlarmReceiver.EXTRA_VIBRATION,      alarm.vibrationEnabled)
            putExtra(AlarmReceiver.EXTRA_GRADUAL_SECONDS, alarm.gradualVolumeSeconds)
            putExtra(AlarmReceiver.EXTRA_CAPTCHA_TYPE,   alarm.captchaType.name)
        }
        startForegroundService(si)
        stopTracking()
    }

    private fun fireLowBreathRateAlarm(brpm: Int) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Low Breath Rate Alert")
            .setContentText("Your breathing rate dropped to $brpm brpm.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(1002, notification)
    }

    // ─── WAV helper ────────────────────────────────────────────────────────────

    private fun writeWavFile(file: java.io.File, data: ShortArray, sampleRate: Int) {
        val channels      = 1
        val byteRate      = 16 * sampleRate * channels / 8
        val totalDataLen  = data.size * 2
        val totalAudioLen = totalDataLen + 36
        java.io.FileOutputStream(file).use { out ->
            val h = ByteArray(44)
            h[0]='R'.code.toByte(); h[1]='I'.code.toByte(); h[2]='F'.code.toByte(); h[3]='F'.code.toByte()
            h[4]=(totalAudioLen and 0xff).toByte(); h[5]=((totalAudioLen shr 8) and 0xff).toByte()
            h[6]=((totalAudioLen shr 16) and 0xff).toByte(); h[7]=((totalAudioLen shr 24) and 0xff).toByte()
            h[8]='W'.code.toByte(); h[9]='A'.code.toByte(); h[10]='V'.code.toByte(); h[11]='E'.code.toByte()
            h[12]='f'.code.toByte(); h[13]='m'.code.toByte(); h[14]='t'.code.toByte(); h[15]=' '.code.toByte()
            h[16]=16; h[17]=0; h[18]=0; h[19]=0; h[20]=1; h[21]=0
            h[22]=channels.toByte(); h[23]=0
            h[24]=(sampleRate and 0xff).toByte(); h[25]=((sampleRate shr 8) and 0xff).toByte()
            h[26]=((sampleRate shr 16) and 0xff).toByte(); h[27]=((sampleRate shr 24) and 0xff).toByte()
            h[28]=(byteRate and 0xff).toByte(); h[29]=((byteRate shr 8) and 0xff).toByte()
            h[30]=((byteRate shr 16) and 0xff).toByte(); h[31]=((byteRate shr 24) and 0xff).toByte()
            h[32]=(2*channels).toByte(); h[33]=0; h[34]=16; h[35]=0
            h[36]='d'.code.toByte(); h[37]='a'.code.toByte(); h[38]='t'.code.toByte(); h[39]='a'.code.toByte()
            h[40]=(totalDataLen and 0xff).toByte(); h[41]=((totalDataLen shr 8) and 0xff).toByte()
            h[42]=((totalDataLen shr 16) and 0xff).toByte(); h[43]=((totalDataLen shr 24) and 0xff).toByte()
            out.write(h)
            val bb = java.nio.ByteBuffer.allocate(data.size * 2)
            bb.order(java.nio.ByteOrder.LITTLE_ENDIAN)
            bb.asShortBuffer().put(data)
            out.write(bb.array())
        }
    }

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    private fun stopTracking() {
        accelerometerCollector.stop()
        audioCollector.stop()
        sonarCollector.stop()
        collectionJob?.cancel()
        audioJob?.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        _trackingState.value = TrackingState.IDLE
        _currentSessionId.value = null
        _currentAvgBrpm.value = null
        _sonarCalibrationState.value = SonarCollector.SonarCalibrationState.IDLE
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Sleep Tracking", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shows while tracking your sleep"; setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sleep Tracking Active")
            .setContentText("Monitoring your sleep…")
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setOngoing(true).setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopTracking()
        yamnetClassifier?.close()
        yamnetClassifier = null
        serviceScope.cancel()
        super.onDestroy()
    }
}

enum class TrackingState { IDLE, TRACKING, PAUSED }
