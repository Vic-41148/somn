package dev.vic41148.somn.core.audio

import dev.vic41148.somn.core.domain.model.AudioEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure JVM tests - no TFLite, no device - for the label list and class-name mapping. */
class YamnetLabelsTest {

    @Test
    fun `class list has exactly 521 AudioSet classes`() {
        assertEquals(521, YamnetLabels.CLASS_NAMES.size)
    }

    @Test
    fun `class list is index-aligned with the bundled model's known classes`() {
        // Spot-check a handful of fixed indices against the label file embedded in
        // yamnet.tflite's own metadata - if these drift, the bundled model was swapped
        // for a different version and the mapping below is no longer valid.
        assertEquals("Speech", YamnetLabels.CLASS_NAMES[0])
        assertEquals("Snoring", YamnetLabels.CLASS_NAMES[38])
        assertEquals("Cough", YamnetLabels.CLASS_NAMES[42])
        assertEquals("Breathing", YamnetLabels.CLASS_NAMES[36])
    }

    @Test
    fun `snoring and snort map to SNORE`() {
        assertEquals(AudioEventType.SNORE, YamnetLabels.classNameToAudioEventType("Snoring"))
        assertEquals(AudioEventType.SNORE, YamnetLabels.classNameToAudioEventType("Snort"))
    }

    @Test
    fun `cough and throat clearing map to COUGH`() {
        assertEquals(AudioEventType.COUGH, YamnetLabels.classNameToAudioEventType("Cough"))
        assertEquals(AudioEventType.COUGH, YamnetLabels.classNameToAudioEventType("Throat clearing"))
    }

    @Test
    fun `speech-family classes map to TALK`() {
        assertEquals(AudioEventType.TALK, YamnetLabels.classNameToAudioEventType("Speech"))
        assertEquals(AudioEventType.TALK, YamnetLabels.classNameToAudioEventType("Conversation"))
        assertEquals(AudioEventType.TALK, YamnetLabels.classNameToAudioEventType("Whispering"))
        assertEquals(AudioEventType.TALK, YamnetLabels.classNameToAudioEventType("Child speech, kid speaking"))
    }

    @Test
    fun `gasp and wheeze map to ANOMALY rather than being dropped`() {
        assertEquals(AudioEventType.ANOMALY, YamnetLabels.classNameToAudioEventType("Gasp"))
        assertEquals(AudioEventType.ANOMALY, YamnetLabels.classNameToAudioEventType("Wheeze"))
    }

    @Test
    fun `unrelated classes return null rather than a guessed type`() {
        assertNull(YamnetLabels.classNameToAudioEventType("Guitar"))
        assertNull(YamnetLabels.classNameToAudioEventType("Rain"))
        assertNull(YamnetLabels.classNameToAudioEventType("Silence"))
        assertNull(YamnetLabels.classNameToAudioEventType("Dog"))
    }
}
