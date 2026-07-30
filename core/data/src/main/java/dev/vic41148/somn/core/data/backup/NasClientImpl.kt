package dev.vic41148.somn.core.data.backup

import android.util.Log
import dev.vic41148.somn.core.data.repository.SomnPreferencesRepository
import dev.vic41148.somn.core.domain.model.NasConfig
import dev.vic41148.somn.core.domain.model.NasProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebDAV-first NasClient implementation.
 * SMB/NFS stubs return false — expand with jcifs-ng / libnfs when needed.
 */
@Singleton
class NasClientImpl @Inject constructor(
    private val preferencesRepository: SomnPreferencesRepository
) : NasClient {

    companion object {
        private const val TAG = "NasClientImpl"
        private const val CONNECT_TIMEOUT = 10_000
        private const val READ_TIMEOUT = 30_000
    }

    override suspend fun testConnection(config: NasConfig): Boolean = withContext(Dispatchers.IO) {
        when (config.protocol) {
            NasProtocol.WEBDAV -> testWebDav(config)
            NasProtocol.SMB -> {
                Log.w(TAG, "SMB not yet implemented")
                false
            }
            NasProtocol.NFS -> {
                Log.w(TAG, "NFS not yet implemented")
                false
            }
        }
    }

    override suspend fun upload(
        config: NasConfig,
        remotePath: String,
        data: InputStream,
        length: Long
    ): Boolean = withContext(Dispatchers.IO) {
        when (config.protocol) {
            NasProtocol.WEBDAV -> uploadWebDav(config, remotePath, data, length)
            else -> {
                Log.w(TAG, "${config.protocol} upload not yet implemented")
                false
            }
        }
    }

    override suspend fun listFiles(config: NasConfig, remotePath: String): List<String> =
        withContext(Dispatchers.IO) {
            when (config.protocol) {
                NasProtocol.WEBDAV -> listWebDav(config, remotePath)
                else -> {
                    Log.w(TAG, "${config.protocol} list not yet implemented")
                    emptyList()
                }
            }
        }

    override suspend fun delete(config: NasConfig, remotePath: String): Boolean =
        withContext(Dispatchers.IO) {
            when (config.protocol) {
                NasProtocol.WEBDAV -> deleteWebDav(config, remotePath)
                else -> {
                    Log.w(TAG, "${config.protocol} delete not yet implemented")
                    false
                }
            }
        }

    // ── WebDAV ───────────────────────────────────────────────────────────

    private fun buildBaseUrl(config: NasConfig): String {
        val scheme = if (config.port == 443) "https" else "http"
        val portSuffix = if (config.port == 80 || config.port == 443) "" else ":${config.port}"
        val path = config.path.trimStart('/')
        return "$scheme://${config.host}$portSuffix/$path"
    }

    private suspend fun openConnection(url: String, method: String, config: NasConfig): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = CONNECT_TIMEOUT
        conn.readTimeout = READ_TIMEOUT

        if (config.username.isNotBlank()) {
            val password = preferencesRepository.getNasPassword() ?: ""
            val credentials = android.util.Base64.encodeToString(
                "${config.username}:$password".toByteArray(),
                android.util.Base64.NO_WRAP
            )
            conn.setRequestProperty("Authorization", "Basic $credentials")
        }
        return conn
    }

    private suspend fun testWebDav(config: NasConfig): Boolean {
        return try {
            val conn = openConnection(buildBaseUrl(config), "OPTIONS", config)
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "WebDAV test failed", e)
            false
        }
    }

    private suspend fun uploadWebDav(
        config: NasConfig,
        remotePath: String,
        data: InputStream,
        length: Long
    ): Boolean {
        return try {
            val url = "${buildBaseUrl(config)}/${remotePath.trimStart('/')}"
            val conn = openConnection(url, "PUT", config)
            conn.doOutput = true
            if (length > 0) conn.setFixedLengthStreamingMode(length)
            conn.setRequestProperty("Content-Type", "application/octet-stream")

            BufferedOutputStream(conn.outputStream).use { out ->
                data.copyTo(out, bufferSize = 8192)
                out.flush()
            }

            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "WebDAV upload failed: $remotePath", e)
            false
        }
    }

    private suspend fun listWebDav(config: NasConfig, remotePath: String): List<String> {
        return try {
            val url = "${buildBaseUrl(config)}/${remotePath.trimStart('/')}"
            val conn = openConnection(url, "PROPFIND", config)
            conn.setRequestProperty("Depth", "1")
            conn.setRequestProperty("Content-Type", "application/xml")

            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                return emptyList()
            }

            // Simple href extraction — good enough for file listing
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val hrefRegex = Regex("<D:href>(.*?)</D:href>", RegexOption.IGNORE_CASE)
            hrefRegex.findAll(body)
                .map { it.groupValues[1].substringAfterLast('/') }
                .filter { it.isNotBlank() }
                .toList()
        } catch (e: Exception) {
            Log.e(TAG, "WebDAV list failed: $remotePath", e)
            emptyList()
        }
    }

    private suspend fun deleteWebDav(config: NasConfig, remotePath: String): Boolean {
        return try {
            val url = "${buildBaseUrl(config)}/${remotePath.trimStart('/')}"
            val conn = openConnection(url, "DELETE", config)
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "WebDAV delete failed: $remotePath", e)
            false
        }
    }
}
