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
    private val encryptionUtils: EncryptionUtils,
    private val backupRepository: BackupRepository,
    private val preferencesRepository: SomnPreferencesRepository
) : CoroutineWorker(appContext, params) {

    companion object {
        const val TAG = "NasSyncWorker"
        const val WORK_NAME = "nas_sync"
        private const val CLIP_AGE_HOURS = 24L
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "NAS sync started")

        // 1. Always run local backup first
        try {
            backupRepository.performSilentBackup()
            Log.d(TAG, "Local backup complete")
        } catch (e: Exception) {
            Log.e(TAG, "Local backup failed", e)
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
                // Encrypt clip
                val plainBytes = clipFile.readBytes()
                val encrypted = encryptionUtils.encryptBytes(plainBytes)

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
                Log.e(TAG, "Failed to sync clip: $clipPath", e)
            }
        }

        // 4. Upload encrypted DB snapshot
        try {
            val dbFile = applicationContext.getDatabasePath("somn-database")
            if (dbFile.exists()) {
                val dbBytes = dbFile.readBytes()
                val encrypted = encryptionUtils.encryptBytes(dbBytes)
                nasClient.upload(
                    config, "db/somn-database.db.enc",
                    ByteArrayInputStream(encrypted),
                    encrypted.size.toLong()
                )
                Log.d(TAG, "DB snapshot uploaded")
            }
        } catch (e: Exception) {
            Log.e(TAG, "DB upload failed", e)
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
            isEnabled = true
        )
    }
}
