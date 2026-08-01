package dev.vic41148.somn.core.data.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import javax.crypto.AEADBadTagException

/**
 * These run on a plain JVM with no Android classes, which is the point: the same code has to
 * produce and consume these envelopes off-device, so a desktop-JVM round trip is the actual
 * contract being tested.
 */
class PortableCryptoTest {

    private val crypto = PortableCrypto()
    private val passphrase = "K3M9-7QRT-8VWX-2NPD-5HJB".toCharArray()

    /** The real KDF cost is deliberately ~200ms; tests use a cheap one and pin params explicitly. */
    private fun cheapKek(pass: CharArray = passphrase, salt: ByteArray = ByteArray(16) { it.toByte() }) =
        crypto.deriveKek(pass, salt, iterations = 1_000)

    @Test
    fun `round trips a byte payload`() {
        val plaintext = "sleep session 2026-07-31".toByteArray()

        val envelope = crypto.encrypt(plaintext, cheapKek())
        val decrypted = crypto.decrypt(envelope, passphrase)

        assertThat(decrypted).isEqualTo(plaintext)
    }

    @Test
    fun `round trips a streamed payload larger than the buffer`() {
        // Larger than STREAM_BUFFER so the update/doFinal chunking path is actually exercised.
        val plaintext = ByteArray(50_000) { (it % 251).toByte() }

        val encrypted = ByteArrayOutputStream()
        crypto.encrypt(ByteArrayInputStream(plaintext), encrypted, cheapKek())

        val decrypted = ByteArrayOutputStream()
        crypto.decrypt(ByteArrayInputStream(encrypted.toByteArray()), decrypted, passphrase)

        assertThat(decrypted.toByteArray()).isEqualTo(plaintext)
    }

    @Test
    fun `stream and byte forms produce interchangeable envelopes`() {
        val plaintext = "interoperable".toByteArray()

        val streamed = ByteArrayOutputStream()
        crypto.encrypt(ByteArrayInputStream(plaintext), streamed, cheapKek())

        // Written by the streaming encoder, read by the byte decoder.
        assertThat(crypto.decrypt(streamed.toByteArray(), passphrase)).isEqualTo(plaintext)

        val boxed = crypto.encrypt(plaintext, cheapKek())
        val unboxed = ByteArrayOutputStream()
        crypto.decrypt(ByteArrayInputStream(boxed), unboxed, passphrase)
        assertThat(unboxed.toByteArray()).isEqualTo(plaintext)
    }

    @Test
    fun `wrong passphrase fails authentication instead of returning garbage`() {
        val envelope = crypto.encrypt("secret".toByteArray(), cheapKek())

        assertThrows(AEADBadTagException::class.java) {
            crypto.decrypt(envelope, "WRONG-PASSPHRASE".toCharArray())
        }
    }

    @Test
    fun `tampered ciphertext is rejected`() {
        val envelope = crypto.encrypt("secret".toByteArray(), cheapKek())
        envelope[envelope.size - 1] = (envelope[envelope.size - 1].toInt() xor 0x01).toByte()

        assertThrows(AEADBadTagException::class.java) { crypto.decrypt(envelope, passphrase) }
    }

    @Test
    fun `salt and iterations are recovered from the envelope`() {
        // Decryption is given only the passphrase, so it must read salt/iterations back out of the
        // header — a non-default iteration count proves the header is honoured, not assumed.
        val salt = ByteArray(16) { (it * 7).toByte() }
        val kek = crypto.deriveKek(passphrase, salt, iterations = 2_048)

        val envelope = crypto.encrypt("params".toByteArray(), kek)

        assertThat(crypto.decrypt(envelope, passphrase)).isEqualTo("params".toByteArray())
    }

    @Test
    fun `derivation is deterministic for the same salt and iterations`() {
        val salt = ByteArray(16) { 9 }
        val a = crypto.encrypt("x".toByteArray(), crypto.deriveKek(passphrase, salt, 1_000))
        val b = crypto.encrypt("x".toByteArray(), crypto.deriveKek(passphrase, salt, 1_000))

        // Different envelopes (fresh DEK and IVs each time) but both open with the same passphrase.
        assertThat(a).isNotEqualTo(b)
        assertThat(crypto.decrypt(a, passphrase)).isEqualTo(crypto.decrypt(b, passphrase))
    }

    @Test
    fun `envelopes are recognisable and legacy blobs are not`() {
        val envelope = crypto.encrypt("x".toByteArray(), cheapKek())

        assertThat(crypto.isPortableEnvelope(envelope)).isTrue()
        // Shape of a legacy EncryptionUtils blob: 1-byte IV length, then IV.
        assertThat(crypto.isPortableEnvelope(byteArrayOf(12) + ByteArray(12))).isFalse()
        assertThat(crypto.isPortableEnvelope(ByteArray(0))).isFalse()
    }

    @Test
    fun `a legacy Keystore blob is refused with an explanatory error rather than mangled`() {
        val legacy = byteArrayOf(12) + ByteArray(12) + "ciphertext".toByteArray()

        val error = assertThrows(IllegalArgumentException::class.java) {
            crypto.decrypt(legacy, passphrase)
        }
        assertThat(error).hasMessageThat().contains("device-bound key")
    }

    @Test
    fun `a truncated envelope fails loudly`() {
        val envelope = crypto.encrypt("x".toByteArray(), cheapKek())

        assertThrows(EOFException::class.java) {
            crypto.decrypt(envelope.copyOfRange(0, 12), passphrase)
        }
    }

    @Test
    fun `recovery keys are grouped Crockford Base32 and unique`() {
        val key = crypto.generateRecoveryKey()

        // 20 bytes -> 32 Base32 chars -> 8 groups of 4.
        assertThat(key).matches("[0-9A-HJKMNP-TV-Z]{4}(-[0-9A-HJKMNP-TV-Z]{4}){7}")
        assertThat(crypto.generateRecoveryKey()).isNotEqualTo(key)
    }
}
