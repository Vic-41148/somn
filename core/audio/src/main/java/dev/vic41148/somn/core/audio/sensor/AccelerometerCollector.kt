package dev.vic41148.somn.core.audio.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Collects accelerometer data at ~10Hz and produces epoch summaries
 * every 30 seconds using wall-clock time.
 *
 * Enhanced with phone-lifted detection: detects significant Z-axis
 * orientation changes that indicate the user picked up their phone,
 * which can auto-pause tracking or mark awake events.
 */
class AccelerometerCollector(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val epochChannel = Channel<EpochData>(Channel.BUFFERED)
    val epochFlow: Flow<EpochData> = epochChannel.receiveAsFlow()

    // Accumulator for current epoch
    private val samples = mutableListOf<Float>()
    private var epochStartTime = 0L

    // Phone-lifted detection state
    private var lastZ = 0f
    private var liftDetected = false
    private val liftEventChannel = Channel<LiftEvent>(Channel.BUFFERED)
    val liftEvents: Flow<LiftEvent> = liftEventChannel.receiveAsFlow()

    // registerListener() with no Handler dispatches onSensorChanged on the calling thread's
    // Looper - for a foreground service that's the main thread, so a whole night of 10Hz
    // accelerometer callbacks would otherwise contend with the UI thread whenever the app is
    // in the foreground. A dedicated background thread keeps sensor processing off it.
    private var handlerThread: HandlerThread? = null

    companion object {
        private const val EPOCH_DURATION_MS = 30_000L  // 30-second epoch window (wall-clock)
        private const val SENSOR_DELAY_US = 100_000    // 100ms = 10Hz sampling rate
        private const val GRAVITY = 9.81f
        private const val LIFT_Z_THRESHOLD = 4.0f          // Z-axis change indicating pickup
        private const val SIGNIFICANT_MOTION_THRESHOLD = 1.5f  // Large movement = likely awake
    }

    fun start() {
        samples.clear()
        epochStartTime = System.currentTimeMillis()
        lastZ = GRAVITY  // Assume phone flat initially
        liftDetected = false

        val thread = HandlerThread("AccelerometerCollector").apply { start() }
        handlerThread = thread

        accelerometer?.let {
            sensorManager.registerListener(
                this,
                it,
                SENSOR_DELAY_US,  // Explicit 10Hz (100ms between samples)
                100_000,  // 100ms max reporting latency for battery
                Handler(thread.looper)
            )
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        samples.clear()
        handlerThread?.quitSafely()
        handlerThread = null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // RMS magnitude minus gravity
        val magnitude = sqrt(x * x + y * y + z * z) - GRAVITY
        val absMagnitude = abs(magnitude)
        samples.add(absMagnitude)

        // Phone-lifted detection: significant Z-axis change from resting position
        val zDelta = abs(z - lastZ)
        if (zDelta > LIFT_Z_THRESHOLD && !liftDetected) {
            liftDetected = true
            liftEventChannel.trySend(
                LiftEvent(
                    timestampMillis = System.currentTimeMillis(),
                    type = LiftEventType.PHONE_LIFTED
                )
            )
        } else if (zDelta < 1.0f && liftDetected) {
            // Phone put back down
            liftDetected = false
            liftEventChannel.trySend(
                LiftEvent(
                    timestampMillis = System.currentTimeMillis(),
                    type = LiftEventType.PHONE_PLACED_DOWN
                )
            )
        }
        lastZ = z

        // Significant motion detection (possible awake event)
        if (absMagnitude > SIGNIFICANT_MOTION_THRESHOLD) {
            liftEventChannel.trySend(
                LiftEvent(
                    timestampMillis = System.currentTimeMillis(),
                    type = LiftEventType.SIGNIFICANT_MOTION
                )
            )
        }

        // Time-based epoch window: process after 30 seconds of real time
        val elapsed = System.currentTimeMillis() - epochStartTime
        if (elapsed >= EPOCH_DURATION_MS && samples.isNotEmpty()) {
            processEpoch()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    private fun processEpoch() {
        if (samples.isEmpty()) return

        val magnitudeList = samples.toList()
        val mean = magnitudeList.average().toFloat()
        val variance = magnitudeList.map { (it - mean) * (it - mean) }
            .average().toFloat()
        val stdDev = sqrt(variance.toDouble()).toFloat()

        val epoch = EpochData(
            timestampMillis = epochStartTime,
            movementMagnitude = mean,
            movementVariability = stdDev
        )

        epochChannel.trySend(epoch)

        samples.clear()
        epochStartTime = System.currentTimeMillis()
    }

    data class EpochData(
        val timestampMillis: Long,
        val movementMagnitude: Float,
        val movementVariability: Float
    )

    data class LiftEvent(
        val timestampMillis: Long,
        val type: LiftEventType
    )

    enum class LiftEventType {
        PHONE_LIFTED,
        PHONE_PLACED_DOWN,
        SIGNIFICANT_MOTION
    }
}
