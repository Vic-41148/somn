package dev.vic41148.somn.core.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.sin
import kotlin.random.Random

/**
 * Task 14 (AUDIO-01) acceptance check: proves YAMNet inference actually runs on-device - model
 * loads from assets, native TFLite libs resolve on real Android runtime, interpreter produces a
 * valid 521-class score vector. This does NOT validate classification accuracy (AUDIO-02) or
 * battery/thermal behavior (AUDIO-03) - those need a real audio corpus and a multi-hour soak
 * test respectively, neither of which this provides.
 *
 * Run with: ./gradlew :core:audio:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class YamnetAudioClassifierInstrumentedTest {

    @Test
    fun interpreterLoadsAndProducesAClassificationForSyntheticTone() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val classifier = YamnetAudioClassifier(YamnetTestData.modelFile(context))
        try {
            // A synthetic 200Hz tone (in a snoring-ish low-frequency range) - not real snore
            // audio, just enough signal (vs. pure silence) to exercise the full model path.
            val tone = ShortArray(YamnetAudioClassifier.WINDOW_SAMPLE_COUNT) { i ->
                (sin(2.0 * Math.PI * 200.0 * i / 16000.0) * 12000).toInt().toShort()
            }
            val result = classifier.classify(tone)
            android.util.Log.i(
                "YamnetSmokeTest",
                "synthetic tone -> mapped=$result raw='${classifier.lastTopClassName}' score=${classifier.lastTopScore}"
            )
            // No assertion on the *mapped* value (null is a valid outcome - see class doc). The
            // acceptance criterion is that the interpreter loaded the model and completed real
            // inference, which is what the raw class name and score below actually demonstrate.
            assertNotNull("Interpreter produced no class name - model did not run", classifier.lastTopClassName)
            // These three inputs are unambiguous enough that YAMNet's own labels are a stable
            // expectation. If they ever stop holding, the model asset or the input conversion is
            // wrong - which is exactly the regression a smoke test that only logged would miss.
            assertEquals("Sine wave", classifier.lastTopClassName)
            assertTrue(
                "Sine wave confidence unexpectedly low: ${classifier.lastTopScore}",
                classifier.lastTopScore > 0.5f
            )

            val silence = ShortArray(YamnetAudioClassifier.WINDOW_SAMPLE_COUNT)
            val silenceResult = classifier.classify(silence)
            android.util.Log.i(
                "YamnetSmokeTest",
                "silence -> mapped=$silenceResult raw='${classifier.lastTopClassName}' score=${classifier.lastTopScore}"
            )
            assertEquals("Silence", classifier.lastTopClassName)

            val noise = ShortArray(YamnetAudioClassifier.WINDOW_SAMPLE_COUNT) { Random.nextInt(-8000, 8000).toShort() }
            val noiseResult = classifier.classify(noise)
            android.util.Log.i(
                "YamnetSmokeTest",
                "white noise -> mapped=$noiseResult raw='${classifier.lastTopClassName}' score=${classifier.lastTopScore}"
            )
            // Noise labels are less stable than tone/silence, so assert only that a class came back.
            assertNotNull(classifier.lastTopClassName)
        } finally {
            classifier.close()
        }
    }
}
