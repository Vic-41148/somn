package dev.vic41148.somn.feature.tracking.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
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
import dev.vic41148.somn.core.data.model.YamnetModelRepository
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
import kotlinx.coroutines.CoroutineExceptionHandler
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
 *  - [TrackingMode.ACCELEROMETER] - phone accelerometer (default, low battery)
 *  - [TrackingMode.SONAR] - ultrasonic Doppler sonar (contactless, higher battery)
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
    @Inject lateinit var yamnetModelRepository: YamnetModelRepository

    private var snoreNudgeEnabled = true

    private val classifyStage = ClassifySleepStageUseCase()
    // Rebuilt per-session in startTracking() once yamnetClassificationEnabled is read. It starts
    // ZCR-only so there is never a window where this is uninitialized.
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

    // ── Stage smoothing state (3-epoch mode filter) ───────────────────
    // smoothStages() smooths epoch i against [i-1, i, i+1], so an epoch cannot persist
    // until its successor is classified. The latest raw epoch is held back and, on each
    // new epoch, the previous one is written smoothed against [prevprev, prev, current]. The
    // final epoch has no successor and is flushed raw when tracking stops - matching
    // smoothStages()'s "first and last epochs are never replaced" semantics.
    //
    // Who flushes it: the ViewModel's stopTracking() writes it (via the [finalEpoch] companion
    // flow) in the normal user-stop path. The service itself only flushes asynchronously for the
    // non-ViewModel stop paths (smart-alarm early wake, onDestroy). It must NEVER flush with
    // runBlocking on the main thread. That blocks main while Room queries from the ViewModel
    // coroutine are in flight. It wedges Room executors so every later query (including the
    // morning health alerts in notifyMorningAlerts) hangs forever.
    private var pendingRawEpoch: SleepEpoch? = null
    private var prevPrevStage: SleepStage? = null
    private var prevStage: SleepStage? = null

    // True when ACTION_STOP stops this service instance. The ViewModel owns the
    // final-epoch flush in that path, so onDestroy must not race it by flushing too.
    private var stopViaActionStop = false

    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, e ->
            // Any uncaught collector/loop failure (e.g. a Room write error mid-session) must
            // degrade to a logged warning - never take the whole process down.
            android.util.Log.e("SleepTrackingService", "Uncaught tracking coroutine failure", e)
        }
    )
    private var collectionJob: Job? = null
    private var audioJob: Job? = null

    private var nextAlarm: Alarm? = null
    private var nextAlarmTimeMillis: Long? = null

    companion object {
        const val CHANNEL_ID      = "sleep_tracking_channel"
        const val ALERT_CHANNEL_ID = "sleep_alerts_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START    = "dev.vic41148.somn.action.START_TRACKING"
        const val ACTION_STOP     = "dev.vic41148.somn.action.STOP_TRACKING"
        const val EXTRA_SESSION_ID    = "session_id"
        const val EXTRA_TRACKING_MODE = "tracking_mode"
        // Tapping the ongoing tracking notification opens MainActivity with this extra set, so
        // the NavGraph can land straight on the tracking screen (Wake Up button) instead of the
        // Home dead-end. Carried on the launcher intent so the service never imports app classes.
        const val EXTRA_OPEN_TRACKING = "dev.vic41148.somn.extra.OPEN_TRACKING"

        // REL-02: the last epoch held back by the 3-epoch smoothing filter. The ViewModel's
        // stopTracking() and its incomplete-session recovery (finalizeIncompleteSession) read it
        // and write it themselves (see the stage-smoothing note above) so the service never blocks
        // the main thread with a runBlocking flush. The ViewModel clears it after
        // writing, or the service clears it after its own async flush.
        private val _finalEpoch = MutableStateFlow<SleepEpoch?>(null)
        val finalEpoch: StateFlow<SleepEpoch?> = _finalEpoch.asStateFlow()

        fun clearFinalEpoch() {
            _finalEpoch.value = null
        }

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

        /** True if the microphone failed to initialize this session. Audio events/BRPM/snoring nudge will not fire. */
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
                    try {
                        startTrackingForeground()
                        startTracking(sessionId, mode)
                    } catch (e: Exception) {
                        // A failed foreground promotion or synchronous session-start error must
                        // never take the whole app down with it - stop cleanly so the system
                        // does not kill the process for a service that started but never went
                        // foreground. The un-finished session is recovered later by REL-02's
                        // incomplete-session finalization. Coroutine-level failures launched by
                        // startTracking() are absorbed by serviceScope's CoroutineExceptionHandler.
                        android.util.Log.e("SleepTrackingService", "Failed to start tracking session", e)
                        stopSelf()
                        return START_NOT_STICKY
                    }
                }
            }
            ACTION_STOP -> {
                // The ViewModel stopTracking() writes the held-back final epoch itself, so the
                // service must not flush here. Doing so with runBlocking on the main thread used
                // to wedge Room executors while the ViewModel ran concurrent queries. The flag
                // tells onDestroy not to flush either, keeping the VM path fully deterministic.
                stopViaActionStop = true
                stopTracking(flushFinalEpochHere = false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    /**
     * Promotes the service to foreground using the correct [android.app.Service.startForeground]
     * overload for the running API level. The overload dispatch needs an explicit
     * [Build.VERSION.SDK_INT] guard - lint's NewApi check can't prove the three-arg overload
     * (API 29+) is safe from a delegated value, so the two-arg path is taken below Q with an
     * SDK check. The type passed to the three-arg overload is computed by [startForegroundTypeForApi],
     * the single testable seam (locked in by StartForegroundBranchSelectionTest).
     */
    private fun startTrackingForeground() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // API 26-28: the three-arg overload does not exist; the two-arg version is required.
            startForeground(NOTIFICATION_ID, notification)
            return
        }
        // API 29+: type 0 means "use the manifest-declared types" on Q..Tiramisu (no runtime
        // permission enforcement before API 34); API 34+ passes the permission-derived mask.
        startForeground(
            NOTIFICATION_ID,
            notification,
            startForegroundTypeForApi(
                sdkInt = Build.VERSION.SDK_INT,
                recordAudioGranted = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED,
                bodySensorsGranted = checkSelfPermission(android.Manifest.permission.BODY_SENSORS) ==
                    PackageManager.PERMISSION_GRANTED
            )
        )
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
        pendingRawEpoch = null
        prevPrevStage   = null
        prevStage       = null

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
                    // YAMNet model is downloaded on demand (consent-gated in Settings), not bundled.
                    // If the preference is on but the model has not been downloaded (fresh install,
                    // storage cleared), degrade to the ZCR heuristic for the session rather than
                    // failing the whole audio pipeline.
                    val yamnetModelFile = if (useYamnet && yamnetModelRepository.isDownloaded()) {
                        yamnetModelRepository.modelFile()
                    } else {
                        null
                    }
                    audioEventClassifier = if (yamnetModelFile != null) {
                        val classifier = YamnetAudioClassifier(yamnetModelFile)
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
                            "Microphone failed to initialize - session $sessionId will have no audio events/BRPM")
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

        // Collect sonar epochs (null during calibration - skip those)
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

        // Phone-lifted → immediate AWAKE epoch. Written raw, bypassing the stage-smoothing
        // buffer in handleEpoch() - a real lift is a strong signal, not single-epoch noise.
        // Note the skip it causes (skipNextEpoch below) can make the next smoothing window
        // non-consecutive (e.g. [E1, E2, E4]); harmless, mode-of-3 stays sane.
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
        val rawEpoch = SleepEpoch(
            sessionId           = sessionId,
            timestampMillis     = epochData.timestampMillis,
            stage               = stage,
            movementMagnitude   = epochData.movementMagnitude,
            movementVariability = epochData.movementVariability
        )

        // Stage smoothing: the epoch before this one now has a successor, so it can be written
        // - smoothed against [prevprev, prev, current]. The current epoch stays pending until
        // its own successor arrives.
        pendingRawEpoch?.let { prevEpoch ->
            val smoothedStage = classifyStage.smoothStages(
                listOf(prevPrevStage ?: prevEpoch.stage, prevEpoch.stage, stage)
            )[1]
            sleepRepository.insertEpoch(prevEpoch.copy(stage = smoothedStage))
        }
        prevPrevStage = prevStage
        prevStage     = stage
        pendingRawEpoch = rawEpoch
        _finalEpoch.value = rawEpoch

        // Smart alarm uses the raw current stage - it's the freshest signal available (the
        // current epoch has no successor to smooth against yet, so this is what the
        // pre-smoothing code would have used anyway).
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
            // Persist a playable WAV clip for every event type (not just talk) - the session
            // detail audio player replays snore, cough, talk and anomaly clips from these files.
            val clipDirName = when (event.type) {
                dev.vic41148.somn.core.domain.model.AudioEventType.TALK -> "sleep_talk"
                dev.vic41148.somn.core.domain.model.AudioEventType.SNORE -> "sleep_snore"
                dev.vic41148.somn.core.domain.model.AudioEventType.COUGH -> "sleep_cough"
                else -> "sleep_events"
            }
            val dir = java.io.File(filesDir, clipDirName)
            if (!dir.exists()) dir.mkdirs()
            val wavFile = java.io.File(dir, "${event.type.name.lowercase()}_${sessionId}_${event.timestampMillis}.wav")
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                writeWavFile(wavFile, rawBuffer, AudioCollector.SAMPLE_RATE)
                event = event.copy(clipPath = wavFile.absolutePath)
                sleepRepository.insertAudioEvent(event)
            }
            if (event.type == dev.vic41148.somn.core.domain.model.AudioEventType.SNORE && snoreNudgeEnabled) {
                snoringNudgeController.nudge()
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
            putExtra(AlarmReceiver.EXTRA_WAKE_WINDOW_MINUTES, alarm.wakeWindowMinutes)
        }
        startForegroundService(si)
        // Smart-alarm wake: no ViewModel stop path follows, so flush the final epoch here
        // (async, best-effort - never block the main thread).
        stopTracking(flushFinalEpochHere = true)
    }

    private fun fireLowBreathRateAlarm(brpm: Int) {
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("Low Breath Rate Alert")
            .setContentText("Your breathing rate dropped to $brpm breaths per minute.")
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

    /**
     * Stops all collectors and releases tracking state.
     *
     * @param flushFinalEpochHere whether this caller flushes the held-back final epoch itself.
     *   The ViewModel's user-stop path sets this false and writes the epoch itself (deterministic
     *   ordering before it reads the epoch list back); fireSmartAlarm/onDestroy set it true and
     *   flush asynchronously on the service scope. Never runBlocking on the main thread here -
     *   that wedged Room's executors during teardown and hung every later query.
     */
    private fun stopTracking(flushFinalEpochHere: Boolean = true) {
        // The held-back final epoch has no successor, so per smoothStages() semantics it is
        // written unsmoothed. The ViewModel path writes it synchronously in its own coroutine
        // (which guarantees it lands before the epoch list is read back); the service paths
        // flush asynchronously as best-effort.
        if (flushFinalEpochHere) {
            _finalEpoch.value?.let { last ->
                _finalEpoch.value = null
                serviceScope.launch {
                    try {
                        sleepRepository.insertEpoch(last)
                    } catch (e: Exception) {
                        android.util.Log.e("SleepTrackingService", "Failed to flush final epoch", e)
                    }
                }
            }
        }
        pendingRawEpoch = null
        prevPrevStage   = null
        prevStage       = null
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
        // Breathing-rate alerts are time-sensitive and sound-bearing, so they get a dedicated
        // high-importance channel - posting them on IMPORTANCE_LOW would mute the sound/heads-up
        // no matter what priority the builder requests (the channel wins).
        val alertChannel = NotificationChannel(
            ALERT_CHANNEL_ID, "Sleep Alerts", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Low breathing-rate and other urgent sleep alerts"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
        manager.createNotificationChannel(alertChannel)
    }

    private fun createNotification(): Notification {
        // Tap-through: the notification opens MainActivity (via the launcher intent so this
        // feature module never imports app classes) with EXTRA_OPEN_TRACKING set, and the
        // NavGraph navigates to the tracking screen. CLEAR_TOP brings an existing task forward
        // instead of stacking a second activity.
        val openTrackingIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_TRACKING, true)
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            openTrackingIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // A foreground-service notification that can't stop the work it advertises strands the
        // user until the timer runs out - expose the STOP action the service already handles.
        val stopIntent = android.content.Intent(this, SleepTrackingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            NOTIFICATION_ID + 1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sleep Tracking Active")
            .setContentText("Monitoring your sleep… Tap to open")
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setOngoing(true).setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // When the stop came via ACTION_STOP, the ViewModel owns the final-epoch flush - flushing
        // here too would clear finalEpoch and async-write while the ViewModel is mid-suspend,
        // reintroducing the race this fix eliminates. When the service is destroyed without an
        // ACTION_STOP (e.g. system-initiated), flush as best-effort like the old code did.
        stopTracking(flushFinalEpochHere = !stopViaActionStop)
        yamnetClassifier?.close()
        yamnetClassifier = null
        serviceScope.cancel()
        super.onDestroy()
    }
}

enum class TrackingState { IDLE, TRACKING, PAUSED }

/**
 * Computes the foreground-service type mask to pass to `startForeground(id, notification, type)`
 * on Android 14+ (the three-arg overload).
 *
 * Android 14+ (targetSdk 34+) enforces the mask at startForeground() time: claiming "health"
 * requires the BODY_SENSORS runtime permission and "microphone" requires RECORD_AUDIO - if a
 * claimed type's permission isn't held, the system throws SecurityException. BODY_SENSORS is only
 * granted if the user opted in during onboarding, so the mask must never assume it. When neither
 * permission is held, it falls back to the permission-free "specialUse" type (declared in the
 * manifest), so the service can still start - audio stays off because [SleepTrackingService]
 * gates mic collection on RECORD_AUDIO separately.
 *
 * @return a bitwise OR of [ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE] and/or
 *   [ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH], or [ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE]
 *   when neither permission is granted.
 */
internal fun foregroundServiceTypeMask(
    recordAudioGranted: Boolean,
    bodySensorsGranted: Boolean
): Int {
    var type = 0
    if (recordAudioGranted) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
    if (bodySensorsGranted) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
    // No sensor permission held (user denied or revoked) - specialUse is the one declared
    // type that needs no runtime permission, so use it rather than crashing.
    if (type == 0) type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
    return type
}

/**
 * Sentinel returned by [startForegroundTypeForApi] on API 26-28: the three-arg
 * [android.app.Service.startForeground] overload does not exist before API 29, so the two-arg
 * overload must be used.
 */
internal const val START_FOREGROUND_TWO_ARG = -1

/**
 * Computes the type to pass to the three-arg [android.app.Service.startForeground] overload on
 * API 29+ - the single testable seam behind [SleepTrackingService.startTrackingForeground]'s
 * SDK-conditional branch selection (locked in by StartForegroundBranchSelectionTest). The
 * two-arg-vs-three-arg overload dispatch itself is guarded by [Build.VERSION.SDK_INT] at the
 * call site, because lint's NewApi check requires an explicit SDK guard on the three-arg call.
 *
 *  - API 26-28 (below Q): returns [START_FOREGROUND_TWO_ARG] - the two-arg overload must be used.
 *    The call site dispatches the two-arg path via its own SDK_INT guard (lint's NewApi check
 *    requires it), so this branch is deliberately redundant - kept so the full decision table
 *    stays locked in by StartForegroundBranchSelectionTest. Do not remove it as "dead".
 *  - API 29-33 (Q..Tiramisu): returns 0, meaning "use the manifest-declared types". FGS types had
 *    no runtime-permission enforcement before API 34, so this can never throw for the type.
 *  - API 34+ (UpsideDownCake): returns the permission-derived mask from [foregroundServiceTypeMask]
 *    - only claiming types whose runtime permission is actually held, falling back to the
 *    permission-free "specialUse" type when none are granted.
 *
 * Android 14+ (targetSdk 34+) enforces the mask at startForeground() time: the two-arg overload
 * throws MissingForegroundServiceTypeException when the manifest declares multiple types, and the
 * three-arg overload throws SecurityException when a claimed type's runtime permission isn't held
 * ("health" requires BODY_SENSORS, "microphone" requires RECORD_AUDIO). BODY_SENSORS is only
 * granted if the user opted in during onboarding, so the service must never assume it - it must
 * only ever claim the types it can prove it holds, falling back to the permission-free
 * "specialUse" type when none are granted.
 */
internal fun startForegroundTypeForApi(
    sdkInt: Int,
    recordAudioGranted: Boolean,
    bodySensorsGranted: Boolean
): Int = when {
    sdkInt < Build.VERSION_CODES.Q -> START_FOREGROUND_TWO_ARG
    sdkInt < Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> 0
    else -> foregroundServiceTypeMask(recordAudioGranted, bodySensorsGranted)
}
