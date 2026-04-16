package dev.vic41148.somn.core.audio

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Estimates breathing rate from audio via envelope detection and peak tracking.
 */
class BreathingRateEstimator {

    private val envelopeHistory = mutableListOf<Double>()
    
    companion object {
        const val WINDOW_SAMPLES = 4000 // 250ms at 16000Hz
        const val HISTORY_MAX_WINDOWS = 240 // 60 seconds * 4 windows/sec
        const val SMOOTHING_WINDOW_SIZE = 4
        const val NOISE_FLOOR_RMS = 5.0 // Min amplitude to care about
    }

    /**
     * @return Breaths per minute estimate, or null if signal too weak.
     */
    fun estimateBrpm(buffer: ShortArray): Int? {
        val chunks = buffer.size / WINDOW_SAMPLES
        if (chunks == 0) return null
        
        for (i in 0 until chunks) {
            var sumSquare = 0.0
            val start = i * WINDOW_SAMPLES
            val end = start + WINDOW_SAMPLES
            for (j in start until end) {
                val sample = buffer[j].toDouble()
                sumSquare += sample * sample
            }
            val rms = sqrt(sumSquare / WINDOW_SAMPLES)
            envelopeHistory.add(rms)
        }
        
        while (envelopeHistory.size > HISTORY_MAX_WINDOWS) {
            envelopeHistory.removeAt(0)
        }
        
        if (envelopeHistory.size < 120) {
            return null
        }
        
        val smoothed = DoubleArray(envelopeHistory.size)
        for (i in envelopeHistory.indices) {
            var sum = 0.0
            var count = 0
            for (j in max(0, i - SMOOTHING_WINDOW_SIZE)..min(envelopeHistory.lastIndex, i + SMOOTHING_WINDOW_SIZE)) {
                sum += envelopeHistory[j]
                count++
            }
            smoothed[i] = sum / count
        }
        
        if (smoothed.average() < NOISE_FLOOR_RMS) {
            return null
        }
        
        var peakCount = 0
        val peakDistance = 6 // 1.5 seconds minimum between peaks
        var i = peakDistance
        while (i < smoothed.size - peakDistance) {
            val point = smoothed[i]
            var isPeak = true
            for (j in i - peakDistance..i + peakDistance) {
                if (smoothed[j] > point) {
                    isPeak = false
                    break
                }
            }
            if (isPeak) {
                peakCount++
                i += peakDistance
            } else {
                i++
            }
        }
        
        val durationMinutes = envelopeHistory.size / (4.0 * 60.0)
        if (durationMinutes <= 0) return null
        
        val brpm = (peakCount / durationMinutes).toInt()
        return if (brpm in 8..40) brpm else null
    }
}
