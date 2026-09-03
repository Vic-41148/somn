package dev.vic41148.somn.core.audio

import dev.vic41148.somn.core.domain.model.AudioEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class AudioEventClassifierTest {

    private val sampleRate = AudioCollector.SAMPLE_RATE

    // Full-scale-ish 1kHz sine ≈ 81dB RMS — well above the 45dB "loud" threshold so every
    // buffer is treated as loud.
    private fun loudBuffer(): ShortArray = ShortArray(sampleRate) { i ->
        (sin(2 * PI * (i % sampleRate) / sampleRate) * 0.5 * Short.MAX_VALUE).toInt().toShort()
    }

    private fun quietBuffer(): ShortArray = ShortArray(sampleRate) // silence ≈ 0dB

    private fun feedBuffers(
        classifier: AudioEventClassifier,
        n: Int,
        make: (Int) -> ShortArray
    ): List<AudioEventType> {
        val types = mutableListOf<AudioEventType>()
        for (i in 0 until n) {
            classifier.processBuffer(make(i), sessionId = 1, timestampMillis = i * 1000L)?.let {
                types += it.first.type
            }
        }
        return types
    }

    @Test
    fun shortLoudBurstFollowedByQuiet_stillEmitsSingleEvent() {
        val c = AudioEventClassifier()
        // 2s of loud audio then silence -> one event emitted on the quiet frame.
        val types = feedBuffers(c, 3) { i -> if (i < 2) loudBuffer() else quietBuffer() }
        assertEquals(1, types.size)
    }

    @Test
    fun sustainedLoudAudio_flushesPeriodicEventsInsteadOfNothing() {
        val c = AudioEventClassifier()
        // Continuous loud audio for 40s then quiet. Without the flush fix this returns 0 events;
        // with it, the 30s raw-buffer cap forces a flush, then the quiet frame emits a second.
        val types = feedBuffers(c, 41) { i -> if (i < 40) loudBuffer() else quietBuffer() }
        assertTrue("expected >=2 flushed events, got ${types.size}", types.size >= 2)
    }

    @Test
    fun continuousLoudUnder30s_withoutQuiet_emitsNothingYet() {
        val c = AudioEventClassifier()
        // 20s of continuous loud audio, never dropping, never reaching the 30s flush cap.
        val types = feedBuffers(c, 20) { loudBuffer() }
        // The event is still open (held in the buffer, waiting for quiet or the 30s flush).
        assertTrue(types.isEmpty())
    }

    @Test
    fun sustainedLoudAudio_flushEventsAreLoudEnoughToPersist() {
        val c = AudioEventClassifier()
        var emitted = false
        for (i in 0 until 41) {
            val buf = if (i < 40) loudBuffer() else quietBuffer()
            val res = c.processBuffer(buf, sessionId = 1, timestampMillis = i * 1000L)
            if (res != null && res.first.intensityDecibels > 50) emitted = true
        }
        assertTrue(emitted)
    }
}
