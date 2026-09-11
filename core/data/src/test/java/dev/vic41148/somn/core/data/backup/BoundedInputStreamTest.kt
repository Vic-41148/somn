package dev.vic41148.somn.core.data.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class BoundedInputStreamTest {

    @Test
    fun smallInputReadsFully() {
        val data = ByteArray(1024) { it.toByte() }
        val out = BoundedInputStream(ByteArrayInputStream(data), 2048).readBytes()
        assertThat(out).isEqualTo(data)
    }

    @Test
    fun oversizedInputThrows() {
        val data = ByteArray(3000)
        assertThrows(IOException::class.java) {
            BoundedInputStream(ByteArrayInputStream(data), 2048).readBytes()
        }
    }

    @Test
    fun readBoundedTextRespectsCap() {
        val text = "x".repeat(100)
        assertThat(
            ByteArrayInputStream(text.toByteArray()).readBoundedText(200, Charsets.UTF_8)
        ).isEqualTo(text)
        assertThrows(IOException::class.java) {
            ByteArrayInputStream(text.toByteArray()).readBoundedText(50, Charsets.UTF_8)
        }
    }
}
