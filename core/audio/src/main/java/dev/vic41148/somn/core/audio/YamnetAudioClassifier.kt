package dev.vic41148.somn.core.audio

import dev.vic41148.somn.core.domain.model.AudioEventType
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * On-device audio classification via YAMNet (AudioSet-trained TFLite model), as an alternative to
 * [AudioEventClassifier]'s ZCR heuristic (AUDIO-01 first pass - see TASKS.md Task 14).
 *
 * The model is NOT bundled in the APK anymore - it is downloaded on first use, consent-gated, and
 * checksum-verified into `context.filesDir` by `core:data`'s YamnetModelRepository (store-channel
 * policy plus a ~4MB reduction on every download). Load here from the on-disk [modelFile].
 *
 * YAMNet expects 16kHz mono, ~0.975s windows (15,600 samples). [AudioCollector.SAMPLE_RATE]
 * is already 16000, so no resampling is needed - only windowing/padding.
 *
 * Not validated for accuracy against a real audio corpus (AUDIO-02) and not soak-tested for
 * battery/thermal impact (AUDIO-03) - those are explicitly separate, still-open follow-ups.
 * This class only proves inference runs and produces a classification.
 */
class YamnetAudioClassifier(modelFile: File) {

    private val interpreter: Interpreter

    init {
        val modelBuffer: MappedByteBuffer = RandomAccessFile(modelFile, "r").use { raf ->
            raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length())
        }
        interpreter = Interpreter(modelBuffer)
    }

    /**
     * Classifies one audio buffer. Returns null if the buffer is silent/empty, or if the
     * top-scoring class is not one [YamnetLabels] maps to a Somn [AudioEventType] - callers
     * should treat null as "no opinion", not as a negative result, and may fall back to
     * another signal (e.g. the ZCR heuristic).
     */
    fun classify(buffer: ShortArray): AudioEventType? {
        if (buffer.isEmpty()) return null

        val input = toYamnetInput(buffer)
        val scores = Array(1) { FloatArray(YamnetLabels.CLASS_NAMES.size) }
        interpreter.run(input, scores)

        val topIndex = scores[0].indices.maxByOrNull { scores[0][it] } ?: return null
        val className = YamnetLabels.CLASS_NAMES.getOrNull(topIndex) ?: return null
        lastTopClassName = className
        lastTopScore = scores[0][topIndex]
        return YamnetLabels.classNameToAudioEventType(className)
    }

    /** Raw top-scoring class name/score from the most recent [classify] call - exposed only for logging/debugging (e.g. the on-device smoke test), not used in the classification decision itself. */
    var lastTopClassName: String? = null
        private set
    var lastTopScore: Float = 0f
        private set

    fun close() {
        interpreter.close()
    }

    companion object {
        /** On-disk model file name (filesDir-relative), shared with [dev.vic41148.somn.core.data.model.YamnetModelRepository]. */
        const val MODEL_FILE_NAME = "yamnet.tflite"
        const val WINDOW_SAMPLE_COUNT = 15_600 // ~0.975s @ 16kHz, YAMNet's expected input size

        /**
         * Converts 16-bit PCM samples to YAMNet's expected input: a single [-1, 1] normalized
         * Float32 window of exactly [WINDOW_SAMPLE_COUNT] samples. Longer buffers are truncated
         * to the first window; shorter ones are zero-padded. Pure function - testable without
         * TFLite or a device.
         */
        fun toYamnetInput(buffer: ShortArray): Array<FloatArray> {
            val window = FloatArray(WINDOW_SAMPLE_COUNT)
            val copyCount = minOf(buffer.size, WINDOW_SAMPLE_COUNT)
            for (i in 0 until copyCount) {
                window[i] = buffer[i] / SHORT_TO_FLOAT_SCALE
            }
            return arrayOf(window)
        }

        private const val SHORT_TO_FLOAT_SCALE = 32768f
    }
}