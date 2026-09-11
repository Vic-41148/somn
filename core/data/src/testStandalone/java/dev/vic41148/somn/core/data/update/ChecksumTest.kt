package dev.vic41148.somn.core.data.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.vic41148.somn.core.data.util.Checksum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ChecksumTest {

    private fun tempFile(contents: String): File =
        File.createTempFile("checksum", ".bin").apply {
            writeBytes(contents.toByteArray())
            deleteOnExit()
        }

    @Test
    fun `sha256 of a file matches the streaming variant`() {
        val file = tempFile("somn checksum test")
        assertEquals(Checksum.sha256("somn checksum test".toByteArray()), Checksum.sha256(file))
        assertEquals(64, Checksum.sha256(file).length)
    }

    @Test
    fun `matching and uppercase checksums pass`() {
        val file = tempFile("hello")
        val hex = Checksum.sha256(file)
        assertTrue(Checksum.sha256Matches(hex, hex))
        assertTrue(Checksum.sha256Matches(hex.uppercase(), hex))
        assertFalse(Checksum.sha256Matches("", hex))
    }

    @Test
    fun `verifyChecksum rejects a tampered apk and deletes it`() {
        val appContext: Context = ApplicationProvider.getApplicationContext()
        val repo = UpdateRepository(appContext)
        val file = tempFile("hello")
        val wrong = "0000000000000000000000000000000000000000000000000000000000000000"
        assertThrows(ChecksumMismatchException::class.java) {
            repo.verifyChecksum(file, wrong)
        }
        assertFalse(file.exists())
    }

    @Test
    fun `verifyChecksum refuses a release with no published checksum`() {
        val appContext: Context = ApplicationProvider.getApplicationContext()
        val repo = UpdateRepository(appContext)
        val file = tempFile("hello")
        assertThrows(ChecksumMismatchException::class.java) {
            repo.verifyChecksum(file, null)
        }
        assertFalse(file.exists())
    }

    @Test
    fun `verifyChecksum accepts a correct checksum`() {
        val appContext: Context = ApplicationProvider.getApplicationContext()
        val repo = UpdateRepository(appContext)
        val file = tempFile("hello")
        repo.verifyChecksum(file, Checksum.sha256(file))
        assertTrue(file.exists())
    }
}