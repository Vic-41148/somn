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

/** WebDAV NasClient implementation — the only transport [NasProtocol] currently offers. */
@Singleton
class NasClientImpl @Inject constructor(
    private val preferencesRepository: SomnPreferencesRepository
) : NasClient {

    companion object {
        private const val TAG = "NasClientImpl"
        private const val CONNECT_TIMEOUT = 10_000
        private const val READ_TIMEOUT = 30_000

        /**
         * Pure so the scheme decision can be tested directly — this is the line that used to leak
         * WebDAV credentials. The scheme follows the user's explicit [NasConfig.useHttps] choice,
         * never the port number: inferring it from the port meant a NAS on, say, 8443 silently got
         * plain HTTP and sent its Basic-auth header in the clear.
         */
        internal fun buildBaseUrl(config: NasConfig): String {
            val scheme = if (config.useHttps) "https" else "http"
            val defaultPort = if (config.useHttps) 443 else 80
            val portSuffix = if (config.port == defaultPort) "" else ":${config.port}"
            val path = config.path.trimStart('/')
            return "$scheme://${config.host}$portSuffix/$path"
        }
    }

    override suspend fun testConnection(config: NasConfig): Boolean = withContext(Dispatchers.IO) {
        when (config.protocol) {
            NasProtocol.WEBDAV -> testWebDav(config)
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
        }
    }

    override suspend fun listFiles(config: NasConfig, remotePath: String): List<String> =
        withContext(Dispatchers.IO) {
            when (config.protocol) {
                NasProtocol.WEBDAV -> listWebDav(config, remotePath)
            }
        }

    override suspend fun delete(config: NasConfig, remotePath: String): Boolean =
        withContext(Dispatchers.IO) {
            when (config.protocol) {
                NasProtocol.WEBDAV -> deleteWebDav(config, remotePath)
            }
        }

    // ── WebDAV ───────────────────────────────────────────────────────────

    private fun buildBaseUrl(config: NasConfig): String = Companion.buildBaseUrl(config)

    /**
     * Android blocks cleartext HTTP by default at this targetSdk, so a plain-HTTP NAS fails with a
     * generic-looking IOException that reads like an unreachable host. Name the real cause instead
     * of letting users chase a network problem they do not have.
     */
    private fun logWebDavFailure(message: String, config: NasConfig, e: Exception) {
        if (!config.useHttps && e.message?.contains("Cleartext", ignoreCase = true) == true) {
            Log.e(
                TAG,
                "$message: Android blocked a cleartext HTTP request to ${config.host}. " +
                    "Enable HTTPS on the NAS connection — Somn does not permit unencrypted traffic.",
                e
            )
        } else {
            Log.e(TAG, message, e)
        }
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
        // disconnect() used to only run on the success path — an exception from
        // conn.responseCode (network failure, the exact scenario a NAS sync worker frequently
        // hits) left the underlying socket connection leaked instead of released.
        var conn: HttpURLConnection? = null
        return try {
            conn = openConnection(buildBaseUrl(config), "OPTIONS", config)
            val code = conn.responseCode
            code in 200..299
        } catch (e: Exception) {
            logWebDavFailure("WebDAV test failed", config, e)
            false
        } finally {
            conn?.disconnect()
        }
    }

    private suspend fun uploadWebDav(
        config: NasConfig,
        remotePath: String,
        data: InputStream,
        length: Long
    ): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            val url = "${buildBaseUrl(config)}/${remotePath.trimStart('/')}"
            conn = openConnection(url, "PUT", config)
            conn.doOutput = true
            if (length > 0) conn.setFixedLengthStreamingMode(length)
            conn.setRequestProperty("Content-Type", "application/octet-stream")

            BufferedOutputStream(conn.outputStream).use { out ->
                data.copyTo(out, bufferSize = 8192)
                out.flush()
            }

            val code = conn.responseCode
            code in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "WebDAV upload failed: $remotePath", e)
            false
        } finally {
            conn?.disconnect()
        }
    }

    private suspend fun listWebDav(config: NasConfig, remotePath: String): List<String> {
        var conn: HttpURLConnection? = null
        return try {
            val url = "${buildBaseUrl(config)}/${remotePath.trimStart('/')}"
            conn = openConnection(url, "PROPFIND", config)
            conn.setRequestProperty("Depth", "1")
            conn.setRequestProperty("Content-Type", "application/xml")

            val code = conn.responseCode
            if (code !in 200..299) {
                return emptyList()
            }

            // Simple href extraction — good enough for file listing
            val body = conn.inputStream.bufferedReader().readText()

            val hrefRegex = Regex("<D:href>(.*?)</D:href>", RegexOption.IGNORE_CASE)
            hrefRegex.findAll(body)
                .map { it.groupValues[1].substringAfterLast('/') }
                .filter { it.isNotBlank() }
                .toList()
        } catch (e: Exception) {
            Log.e(TAG, "WebDAV list failed: $remotePath", e)
            emptyList()
        } finally {
            conn?.disconnect()
        }
    }

    private suspend fun deleteWebDav(config: NasConfig, remotePath: String): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            val url = "${buildBaseUrl(config)}/${remotePath.trimStart('/')}"
            conn = openConnection(url, "DELETE", config)
            val code = conn.responseCode
            code in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "WebDAV delete failed: $remotePath", e)
            false
        } finally {
            conn?.disconnect()
        }
    }
}
