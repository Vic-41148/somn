package dev.vic41148.somn.core.data.update

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseParserTest {

    private fun releaseJson(
        tag: String = "v0.2.0",
        prerelease: Boolean = false,
        apkAsset: Boolean = true,
        checksumAsset: Boolean = false,
        body: String = ""
    ): String {
        val assets = org.json.JSONArray().apply {
            if (apkAsset) {
                put(org.json.JSONObject().apply {
                    put("name", "somn-release.apk")
                    put("browser_download_url", "https://github.com/x/somn/releases/download/$tag/somn-release.apk")
                })
            }
            if (checksumAsset) {
                put(org.json.JSONObject().apply {
                    put("name", "checksums.txt")
                    put("browser_download_url", "https://github.com/x/somn/releases/download/$tag/checksums.txt")
                })
            }
        }
        return org.json.JSONObject().apply {
            put("tag_name", tag)
            put("name", "Somn $tag")
            put("prerelease", prerelease)
            put("published_at", "2026-08-30T12:00:00Z")
            put("body", body)
            put("assets", assets)
        }.toString()
    }

    @Test
    fun `parse latest picks the apk asset and strips the v prefix`() {
        val release = ReleaseParser.parseLatest(releaseJson())
        assertEquals("v0.2.0", release.tag)
        assertEquals("0.2.0", release.versionName)
        assertEquals("https://github.com/x/somn/releases/download/v0.2.0/somn-release.apk", release.apkUrl)
        assertEquals("2026-08-30T12:00:00Z", release.publishedAt)
        assertTrue(!release.isPrerelease)
    }

    @Test
    fun `parse latest marks prereleases`() {
        val release = ReleaseParser.parseLatest(releaseJson(tag = "v0.2.0-rc.1", prerelease = true))
        assertTrue(release.isPrerelease)
        assertEquals("0.2.0-rc.1", release.versionName)
    }

    @Test
    fun `missing assets result in null apk url, not a crash`() {
        val release = ReleaseParser.parseLatest(releaseJson(apkAsset = false))
        assertNull(release.apkUrl)
    }

    @Test
    fun `checksum from named line in checksums body`() {
        val body = """
            abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890  somn-release.apk
            ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff  other.apk
        """.trimIndent()
        val sha = ReleaseParser.extractChecksumFromBody(body, "somn-release.apk")
        assertEquals("abcdef1234567890".repeat(4), sha)
    }

    @Test
    fun `checksum refuses bare hash with no apk name`() {
        val body = "release built at 2026-08-30\nhash abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
        assertNull(ReleaseParser.extractChecksumFromBody(body, null))
    }

    @Test
    fun `checksum null on blank body`() {
        assertNull(ReleaseParser.extractChecksumFromBody("", "somn-release.apk"))
        assertNull(ReleaseParser.extractChecksumFromBody("no hash in here", "somn-release.apk"))
    }

    @Test
    fun `parse history dedupes by tag`() {
        val raw = "[${releaseJson(tag = "v0.2.0")}, ${releaseJson(tag = "v0.2.0")}, ${releaseJson(tag = "v0.1.2")}]"
        val history = ReleaseParser.parseHistory(raw)
        assertEquals(2, history.size)
        assertEquals(listOf("v0.2.0", "v0.1.2"), history.map { it.tag })
    }
}