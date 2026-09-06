package dev.vic41148.somn.core.data.backup

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.vic41148.somn.core.data.repository.BackupRepository

/**
 * Flavor-agnostic periodic local backup. The mandatory pre-update backup used to live only inside
 * the standalone self-updater, which silently stripped the safety net from store-channel builds.
 * This job breaks that coupling: it exists in every flavor and keeps a recent DB + preferences
 * snapshot in the user's chosen backup folder around at all times.
 *
 * Deliberately modest: it only writes when the user has granted a backup folder (backupUri), so on
 * a fresh install it no-ops instead of nagging; it touches no install logic and needs no special
 * permission. Running it daily - independent of update events - also protects against crashes or
 * corruption that have nothing to do with updates.
 */
@HiltWorker
class LocalBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val backupRepository: BackupRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            backupRepository.performSilentBackup()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Local backup failed (${e.javaClass.simpleName})")
            Result.retry()
        }
    }

    companion object {
        const val TAG = "LocalBackupWorker"
        const val WORK_NAME = "local_backup"
        const val INTERVAL_HOURS = 24L
    }
}