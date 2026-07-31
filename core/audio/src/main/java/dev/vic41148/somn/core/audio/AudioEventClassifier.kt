package dev.vic41148.somn.core.audio

import dev.vic41148.somn.core.domain.model.AudioEvent
import dev.vic41148.somn.core.domain.model.AudioEventType
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Classifies audio buffers into AudioEvent items.
 * Uses Zero-Crossing Rate (ZCR) alongside duration for classification.
 *
 * @param yamnetClassify optional YAMNet-backed classifier (Task 14, AUDIO-01) — when provided,
 * the finished event's audio is classified by YAMNet first; the ZCR heuristic below only runs
 * as a fallback when YAMNet returns null (silence, or a class this feature doesn't map — see
 * [YamnetLabels.classNameToAudioEventType]). Kept as a plain lambda rather than a direct
 * [YamnetAudioClassifier] dependency so this class stays Android/TFLite-free and unit-testable.
 */
class AudioEventClassifier(
    private val yamnetClassify: ((ShortArray) -> AudioEventType?)? = null
) {

    private var loudBufferCount = 0
    private var eventStartTime = 0L
    private var maxIntensity = 0
    
    private var sumZcr = 0L
    private var maxZcr = 0
    private val currentBuffer = mutableListOf<Short>()

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
            currentBuffer.addAll(buffer.toList())
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
                val outBuffer = currentBuffer.toShortArray()

                val type = yamnetClassify?.invoke(outBuffer) ?: when {
                    // Duration & ZCR heuristic classification (fallback when YAMNet is
                    // disabled, or returned null for this buffer)
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
