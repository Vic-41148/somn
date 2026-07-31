package dev.vic41148.somn.core.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val portableCrypto: PortableCrypto
) {
    companion object {
        private const val TAG = "BackupRepository"

        /** Name the DB is written under inside the backup tree. */
        const val DB_BACKUP_NAME = "sleep_tracker.db"

        /** Same payload, passphrase-encrypted. Only one of the two is present in a given backup. */
        const val DB_BACKUP_NAME_ENCRYPTED = "sleep_tracker.db.enc"

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
                writeToDocumentTree(documentTree, DB_BACKUP_NAME_ENCRYPTED) { output ->
                    dbFile.inputStream().use { portableCrypto.encrypt(it, output, kek) }
                }
                // Drop any plaintext copy left by a pre-passphrase backup so the two don't diverge.
                documentTree.findFile(DB_BACKUP_NAME)?.delete()
            } else {
                // No passphrase yet — keep the existing plaintext behaviour rather than silently
                // skipping the backup entirely. Restore still works; off-site sync stays disabled.
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

            val magic = resolver.openInputStream(backupUri)?.use { input ->
                ByteArray(PortableCrypto.MAGIC.size).also { buffer ->
                    var offset = 0
                    while (offset < buffer.size) {
                        val read = input.read(buffer, offset, buffer.size - offset)
                        if (read == -1) break
                        offset += read
                    }
                }
            } ?: return@withContext RestoreResult.Failure("Could not open the backup file")

            val encrypted = portableCrypto.isPortableEnvelope(magic)
            if (encrypted && passphrase.isNullOrBlank()) {
                return@withContext RestoreResult.Failure("This backup is encrypted — enter your recovery passphrase")
            }

            resolver.openInputStream(backupUri)?.use { input ->
                staging.outputStream().use { output ->
                    if (encrypted) {
                        portableCrypto.decrypt(input, output, passphrase!!.toCharArray())
                    } else {
                        input.copyTo(output)
                    }
                }
            } ?: return@withContext RestoreResult.Failure("Could not open the backup file")

            if (!looksLikeSqlite(staging)) {
                return@withContext RestoreResult.Failure(
                    "Backup did not decrypt to a valid database — check the recovery passphrase"
                )
            }

            // Only now is it safe to touch the live database.
            checkpointWal()
            database.close()

            val target = databaseFile()
            staging.copyTo(target, overwrite = true)
            // The old -wal/-shm describe the *previous* database and would corrupt the restored one.
            File("${target.path}-wal").delete()
            File("${target.path}-shm").delete()

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
