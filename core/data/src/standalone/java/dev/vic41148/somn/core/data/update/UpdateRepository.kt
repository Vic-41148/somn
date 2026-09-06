package dev.vic41148.somn.core.data.update

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.vic41148.somn.core.data.util.Checksum
import dev.vic41148.somn.core.domain.model.ReleaseInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin HTTP client over [HttpURLConnection] (zero third-party networking deps, matching the app's
 * low-dependency approach) for the GitHub Releases API. All heavy work offloads to [Dispatchers.IO]
 * and every response is capped to a bounded size so memory stays flat. GitHub's unauthenticated
 * release endpoint is rate-limited to 60 requests/hour per IP - the caller schedules at daily or
 * weekly cadence, and failures degrade to "no update" rather than ever retrying in a tight loop.
 */
@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val cacheDir
        get() = context.cacheDir

    /**
     * Fetches the latest non-prerelease release and resolves its APK checksum (preferring the
     * checksums.txt asset over anything embedded in the body). Returns null when there is no
     * release, or when [UpdateConfig.repoOwner]/[UpdateConfig.repoName] point nowhere.
     */
    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val latest = fetchLatest() ?: return@withContext UpdateCheckResult.NoUpdate
        if (latest.isPrerelease || latest.apkUrl == null) {
            return@withContext UpdateCheckResult.NoUpdate
        }

        val checksumUrl = latest.checksumAssetUrl
            ?: resolveChecksumAssetUrl(latest.tag)
        val checksum: String? = if (checksumUrl != null) {
            fetchChecksum(checksumUrl, latest.apkUrl)
        } else {
            latest.checksumSha256
        }

        val resolved = latest.copy(checksumSha256 = checksum)
        return@withContext if (resolved.apkUrl == null) {
            UpdateCheckResult.NoUpdate
        } else {
            UpdateCheckResult.Available(resolved)
        }
    }

    /** Fetches release history (including prereleases) for the version list / downgrade screen. */
    suspend fun fetchReleaseHistory(): List<ReleaseInfo> = withContext(Dispatchers.IO) {
        try {
            val body = httpGet("${UpdateConfig.apiBase}/releases")
            ReleaseParser.parseHistory(body)
        } catch (e: IOException) {
            throw UpdateException("Could not load release history.", e)
        }
    }

    /** Streams the APK to [destFile], reporting [onProgress] bytes; returns the completed file. */
    suspend fun downloadApk(
        url: String,
        destFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        try {
            val connection = openConnection(url)
            try {
                val status = connection.responseCode
                if (status !in 200..299) {
                    throw IOException("Download failed (HTTP $status).")
                }
                val total = connection.contentLengthLong.coerceAtLeast(0L)
                destFile.outputStream().use { output ->
                    val input = connection.inputStream
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded > MAX_APK_BYTES) {
                            destFile.delete()
                            throw IOException("APK exceeded ${MAX_APK_BYTES} bytes")
                        }
                        if (downloaded % NOTIFY_EVERY_BYTES < DEFAULT_BUFFER_SIZE.toLong()) {
                            onProgress(downloaded, total)
                        }
                    }
                    onProgress(downloaded, total)
                }
                destFile
            } finally {
                connection.disconnect()
            }
        } catch (e: IOException) {
            throw UpdateException("Could not download the update APK.", e)
        }
    }

    /** Verifies [file] against [expectedSha256]; throws [ChecksumMismatchException] on failure. */
    fun verifyChecksum(file: File, expectedSha256: String?) {
        val actual = Checksum.sha256(file)
        if (expectedSha256.isNullOrBlank() || !Checksum.sha256Matches(expectedSha256, actual)) {
            file.delete()
            throw ChecksumMismatchException(expectedSha256, actual)
        }
    }

    /**
     * Authenticity gate: checksum proves the file matches the release notes, this proves the
     * release notes match the installed app. The downloaded APK must carry the same signing
     * certificate set, otherwise the file is deleted and the install refused.
     */
    fun verifySameSigner(file: File) {
        val pm = context.packageManager
        val installed = runCatching {
            pm.getPackageInfo(
                context.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
            ).signingInfo?.apkContentsSigners
        }.getOrNull()
        val archive = runCatching {
            pm.getPackageArchiveInfo(
                file.absolutePath, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
            )?.signingInfo?.apkContentsSigners
        }.getOrNull()
        if (installed.isNullOrEmpty() || archive.isNullOrEmpty() || installed.toSet() != archive.toSet()) {
            file.delete()
            throw UpdateException("Update is not signed by the same certificate as the installed app.")
        }
    }

    /** Hosts an update URL may point at: the API, release pages, and the CDN they redirect to. */
    fun isAllowedHost(url: String): Boolean {
        return try {
            val parsed = URL(url)
            parsed.protocol.equals("https", ignoreCase = true) &&
                parsed.host.lowercase() in ALLOWED_UPDATE_HOSTS
        } catch (_: Exception) {
            false
        }
    }

    // ---- internals ----

    private fun fetchLatest(): ReleaseInfo? {
        try {
            val body = httpGet("${UpdateConfig.apiBase}/releases/latest")
            return ReleaseParser.parseLatest(body)
        } catch (e: IOException) {
            // 404 (no releases yet) and rate limits are both "no update today", not errors.
            return null
        }
    }

    private fun resolveChecksumAssetUrl(tag: String): String? {
        val escape = { s: String -> java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20") }
        return "https://github.com/${UpdateConfig.repoOwner}/${UpdateConfig.repoName}/releases/download/${escape(tag)}/checksums.txt"
    }

    private fun fetchChecksum(checksumUrl: String, apkUrl: String?): String? {
        val apkName = apkUrl?.substringAfterLast('/')
        // 404 (the release ships no checksums.txt) resolves to null -> install is refused solely on
        // the body fallback. Any *real* error also degrades to null so we never half-install.
        return try {
            val body = httpGet(checksumUrl)
            ReleaseParser.extractChecksumFromBody(body, apkName)
        } catch (e: IOException) {
            null
        }
    }

    private fun httpGet(url: String): String {
        val connection = openConnection(url)
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IOException("HTTP $status for $url")
            }
            val maxBytes = (MAX_BODY_BYTES + 1).toInt()
            val bytes = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            val connectionInput = connection.inputStream
            var total = 0
            while (true) {
                val read = connectionInput.read(buffer)
                if (read == -1) break
                total += read
                if (total > MAX_BODY_BYTES) {
                    throw IOException("Response exceeded ${MAX_BODY_BYTES} bytes")
                }
                bytes.write(buffer, 0, read)
            }
            return String(bytes.toByteArray(), Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        return openCheckedConnection(url, MAX_REDIRECT_HOPS)
    }

    /**
     * Follows redirects manually so every hop stays on the host allowlist — the platform
     * follower would silently land on an attacker's host from a tampered API response.
     */
    private fun openCheckedConnection(url: String, hopsLeft: Int): HttpURLConnection {
        if (!isAllowedHost(url)) throw IOException("Update URL host is not allowed: $url")
        if (hopsLeft < 0) throw IOException("Too many redirects for $url")
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "Somn-UpdateChecker")
        val status = connection.responseCode
        if (status in 300..399) {
            val location = connection.getHeaderField("Location")
                ?: throw IOException("Redirect without Location for $url")
            connection.disconnect()
            return openCheckedConnection(URL(URL(url), location).toString(), hopsLeft - 1)
        }
        return connection
    }

    private fun downloadDir(): File = File(cacheDir, "update_downloads").apply { mkdirs() }

    fun prepareDownloadFile(): File {
        val dir = downloadDir()
        dir.listFiles()?.forEach { it.delete() }
        return File(dir, "somn-update.apk")
    }

    sealed class UpdateCheckResult {
        data class Available(val release: ReleaseInfo) : UpdateCheckResult()
        data object NoUpdate : UpdateCheckResult()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 15_000
        const val MAX_BODY_BYTES = 2 * 1024 * 1024
        const val NOTIFY_EVERY_BYTES = 256 * 1024L
        /** APKs are ~15 MB; anything past this is not our update. */
        const val MAX_APK_BYTES = 100L * 1024 * 1024
        const val MAX_REDIRECT_HOPS = 5
        val ALLOWED_UPDATE_HOSTS = setOf(
            "api.github.com",
            "github.com",
            "objects.githubusercontent.com"
        )
    }
}

object UpdateConfig {
    const val repoOwner = "Vic-41148"
    const val repoName = "somn"
    const val apiBase = "https://api.github.com/repos/$repoOwner/$repoName"
}

open class UpdateException(message: String, cause: Throwable? = null) : IOException(message, cause)

class ChecksumMismatchException(expected: String?, actual: String) :
    UpdateException("Checksum mismatch: expected sha256 $expected, got $actual")