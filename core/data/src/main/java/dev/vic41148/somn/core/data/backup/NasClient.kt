package dev.vic41148.somn.core.data.backup

import dev.vic41148.somn.core.domain.model.NasConfig
import java.io.InputStream

/**
 * Abstraction over NAS protocol specifics.
 * Implementations handle WebDAV/SMB/NFS transport.
 */
interface NasClient {

    /** Test connectivity and auth. Returns true if reachable + writable. */
    suspend fun testConnection(config: NasConfig): Boolean

    /** Upload [data] stream to [remotePath] on the configured NAS. */
    suspend fun upload(config: NasConfig, remotePath: String, data: InputStream, length: Long): Boolean

    /** List files at [remotePath]. Returns filenames. */
    suspend fun listFiles(config: NasConfig, remotePath: String): List<String>

    /** Delete [remotePath] on NAS. */
    suspend fun delete(config: NasConfig, remotePath: String): Boolean
}
