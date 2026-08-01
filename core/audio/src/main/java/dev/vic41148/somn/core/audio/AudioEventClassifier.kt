package dev.vic41148.somn.core.audio

import dev.vic41148.somn.core.domain.model.AudioEvent
import dev.vic41148.somn.core.domain.model.AudioEventType
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Classifies audio buffers into AudioEvent items.
 * Uses Zero-Crossing Rate (ZCR) alongside duration for classification.
 */
class AudioEventClassifier {

    companion object {
        // Sustained noise above the "loud" threshold (fan, traffic, a snoring bout that never
        // drops below 45dB) would otherwise keep boxing samples into `currentBuffer` for as long
        // as it lasts, unbounded, for the rest of the night. Cap raw-sample retention once an
        // event has accumulated this much audio; duration/dB/ZCR stats keep accumulating past
        // this point for classification, only the raw clip itself is capped.
        private const val MAX_BUFFERED_SECONDS = 30
    }

    private var loudBufferCount = 0
    private var eventStartTime = 0L
    private var maxIntensity = 0

    private var sumZcr = 0L
    private var maxZcr = 0
    private val currentBuffer = mutableListOf<Short>()
    private val maxBufferedSamples = MAX_BUFFERED_SECONDS * AudioCollector.SAMPLE_RATE

    /**
     * Processes an audio buffer and returns an AudioEvent if an event just finished.
     */
    fun processBuffer(buffer: ShortArray, sessionId: Long, timestampMillis: Long): Pair<AudioEvent, ShortArray>? {
        val rms = calculateRMS(buffer)
        val db = calculateDecibels(rms)
        val zcr = calculateZCR(buffer)

        // Simple thresholding: e.g. > 45dB is considered "loud"
        val isLoud = db > 45

        if (isLoud) {
            if (loudBufferCount == 0) {
                eventStartTime = timestampMillis
                sumZcr = 0L
                maxZcr = 0
                currentBuffer.clear()
            }
            loudBufferCount++
            if (currentBuffer.size < maxBufferedSamples) {
                currentBuffer.addAll(buffer.toList())
            }
            if (db > maxIntensity) {
                maxIntensity = db
            }
            sumZcr += zcr
            if (zcr > maxZcr) {
                maxZcr = zcr
            }
        } else {
            if (loudBufferCount > 0) {
                // Event finished
                val durationSec = (loudBufferCount * buffer.size.toDouble()) / AudioCollector.SAMPLE_RATE.toDouble()
                val avgZcr = if (loudBufferCount > 0) (sumZcr / loudBufferCount).toInt() else 0
                
                // Duration & ZCR heuristic classification
                val type = when {
                    durationSec < 0.8 && maxZcr < 1500 -> AudioEventType.COUGH
                    maxZcr > 2000 || avgZcr > 1000 -> AudioEventType.TALK
                    else -> AudioEventType.SNORE
                }

                val event = AudioEvent(
                    sessionId = sessionId,
                    timestampMillis = eventStartTime,
                    durationSeconds = max(1, durationSec.toInt()),
                    type = type,
                    intensityDecibels = maxIntensity
                )

                val outBuffer = currentBuffer.toShortArray()

                loudBufferCount = 0
                maxIntensity = 0
                sumZcr = 0L
                maxZcr = 0
                currentBuffer.clear()

                // Only return events > some min threshold to avoid noise
                if (event.intensityDecibels > 50) {
                    return Pair(event, outBuffer)
                }
            }
        }
        return null
    }

    private fun calculateRMS(buffer: ShortArray): Double {
        var sum = 0.0
        for (sample in buffer) {
            sum += sample * sample
        }
        return sqrt(sum / buffer.size)
    }

    private fun calculateDecibels(rms: Double): Int {
        if (rms <= 0) return 0
        // Reference value based on 16-bit PCM max (32768)
        return (20 * log10(rms)).toInt()
    }
    
    private fun calculateZCR(buffer: ShortArray): Int {
        var zeroCrossings = 0
        for (i in 1 until buffer.size) {
            if ((buffer[i - 1] > 0 && buffer[i] <= 0) || (buffer[i - 1] < 0 && buffer[i] >= 0)) {
                zeroCrossings++
            }
        }
        return zeroCrossings
    }
}
