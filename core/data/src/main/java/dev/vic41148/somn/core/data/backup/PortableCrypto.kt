package dev.vic41148.somn.core.data.backup

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Passphrase-derived AES-256-GCM for backup payloads that must survive the device.
 *
 * [EncryptionUtils] uses an Android Keystore key, which by design cannot leave the TEE — a backup
 * encrypted with it is unreadable the moment the phone is lost, wiped, or the app's data cleared,
 * which is exactly when a backup matters. This class derives its key from a user-held recovery
 * passphrase instead, so a restore only needs the backup file plus the phrase.
 *
 * Envelope layout (big-endian, self-describing so the format can be evolved):
 *
 *     magic          8   "SOMNBAK1"
 *     formatVersion  1
 *     kdfId          1   1 = PBKDF2-HMAC-SHA512
 *     iterations     4
 *     saltLen        1
 *     salt           saltLen
 *     wrapIv        12
 *     wrappedDekLen  2   DEK ciphertext + GCM tag
 *     wrappedDek     wrappedDekLen
 *     payloadIv     12
 *     payload        rest
 *
 * A per-payload data key (DEK) is generated and wrapped by the passphrase-derived key (KEK) rather
 * than encrypting the payload with the KEK directly. That keeps the expensive KDF off the per-file
 * path: [deriveKek] once per sync run, then wrap a fresh DEK per file.
 */
@Singleton
class PortableCrypto @Inject constructor() {

    companion object {
        /** Identifies a portable envelope; lets restore tell these apart from legacy Keystore blobs. */
        val MAGIC: ByteArray = "SOMNBAK1".toByteArray(Charsets.US_ASCII)

        const val FORMAT_VERSION = 1
        const val KDF_PBKDF2_HMAC_SHA512 = 1

        /** OWASP guidance for PBKDF2-HMAC-SHA512. */
        const val DEFAULT_ITERATIONS = 210_000

        private const val SALT_LEN = 16
        private const val GCM_IV_LEN = 12
        private const val GCM_TAG_BITS = 128
        private const val DEK_LEN = 32
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA512"
        private const val STREAM_BUFFER = 8192

        /** Crockford Base32 — no I/L/O/U, so recovery keys survive being read aloud or hand-copied. */
        private const val CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        private const val RECOVERY_KEY_BYTES = 20
    }

    /** A passphrase-derived key-encryption key, plus the KDF parameters needed to reproduce it. */
    class Kek internal constructor(
        internal val key: SecretKey,
        internal val salt: ByteArray,
        internal val iterations: Int
    )

    private val secureRandom = SecureRandom()

    // ---- Key derivation ----

    /**
     * Derives a KEK from [passphrase]. Pass an existing [salt]/[iterations] to reproduce a prior
     * key. Omit them for a fresh one.
     */
    fun deriveKek(
        passphrase: CharArray,
        salt: ByteArray = randomBytes(SALT_LEN),
        iterations: Int = DEFAULT_ITERATIONS
    ): Kek {
        val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
        // PBKDF2 implementations disagree on how to turn chars into bytes (some historically kept
        // only the low byte of each char). Pre-encoding to UTF-8 and widening each byte makes the
        // derivation byte-identical on Android and on desktop JVMs, which a cross-platform restore
        // depends on.
        val widened = String(passphrase).toByteArray(Charsets.UTF_8)
            .map { (it.toInt() and 0xFF).toChar() }
            .toCharArray()
        val spec = PBEKeySpec(widened, salt, iterations, DEK_LEN * 8)
        try {
            val derived = factory.generateSecret(spec).encoded
            return Kek(SecretKeySpec(derived, "AES"), salt, iterations)
        } finally {
            spec.clearPassword()
            widened.fill('\u0000')
        }
    }

    // ---- Encryption ----

    /** Encrypts [plaintext] into a self-contained envelope. */
    fun encrypt(plaintext: ByteArray, kek: Kek): ByteArray {
        val dek = randomBytes(DEK_LEN)
        try {
            val header = buildHeader(kek, dek)
            val payloadIv = randomBytes(GCM_IV_LEN)
            val cipher = gcm(Cipher.ENCRYPT_MODE, SecretKeySpec(dek, "AES"), payloadIv)
            return header + payloadIv + cipher.doFinal(plaintext)
        } finally {
            dek.fill(0)
        }
    }

    /** Streaming form of [encrypt], for payloads too large to hold twice in memory (the DB). */
    fun encrypt(input: InputStream, output: OutputStream, kek: Kek) {
        val dek = randomBytes(DEK_LEN)
        try {
            output.write(buildHeader(kek, dek))
            val payloadIv = randomBytes(GCM_IV_LEN)
            output.write(payloadIv)

            val cipher = gcm(Cipher.ENCRYPT_MODE, SecretKeySpec(dek, "AES"), payloadIv)
            val buffer = ByteArray(STREAM_BUFFER)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                cipher.update(buffer, 0, read)?.let { output.write(it) }
            }
            cipher.doFinal()?.let { output.write(it) }
            output.flush()
        } finally {
            dek.fill(0)
        }
    }

    // ---- Decryption ----

    /** Decrypts an envelope produced by [encrypt]. Throws if [passphrase] is wrong or data is corrupt. */
    fun decrypt(envelope: ByteArray, passphrase: CharArray): ByteArray {
        return decryptStream(envelope.inputStream(), passphrase) { cipher, input ->
            cipher.doFinal(input.readBytes())
        }
    }

    /** Streaming form of [decrypt]. */
    fun decrypt(input: InputStream, output: OutputStream, passphrase: CharArray) {
        decryptStream(input, passphrase) { cipher, stream ->
            val buffer = ByteArray(STREAM_BUFFER)
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                cipher.update(buffer, 0, read)?.let { output.write(it) }
            }
            cipher.doFinal()?.let { output.write(it) }
            output.flush()
            ByteArray(0)
        }
    }

    /**
     * True if [prefix] starts with the portable-envelope magic. Restore uses this to reject (rather
     * than silently mangle) legacy Keystore-encrypted files, which no passphrase can open.
     */
    fun isPortableEnvelope(prefix: ByteArray): Boolean =
        prefix.size >= MAGIC.size && MAGIC.indices.all { prefix[it] == MAGIC[it] }

    // ---- Recovery keys ----

    /**
     * Generates a 160-bit recovery key as Crockford Base32 in dash-separated groups of four,
     * e.g. `K3M9-7QRT-...`. Shown to the user once. It is the only thing that can open their backups.
     */
    fun generateRecoveryKey(): String {
        val bytes = randomBytes(RECOVERY_KEY_BYTES)
        val sb = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        for (byte in bytes) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                sb.append(CROCKFORD[(buffer shr (bitsLeft - 5)) and 0x1F])
                bitsLeft -= 5
            }
        }
        if (bitsLeft > 0) sb.append(CROCKFORD[(buffer shl (5 - bitsLeft)) and 0x1F])
        return sb.chunked(4).joinToString("-")
    }

    // ---- Internals ----

    private fun buildHeader(kek: Kek, dek: ByteArray): ByteArray {
        val wrapIv = randomBytes(GCM_IV_LEN)
        val wrappedDek = gcm(Cipher.ENCRYPT_MODE, kek.key, wrapIv).doFinal(dek)

        val out = java.io.ByteArrayOutputStream()
        out.write(MAGIC)
        out.write(FORMAT_VERSION)
        out.write(KDF_PBKDF2_HMAC_SHA512)
        writeInt(out, kek.iterations)
        out.write(kek.salt.size)
        out.write(kek.salt)
        out.write(wrapIv)
        writeShort(out, wrappedDek.size)
        out.write(wrappedDek)
        return out.toByteArray()
    }

    /** Parses the header, unwraps the DEK, and hands a ready payload cipher to [body]. */
    private fun decryptStream(
        input: InputStream,
        passphrase: CharArray,
        body: (Cipher, InputStream) -> ByteArray
    ): ByteArray {
        val magic = readFully(input, MAGIC.size)
        require(isPortableEnvelope(magic)) {
            "Not a portable Somn backup — this file was encrypted with a device-bound key and " +
                "cannot be restored on another device or install."
        }

        val formatVersion = readByte(input)
        require(formatVersion == FORMAT_VERSION) {
            "Unsupported backup format version $formatVersion (this build understands $FORMAT_VERSION)"
        }
        val kdfId = readByte(input)
        require(kdfId == KDF_PBKDF2_HMAC_SHA512) { "Unsupported KDF id $kdfId" }

        val iterations = readInt(input)
        val salt = readFully(input, readByte(input))
        val wrapIv = readFully(input, GCM_IV_LEN)
        val wrappedDek = readFully(input, readShort(input))

        val kek = deriveKek(passphrase, salt, iterations)
        val dek = gcm(Cipher.DECRYPT_MODE, kek.key, wrapIv).doFinal(wrappedDek)
        try {
            val payloadIv = readFully(input, GCM_IV_LEN)
            return body(gcm(Cipher.DECRYPT_MODE, SecretKeySpec(dek, "AES"), payloadIv), input)
        } finally {
            dek.fill(0)
        }
    }

    private fun gcm(mode: Int, key: SecretKey, iv: ByteArray): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply {
            init(mode, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        }

    private fun randomBytes(size: Int) = ByteArray(size).also { secureRandom.nextBytes(it) }

    private fun writeInt(out: java.io.ByteArrayOutputStream, value: Int) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun writeShort(out: java.io.ByteArrayOutputStream, value: Int) {
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun readByte(input: InputStream): Int {
        val b = input.read()
        if (b == -1) throw EOFException("Backup ended mid-header")
        return b
    }

    private fun readShort(input: InputStream): Int = (readByte(input) shl 8) or readByte(input)

    private fun readInt(input: InputStream): Int =
        (readByte(input) shl 24) or (readByte(input) shl 16) or (readByte(input) shl 8) or readByte(input)

    /**
     * Reads exactly [size] bytes. [InputStream.read] may return short on network/SAF streams, so
     * every header field must loop rather than trust a single call.
     */
    private fun readFully(input: InputStream, size: Int): ByteArray {
        val buffer = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = input.read(buffer, offset, size - offset)
            if (read == -1) throw EOFException("Backup ended before $size bytes could be read")
            offset += read
        }
        return buffer
    }
}
