package dev.vic41148.somn.core.data.util

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * sha256 helpers for the update integrity gate. The download is only ever installed after its
 * digest matches what the release's checksums.txt advertised - a mismatch is a hard failure, not
 * a "best effort, probably fine" warning (the file could have been tampered with in transit).
 */
object Checksum {

    fun sha256(file: File): String {
        check(file.exists()) { "Checksum target missing: ${file.path}" }
        file.inputStream().use { return sha256(it) }
    }

    fun sha256(bytes: ByteArray): String = sha256(bytes.inputStream())

    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().toHex()
    }

    fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    /** Pure comparison, case-insensitive. Blank/false never throws - the gate is [UpdateRepository.verifyChecksum]. */
    fun sha256Matches(expectedHex: String, actualHex: String): Boolean {
        val normalizedExpected = expectedHex.trim().lowercase()
        val normalizedActual = actualHex.trim().lowercase()
        if (normalizedExpected.isEmpty()) return false
        return normalizedExpected == normalizedActual
    }
}