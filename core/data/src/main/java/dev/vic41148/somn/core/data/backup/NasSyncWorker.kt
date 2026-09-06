package dev.vic41148.somn.core.data.backup

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.vic41148.somn.core.data.database.dao.AudioEventDao
import dev.vic41148.somn.core.data.repository.BackupRepository
import dev.vic41148.somn.core.data.repository.SomnPreferencesRepository
import dev.vic41148.somn.core.domain.model.NasConfig
import dev.vic41148.somn.core.domain.model.NasProtocol
import kotlinx.coroutines.flow.first
import java.io.ByteArrayInputStream
import java.io.File

/**
 * WorkManager worker that:
 *  1. Runs local SAF backup (DB + prefs)
 *  2. Uploads un-synced audio clips to NAS (encrypted)
 *  3. Prunes local clips after successful upload
 */
@HiltWorker
class NasSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val audioEventDao: AudioEventDao,
    private val nasClient: NasClient,
    private val portableCrypto: PortableCrypto,
    private val backupRepository: BackupRepository,
    private val preferencesRepository: SomnPreferencesRepository,
    private val audioClipStore: dev.vic41148.somn.core.data.audio.AudioClipStore
) : CoroutineWorker(appContext, params) {

    companion object {
        const val TAG = "NasSyncWorker"
        const val WORK_NAME = "nas_sync"
        private const val CLIP_AGE_HOURS = 24L
        private const val REMOTE_DB_PATH = "db/sleep_tracker.db.enc"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "NAS sync started")

        // 1. Always run local backup first
        try {
            backupRepository.performSilentBackup()
            Log.d(TAG, "Local backup complete")
        } catch (e: Exception) {
            Log.e(TAG, "Local backup failed (${e.javaClass.simpleName})")
        }

        // 2. Check if NAS enabled
        val nasEnabled = preferencesRepository.nasEnabled.first()
        if (!nasEnabled) {
            Log.d(TAG, "NAS disabled, skipping remote sync")
            return Result.success()
        }

        val config = loadNasConfig() ?: run {
            Log.w(TAG, "NAS config incomplete, skipping")
            return Result.success()
        }

        // Everything leaving the device is encrypted with the user's recovery passphrase, never with
        // the Keystore key — a Keystore-encrypted upload is unreadable the moment the phone is gone,
        // which defeats the point of having an off-device copy.
        val passphrase = preferencesRepository.getBackupPassphrase()
        if (passphrase == null) {
            Log.w(TAG, "No backup passphrase set — skipping NAS sync (uploads would be unrecoverable)")
            return Result.success()
        }
        // Derived once per run: the KDF is deliberately expensive, and a night can produce dozens of
        // clips. Each file still gets its own random data key wrapped under this one.
        val kek = portableCrypto.deriveKek(passphrase.toCharArray())

        // 3. Sync un-synced audio clips
        val cutoff = System.currentTimeMillis() - (CLIP_AGE_HOURS * 3600 * 1000)
        val unsyncedEvents = audioEventDao.getUnsyncedAudioEventsOlderThan(cutoff)
        Log.d(TAG, "Found ${unsyncedEvents.size} un-synced clips")

        var successCount = 0
        for (event in unsyncedEvents) {
            val clipPath = event.clipPath ?: continue
            val clipFile = File(clipPath)
            if (!clipFile.exists()) {
                // File gone, mark synced anyway to avoid retry loops
                audioEventDao.markSynced(event.id)
                continue
            }

            try {
                // Encrypt clip (sealed at-rest clips are decrypted first)
                val plainBytes = audioClipStore.readClipBytes(clipPath)
                val encrypted = portableCrypto.encrypt(plainBytes, kek)

                // Upload
                val remoteName = "clips/${event.sessionId}/${clipFile.name}.enc"
                val uploaded = nasClient.upload(
                    config, remoteName,
                    ByteArrayInputStream(encrypted),
                    encrypted.size.toLong()
                )

                if (uploaded) {
                    audioEventDao.markSynced(event.id)
                    // Prune local clip after successful upload
                    clipFile.delete()
                    audioEventDao.clearClipPath(event.id)
                    successCount++
                    Log.d(TAG, "Synced + pruned: ${clipFile.name}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync clip (${e.javaClass.simpleName})")
            }
        }

        // 4. Upload encrypted DB snapshot
        val staging = File(applicationContext.cacheDir, "nas-db-snapshot.enc")
        try {
            // Fold -wal into the main file, or the snapshot silently omits the most recent night.
            backupRepository.checkpointWal()

            val dbFile = backupRepository.databaseFile()
            if (dbFile.exists()) {
                // Encrypt to disk rather than memory: the upload API needs the ciphertext length up
                // front, and a multi-year database is not something to hold twice on the heap.
                dbFile.inputStream().use { input ->
                    staging.outputStream().use { output ->
                        portableCrypto.encrypt(input, output, kek)
                    }
                }
                val uploaded = staging.inputStream().use { encryptedStream ->
                    nasClient.upload(config, REMOTE_DB_PATH, encryptedStream, staging.length())
                }
                Log.d(TAG, if (uploaded) "DB snapshot uploaded" else "DB snapshot upload rejected")
            } else {
                Log.w(TAG, "Database missing at ${dbFile.path} — no snapshot uploaded")
            }
        } catch (e: Exception) {
            Log.e(TAG, "DB upload failed (${e.javaClass.simpleName})")
        } finally {
            staging.delete()
        }

        Log.d(TAG, "NAS sync complete. Synced $successCount clips.")
        return Result.success()
    }

    private suspend fun loadNasConfig(): NasConfig? {
        val host = preferencesRepository.nasHost.first()
        if (host.isBlank()) return null
        return NasConfig(
            host = host,
            path = preferencesRepository.nasPath.first(),
            username = preferencesRepository.nasUsername.first(),
            protocol = try {
                NasProtocol.valueOf(preferencesRepository.nasProtocol.first())
            } catch (e: Exception) {
                NasProtocol.WEBDAV
            },
            port = preferencesRepository.nasPort.first(),
            isEnabled = true,
            useHttps = preferencesRepository.nasUseHttps.first()
        )
    }
}
