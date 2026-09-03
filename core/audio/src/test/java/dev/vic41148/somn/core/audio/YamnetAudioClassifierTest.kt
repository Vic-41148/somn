package dev.vic41148.somn.core.audio

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

/**
 * Tests [YamnetAudioClassifier.toYamnetInput] only - pure sample-conversion/windowing logic,
 * no TFLite/Interpreter involved, so this runs on plain JVM without a device.
 */
class YamnetAudioClassifierTest {

    @Test
    fun `output window is always exactly WINDOW_SAMPLE_COUNT samples`() {
        val short = ShortArray(100) { 1000 }
        val exact = ShortArray(YamnetAudioClassifier.WINDOW_SAMPLE_COUNT) { 1000 }
        val long = ShortArray(YamnetAudioClassifier.WINDOW_SAMPLE_COUNT * 3) { 1000 }

        assertEquals(YamnetAudioClassifier.WINDOW_SAMPLE_COUNT, YamnetAudioClassifier.toYamnetInput(short)[0].size)
        assertEquals(YamnetAudioClassifier.WINDOW_SAMPLE_COUNT, YamnetAudioClassifier.toYamnetInput(exact)[0].size)
        assertEquals(YamnetAudioClassifier.WINDOW_SAMPLE_COUNT, YamnetAudioClassifier.toYamnetInput(long)[0].size)
    }

    @Test
    fun `shorter buffers are zero-padded, not repeated or garbage-filled`() {
        val short = ShortArray(10) { 5000 }
        val window = YamnetAudioClassifier.toYamnetInput(short)[0]

        for (i in 10 until window.size) {
            assertEquals(0f, window[i], 0.0001f)
        }
    }

    @Test
    fun `longer buffers are truncated to the first window, not averaged or resampled`() {
        val long = ShortArray(YamnetAudioClassifier.WINDOW_SAMPLE_COUNT + 500) { index ->
            if (index < YamnetAudioClassifier.WINDOW_SAMPLE_COUNT) 100 else 30000
        }
        val window = YamnetAudioClassifier.toYamnetInput(long)[0]

        assertEquals(YamnetAudioClassifier.WINDOW_SAMPLE_COUNT, window.size)
        // every sample should reflect the first-window value (100), never the truncated tail (30000)
        window.forEach { sample -> assert(abs(sample) < 1f) { "expected samples derived from 100, got $sample" } }
    }

    @Test
    fun `samples are normalized to the -1,1 range expected by YAMNet`() {
        val maxPositive = shortArrayOf(Short.MAX_VALUE)
        val maxNegative = shortArrayOf(Short.MIN_VALUE)

        val posWindow = YamnetAudioClassifier.toYamnetInput(maxPositive)[0]
        val negWindow = YamnetAudioClassifier.toYamnetInput(maxNegative)[0]

        assert(posWindow[0] in 0.99f..1.0f) { "expected ~1.0, got ${posWindow[0]}" }
        assert(negWindow[0] in -1.0f..-0.99f) { "expected ~-1.0, got ${negWindow[0]}" }
    }

    @Test
    fun `empty buffer produces an all-silent window without crashing`() {
        val window = YamnetAudioClassifier.toYamnetInput(ShortArray(0))[0]
        assertEquals(YamnetAudioClassifier.WINDOW_SAMPLE_COUNT, window.size)
        window.forEach { assertEquals(0f, it, 0.0001f) }
    }
}
