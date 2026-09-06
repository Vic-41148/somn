package dev.vic41148.somn.core.data.diagnostics

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CrashLogStoreTest {

    @Test
    fun redactFoldsAppPrivatePaths() {
        val raw = "Caused by java.io.FileNotFoundException: " +
            "/data/user/0/dev.vic41148.somn/files/sleep_talk/clip.wav (No such file)"
        val redacted = CrashLogStore.redact(raw)
        assertThat(redacted).doesNotContain("dev.vic41148.somn")
        assertThat(redacted).doesNotContain("sleep_talk")
        assertThat(redacted).contains("FileNotFoundException")
    }

    @Test
    fun redactFoldsSharedStorage() {
        val raw = "Export failed: /storage/emulated/0/Download/somn_export.zip"
        assertThat(CrashLogStore.redact(raw)).doesNotContain("/storage/emulated/0")
    }
}
