package dev.vic41148.somn.core.domain.usecase

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager

/**
 * Verifies device sensor capabilities before first use.
 *
 * Research doc §3.1: "Sensor diagnostic test — verify hardware capabilities before first use"
 */
class SensorDiagnosticUseCase {

    data class DiagnosticResult(
        val hasAccelerometer: Boolean,
        val accelerometerResolution: Float?,  // m/s² per bit
        val accelerometerMaxRange: Float?,    // m/s²
        val hasMicrophone: Boolean,
        val hasGyroscope: Boolean,
        val overallReady: Boolean,
        val warnings: List<String>
    )

    fun diagnose(context: Context): DiagnosticResult {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val hasMic = context.packageManager.hasSystemFeature("android.hardware.microphone")

        val warnings = mutableListOf<String>()

        if (accelerometer == null) {
            warnings.add("No accelerometer found. Sleep tracking requires an accelerometer.")
        }

        accelerometer?.let {
            if (it.resolution > 0.05f) {
                warnings.add("Accelerometer resolution is low (${it.resolution} m/s²). Sleep stage accuracy may be reduced.")
            }
        }

        if (!hasMic) {
            warnings.add("No microphone found. Snoring detection and audio monitoring will be unavailable.")
        }

        return DiagnosticResult(
            hasAccelerometer = accelerometer != null,
            accelerometerResolution = accelerometer?.resolution,
            accelerometerMaxRange = accelerometer?.maximumRange,
            hasMicrophone = hasMic,
            hasGyroscope = gyroscope != null,
            overallReady = accelerometer != null,
            warnings = warnings
        )
    }
}
