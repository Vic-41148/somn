package dev.vic41148.somn.core.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.vic41148.somn.core.data.backup.BoundedInputStream
import dev.vic41148.somn.core.data.backup.PortableCrypto
import dev.vic41148.somn.core.data.database.SleepDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: SomnPreferencesRepository,
    private val database: SleepDatabase,
    private val portableCrypto: PortableCrypto,
    private val keyManager: dev.vic41148.somn.core.data.database.DatabaseKeyManager
) {
    companion object {
        private const val TAG = "BackupRepository"

        /** Name the DB is written under inside the backup tree. */
        const val DB_BACKUP_NAME = "sleep_tracker.db"
        /** Same payload, passphrase-encrypted. Only one of the two is present in a given backup. */
        const val DB_BACKUP_NAME_ENCRYPTED = "sleep_tracker.db.enc"

        /** Staging cap: databases are megabytes; anything past this is not our backup. */
        const val MAX_RESTORE_BYTES = 256L * 1024 * 1024

        const val PREFS_BACKUP_NAME = "somn_prefs.preferences_pb"

        private const val PREFS_RELATIVE_PATH = "datastore/somn_prefs.preferences_pb"

        /**
         * Every SQLite file starts with these 16 bytes ("SQLite format 3" + NUL); used to
         * reject garbage before overwriting a live DB.
         */
        private val SQLITE_HEADER =
            "SQLite format 3".toByteArray(Charsets.US_ASCII) + 0x00.toByte()
    }

    sealed interface RestoreResult {
        /** DB replaced on disk. The process must restart before Room is reopened. */
        data object SuccessRestartRequired : RestoreResult
        data class Failure(val message: String) : RestoreResult
    }

    suspend fun performSilentBackup() = withContext(Dispatchers.IO) {
        val backupUriStr = preferencesRepository.backupUri.first() ?: return@withContext
        val uri = Uri.parse(backupUriStr)
        val documentTree = DocumentFile.fromTreeUri(context, uri)
        if (documentTree == null || !documentTree.canWrite()) {
            return@withContext
        }

        // Fold the write-ahead log into the main DB file first. Without this the copy below is a
        // snapshot missing every commit still sitting in -wal — i.e. potentially the entire most
        // recent night.
        checkpointWal()

        val dbFile = databaseFile()
        if (dbFile.exists()) {
            val passphrase = preferencesRepository.getBackupPassphrase()
            if (passphrase != null) {
                val kek = portableCrypto.deriveKek(passphrase.toCharArray())
                // Envelope a plaintext export, never the live ciphertext file: the envelope
                // must restore on installs holding a different database key.
                val plain = File.createTempFile("backup-plain", ".db", context.cacheDir)
                try {
                    keyManager.exportDecryptedCopy(database.openHelper.writableDatabase, plain)
                    writeToDocumentTree(documentTree, DB_BACKUP_NAME_ENCRYPTED) { output ->
                        plain.inputStream().use { portableCrypto.encrypt(it, output, kek) }
                    }
                } finally {
                    plain.delete()
                }
                // Drop any plaintext copy left by a pre-passphrase backup so the two do not diverge.
                documentTree.findFile(DB_BACKUP_NAME)?.delete()
            } else {
                // No passphrase yet — keep the existing plaintext behaviour rather than silently
                // skipping the backup entirely. Restore still works. Off-site sync stays disabled.
                writeToDocumentTree(documentTree, DB_BACKUP_NAME) { output ->
                    dbFile.inputStream().use { it.copyTo(output) }
                }
            }
        } else {
            Log.w(TAG, "Database file missing at ${dbFile.path} — nothing to back up")
        }

        val prefsFile = File(context.filesDir, PREFS_RELATIVE_PATH)
        if (prefsFile.exists()) {
            writeToDocumentTree(documentTree, PREFS_BACKUP_NAME) { output ->
                prefsFile.inputStream().use { it.copyTo(output) }
            }
        }
    }

    /**
     * Restores the sleep database from a previously written backup file.
     *
     * [passphrase] is required for encrypted backups and ignored for plaintext ones. On success the
     * database file has been replaced but the in-memory Room instance is stale, so the caller must
     * restart the process — hence [RestoreResult.SuccessRestartRequired] rather than a bare boolean.
     */
    suspend fun restoreDatabase(
        backupUri: Uri,
        passphrase: String? = null
    ): RestoreResult = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "restore-${System.currentTimeMillis()}.db")
        try {
            val resolver = context.contentResolver

            // Fast reject before a single byte is staged: providers report size up front.
            // Unknown size (-1) still stages, but the bounded copy below caps it.
            val declaredSize = resolver.query(backupUri, arrayOf(OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getLong(0) else -1L
                } ?: -1L
            if (declaredSize > MAX_RESTORE_BYTES) {
                return@withContext RestoreResult.Failure("Backup file is larger than expected")
            }

            val prefix = resolver.openInputStream(backupUri)?.use { input ->
                ByteArray(SQLITE_HEADER.size).also { buffer ->
                    var offset = 0
                    while (offset < buffer.size) {
                        val read = input.read(buffer, offset, buffer.size - offset)
                        if (read == -1) break
                        offset += read
                    }
                }
            } ?: return@withContext RestoreResult.Failure("Could not open the backup file")

            val encrypted = portableCrypto.isPortableEnvelope(prefix)
            if (encrypted && passphrase.isNullOrBlank()) {
                return@withContext RestoreResult.Failure("This backup is encrypted — enter your recovery passphrase")
            }

            // A device-bound Keystore blob from an older Somn names its own failure: no key
            // the user types can ever open it. Everything else stages for validation below —
            // including raw ciphertext backups, which carry no SQLite header by design.
            if (!encrypted && !prefix.startsWithSqliteHeader() && looksLikeLegacyKeystoreBlob(prefix)) {
                return@withContext RestoreResult.Failure(
                    "This backup was encrypted with a device-bound key from an older version of " +
                        "Somn and can only be restored on the device that wrote it. No recovery " +
                        "key can open it."
                )
            }

            resolver.openInputStream(backupUri)?.use { input ->
                staging.outputStream().use { output ->
                    // Bounded on the way in: a lying provider or a corrupt envelope must fail
                    // here, not after filling the disk.
                    val bounded = BoundedInputStream(input, MAX_RESTORE_BYTES)
                    if (encrypted) {
                        portableCrypto.decrypt(bounded, output, passphrase!!.toCharArray())
                    } else {
                        bounded.copyTo(output)
                    }
                }
            } ?: return@withContext RestoreResult.Failure("Could not open the backup file")

            if (staging.length() > MAX_RESTORE_BYTES) {
                return@withContext RestoreResult.Failure("Backup file is larger than expected")
            }

            val key = keyManager.getOrCreatePassphrase()
            val target = databaseFile()
            if (looksLikeSqlite(staging)) {
                if (!keyManager.passesIntegrityCheck(staging, null)) {
                    return@withContext RestoreResult.Failure(
                        "Backup failed its integrity check and was not restored"
                    )
                }
                // Only now is it safe to touch the live database.
                checkpointWal()
                database.close()
                keyManager.importPlaintextCopy(staging)
            } else if (keyManager.isEncryptedSQLite(staging, key)) {
                // Raw ciphertext backup from this install (no-passphrase backups copy the live
                // file). It only opens with this install's key.
                if (!keyManager.passesIntegrityCheck(staging, key)) {
                    return@withContext RestoreResult.Failure(
                        "Backup failed its integrity check and was not restored"
                    )
                }
                checkpointWal()
                database.close()
                staging.copyTo(target, overwrite = true)
                // The old -wal/-shm describe the *previous* database and would corrupt the restored one.
                File("${target.path}-wal").delete()
                File("${target.path}-shm").delete()
            } else {
                return@withContext RestoreResult.Failure(
                    "This file is not a readable Somn backup"
                )
            }

            Log.i(TAG, "Database restored from $backupUri")
            RestoreResult.SuccessRestartRequired
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed", e)
            RestoreResult.Failure(e.message ?: "Restore failed")
        } finally {
            staging.delete()
        }
    }

    /** Resolves the real on-disk database path. */
    internal fun databaseFile(): File = context.getDatabasePath(SleepDatabase.DATABASE_NAME)

    /**
     * Merges the write-ahead log into the main database file so a plain file copy is a complete,
     * consistent snapshot.
     */
    internal fun checkpointWal() {
        try {
            database.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(TRUNCATE)")
                .use { it.moveToFirst() }
        } catch (e: Exception) {
            Log.w(TAG, "WAL checkpoint failed; backup may omit recent writes", e)
        }
    }

    private fun looksLikeSqlite(file: File): Boolean {
        if (file.length() < SQLITE_HEADER.size) return false
        val header = ByteArray(SQLITE_HEADER.size)
        file.inputStream().use { it.read(header) }
        return header.contentEquals(SQLITE_HEADER)
    }

    private fun ByteArray.startsWithSqliteHeader(): Boolean =
        size >= SQLITE_HEADER.size && SQLITE_HEADER.indices.all { this[it] == SQLITE_HEADER[it] }

    /**
     * Recognises the shape written by [dev.vic41148.somn.core.data.backup.EncryptionUtils]: a
     * single IV-length byte followed by that many IV bytes. Only used to explain *why* a file cannot
     * be restored — there is no way to actually decrypt one off-device.
     */
    private fun looksLikeLegacyKeystoreBlob(prefix: ByteArray): Boolean =
        prefix.isNotEmpty() && prefix[0].toInt() == 12

    private fun writeToDocumentTree(
        tree: DocumentFile,
        destName: String,
        write: (java.io.OutputStream) -> Unit
    ) {
        try {
            val destDoc = tree.findFile(destName)
                ?: tree.createFile("application/octet-stream", destName)
                ?: return
            context.contentResolver.openOutputStream(destDoc.uri, "wt")?.use(write)
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing $destName to backup tree", e)
        }
    }
}
