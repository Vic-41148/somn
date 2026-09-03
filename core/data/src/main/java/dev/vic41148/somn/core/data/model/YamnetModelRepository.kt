package dev.vic41148.somn.core.data.model

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.vic41148.somn.core.data.util.Checksum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads and stores the YAMNet audio-classification model. The model used to ship as a bundled
 * APK asset; it is now external so every distribution channel stays lean (IzzyOnDroid's 30MB cap,
 * Accrescent's size review) and avoids F-Droid's bundled-binary scanner for the app build itself.
 *
 * The download is user-initiated from Settings with an explicit consent prompt (F-Droid accepts
 * opt-in runtime model downloads), runs over HTTPS, and is **checksum-pinned**: a digest mismatch
 * (tampered in transit / wrong server) is a hard failure that never installs the file.
 *
 * Pinning lives here in constants so the app has no network code path that trusts an arbitrary
 * server. Bumping the model means: upload a new asset, bump [MODEL_URL] + [EXPECTED_SHA256] (and
 * ideally the yamnet-v1 tag) together.
 */
@Singleton
class YamnetModelRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val modelFile: File
        get() = File(context.filesDir, MODEL_FILE_NAME)

    /** Path the classifier loads from; exists only after a successful download. */
    fun modelFile(): File = modelFile

    fun isDownloaded(): Boolean = modelFile().exists() && modelFile().length() > 0

    /**
     * Fetches the model into filesDir, streaming to a temp file, verifying sha256 against
     * [EXPECTED_SHA256] before the final rename. Returns the file or throws; no partial state is
     * left behind. [onProgress] is invoked with (bytesDownloaded, totalBytes).
     */
    suspend fun download(onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> }): File =
        withContext(Dispatchers.IO) {
            val connection = URL(MODEL_URL).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("Accept", "application/octet-stream")

                val total = connection.contentLengthLong
                val temp = File(context.cacheDir, "$MODEL_FILE_NAME.part")
                temp.delete()

                connection.inputStream.use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, total)
                        }
                    }
                }

                val actual = Checksum.sha256(temp)
                if (!Checksum.sha256Matches(EXPECTED_SHA256, actual)) {
                    temp.delete()
                    throw IOException("YAMNet model checksum mismatch: expected $EXPECTED_SHA256, got $actual - refusing to install")
                }

                modelFile().parentFile?.mkdirs()
                if (!temp.renameTo(modelFile())) {
                    // Rename across the same volume is atomic; fall back to a copy if something
                    // exotic interfered, then clean the temp regardless.
                    temp.copyTo(modelFile(), overwrite = true)
                    temp.delete()
                }
                modelFile()
            } finally {
                connection.disconnect()
            }
        }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000

        /** Published on the `yamnet-v1` GitHub release (scripts/publish-yamnet-model.sh). */
        const val MODEL_URL =
            "https://github.com/Vic-41148/somn/releases/download/yamnet-v1/yamnet.tflite"

        /** sha256 of model/yamnet.tflite - verified before the file is ever kept. */
        const val EXPECTED_SHA256 =
            "10c95ea3eb9a7bb4cb8bddf6feb023250381008177ac162ce169694d05c317de"

        const val MODEL_FILE_NAME = "yamnet.tflite"
    }
}