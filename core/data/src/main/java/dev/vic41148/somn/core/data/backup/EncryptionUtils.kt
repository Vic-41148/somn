package dev.vic41148.somn.core.data.backup

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-256-GCM encryption using Android Keystore.
 * Keys never leave hardware-backed storage.
 */
@Singleton
class EncryptionUtils @Inject constructor() {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "somn_backup_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        keyStore.getEntry(KEY_ALIAS, null)?.let { entry ->
            return (entry as KeyStore.SecretKeyEntry).secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGenerator.generateKey()
    }

    /**
     * Encrypt [input] stream, writing IV + ciphertext to [output].
     */
    fun encrypt(input: InputStream, output: OutputStream) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv

        // Write IV length + IV first
        output.write(iv.size)
        output.write(iv)

        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            val encrypted = cipher.update(buffer, 0, bytesRead)
            if (encrypted != null) output.write(encrypted)
        }
        val finalBlock = cipher.doFinal()
        if (finalBlock != null) output.write(finalBlock)
        output.flush()
    }

    /**
     * Decrypt [input] stream (IV + ciphertext), writing plaintext to [output].
     */
    fun decrypt(input: InputStream, output: OutputStream) {
        val ivLength = input.read()
        val iv = ByteArray(ivLength)
        // REL-07: InputStream.read(byte[]) may return fewer bytes than requested on a single
        // call (e.g. network/NAS streams) — read in a loop until the full IV is filled.
        var offset = 0
        while (offset < iv.size) {
            val n = input.read(iv, offset, iv.size - offset)
            if (n == -1) throw java.io.EOFException("Stream ended before full IV (${iv.size} bytes) was read")
            offset += n
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))

        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            val decrypted = cipher.update(buffer, 0, bytesRead)
            if (decrypted != null) output.write(decrypted)
        }
        val finalBlock = cipher.doFinal()
        if (finalBlock != null) output.write(finalBlock)
        output.flush()
    }

    /**
     * Encrypt a byte array, returning IV + ciphertext.
     */
    fun encryptBytes(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data)
        return byteArrayOf(iv.size.toByte()) + iv + encrypted
    }

    /**
     * Decrypt a byte array (IV + ciphertext).
     */
    fun decryptBytes(data: ByteArray): ByteArray {
        val ivLength = data[0].toInt()
        val iv = data.sliceArray(1..ivLength)
        val ciphertext = data.sliceArray((ivLength + 1) until data.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(ciphertext)
    }
}
