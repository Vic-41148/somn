package dev.vic41148.somn.core.audio

import android.content.Context
import java.io.File

/**
 * YAMNet is no longer bundled with the app (externalized for store-channel compliance, see
 * core:data's YamnetModelRepository), so on-device tests need the model pushed separately:
 *
 *     adb push model/yamnet.tflite /data/local/tmp/yamnet.tflite
 *
 * This helper copies the pre-pushed copy into the test app's cache the first time it is needed and
 * returns that file for YamnetAudioClassifier. It fails with the push command if the model is
 * absent - never silently downgrades to the heuristic.
 */
object YamnetTestData {

    fun modelFile(context: Context): File {
        val cached = File(context.cacheDir, YamnetAudioClassifier.MODEL_FILE_NAME)
        if (cached.exists() && cached.length() > 0) return cached

        val pushed = File("/data/local/tmp", YamnetAudioClassifier.MODEL_FILE_NAME)
        check(pushed.exists() && pushed.length() > 0) {
            "YAMNet model not found on device. Pre-push it with: " +
                "adb push model/yamnet.tflite /data/local/tmp/yamnet.tflite"
        }
        pushed.copyTo(cached, overwrite = true)
        return cached
    }
}