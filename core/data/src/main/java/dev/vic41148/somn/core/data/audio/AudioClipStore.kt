package dev.vic41148.somn.core.data.audio

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.vic41148.somn.core.data.backup.EncryptionUtils
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * At-rest encryption for sleep audio clips. New clips are AES-256-GCM sealed
 * ([EncryptionUtils], Keystore key) with an `.enc` suffix; reads accept legacy plaintext
 * clips as-is so pre-encryption recordings keep playing until retention prunes them.
 */
@Singleton
class AudioClipStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryption: EncryptionUtils
) {

    fun isEncrypted(path: String): Boolean = path.endsWith(ENCRYPTED_SUFFIX)

    /** Seals raw WAV bytes into `dir/name.enc`, returning the stored file. */
    fun writeClip(dir: File, name: String, wavBytes: ByteArray): File {
        if (!dir.exists()) dir.mkdirs()
        val out = File(dir, "$name$ENCRYPTED_SUFFIX")
        out.outputStream().use { o ->
            wavBytes.inputStream().use { i -> encryption.encrypt(i, o) }
        }
        return out
    }

    /** Raw clip bytes, decrypting `.enc` files transparently. */
    fun readClipBytes(path: String): ByteArray {
        val file = File(path)
        if (!isEncrypted(path)) return file.readBytes()
        val bos = ByteArrayOutputStream()
        file.inputStream().use { i -> encryption.decrypt(i, bos) }
        return bos.toByteArray()
    }

    /**
     * Plaintext temp copy for APIs that need a real file (MediaPlayer). Returns the
     * original file for legacy clips. The caller deletes the temp copy when done.
     */
    fun playableCopy(path: String): File {
        if (!isEncrypted(path)) return File(path)
        val tmp = File.createTempFile("play_", ".wav", context.cacheDir)
        tmp.outputStream().use { o ->
            File(path).inputStream().use { i -> encryption.decrypt(i, o) }
        }
        return tmp
    }

    companion object {
        const val ENCRYPTED_SUFFIX = ".enc"
    }
}
