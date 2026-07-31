package dev.vic41148.somn.core.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TEMPORARY, manual-only check for Task 14's real acceptance criterion: "verified by manual
 * testing with real audio (e.g. deliberately snoring/coughing/talking near the phone during a
 * test session) — not that it's more accurate than the heuristic, just that it functions."
 *
 * [YamnetAudioClassifierInstrumentedTest] only proves the interpreter runs on synthetic tones.
 * This records ~10 seconds of real mic input in YAMNet-sized windows and logs each window's top
 * class, so a person can talk/cough/fake-snore at the phone during the run and read the result
 * off logcat. Not a pass/fail test — there is nothing to assert without a labeled recording, which
 * is exactly what AUDIO-02 (still open) will add.
 *
 * RECORD_AUDIO is a runtime-dangerous permission, so grant it to the test APK before running:
 *   adb shell pm grant dev.vic41148.somn.core.audio.test android.permission.RECORD_AUDIO
 * Then run:
 *   ./gradlew :core:audio:connectedDebugAndroidTest --tests "*RealAudioYamnetInstrumentedTest*"
 * Then read the result with:
 *   adb logcat -d -s RealAudioYamnetCheck:I
 *
 * Talk, cough, or fake-snore at the phone's mic for the ~10 seconds after this starts.
 */
@RunWith(AndroidJUnit4::class)
class RealAudioYamnetInstrumentedTest {

    private companion object {
        const val TAG = "RealAudioYamnetCheck"
        const val SAMPLE_RATE = 16000
        const val WINDOW_COUNT = 10
    }

    @Test
    fun classifiesLiveMicInputWindowByWindow() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBufferSize, YamnetAudioClassifier.WINDOW_SAMPLE_COUNT * 2)
        )
        val classifier = YamnetAudioClassifier(context)

        try {
            check(audioRecord.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord failed to initialize" }
            audioRecord.startRecording()
            android.util.Log.i(TAG, "=== Recording started — talk / cough / fake-snore now ===")

            val window = ShortArray(YamnetAudioClassifier.WINDOW_SAMPLE_COUNT)
            for (i in 1..WINDOW_COUNT) {
                var offset = 0
                while (offset < window.size) {
                    val read = audioRecord.read(window, offset, window.size - offset)
                    if (read <= 0) break
                    offset += read
                }
                val mapped = classifier.classify(window)
                android.util.Log.i(
                    TAG,
                    "window $i/$WINDOW_COUNT -> mapped=$mapped raw='${classifier.lastTopClassName}' " +
                        "score=${classifier.lastTopScore}"
                )
            }

            android.util.Log.i(TAG, "=== Recording finished ===")
        } finally {
            audioRecord.stop()
            audioRecord.release()
            classifier.close()
        }
    }
}
