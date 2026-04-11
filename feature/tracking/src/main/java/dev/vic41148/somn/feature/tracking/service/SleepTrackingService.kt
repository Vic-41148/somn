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
import dev.vic41148.somn.core.data.repository.SleepRepository
import dev.vic41148.somn.core.domain.model.SleepEpoch
import dev.vic41148.somn.core.domain.model.SleepStage
import dev.vic41148.somn.core.domain.usecase.ClassifySleepStageUseCase
import dev.vic41148.somn.feature.tracking.sensor.AccelerometerCollector
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that manages the overnight sleep tracking session.
 * Collects accelerometer data, classifies sleep stages, and persists epochs.
 */
@AndroidEntryPoint
class SleepTrackingService : Service() {

    @Inject lateinit var sleepRepository: SleepRepository

    private val classifyStage = ClassifySleepStageUseCase()
    private lateinit var accelerometerCollector: AccelerometerCollector
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var collectionJob: Job? = null

    companion object {
        const val CHANNEL_ID = "sleep_tracking_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "dev.vic41148.somn.action.START_TRACKING"
        const val ACTION_STOP = "dev.vic41148.somn.action.STOP_TRACKING"
        const val EXTRA_SESSION_ID = "session_id"

        private val _trackingState = MutableStateFlow(TrackingState.IDLE)
        val trackingState: StateFlow<TrackingState> = _trackingState.asStateFlow()

        private val _currentSessionId = MutableStateFlow<Long?>(null)
        val currentSessionId: StateFlow<Long?> = _currentSessionId.asStateFlow()

        fun startTracking(context: Context, sessionId: Long) {
            val intent = Intent(context, SleepTrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SESSION_ID, sessionId)
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
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1)
                if (sessionId != -1L) {
                    startForeground(NOTIFICATION_ID, createNotification())
                    startTracking(sessionId)
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

    private fun startTracking(sessionId: Long) {
        _currentSessionId.value = sessionId
        _trackingState.value = TrackingState.TRACKING

        // Acquire partial wake lock for reliable sensor reading
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Somn::TrackingWakeLock"
        ).apply {
            acquire(8 * 60 * 60 * 1000L)  // 8 hours max
        }

        accelerometerCollector.start()

        collectionJob = serviceScope.launch {
            accelerometerCollector.epochFlow.collect { epochData ->
                val stage = classifyStage(
                    epochData.movementMagnitude,
                    epochData.movementVariability
                )

                val epoch = SleepEpoch(
                    sessionId = sessionId,
                    timestampMillis = epochData.timestampMillis,
                    stage = stage,
                    movementMagnitude = epochData.movementMagnitude,
                    movementVariability = epochData.movementVariability
                )

                sleepRepository.insertEpoch(epoch)
            }
        }
    }

    private fun stopTracking() {
        accelerometerCollector.stop()
        collectionJob?.cancel()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        _trackingState.value = TrackingState.IDLE
        _currentSessionId.value = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Sleep Tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows while tracking your sleep"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sleep Tracking Active")
            .setContentText("Monitoring your sleep...")
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopTracking()
        serviceScope.cancel()
        super.onDestroy()
    }
}

enum class TrackingState {
    IDLE,
    TRACKING,
    PAUSED
}
