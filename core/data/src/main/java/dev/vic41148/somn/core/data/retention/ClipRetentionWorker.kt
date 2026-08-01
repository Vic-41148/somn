package dev.vic41148.somn.core.data.retention

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.vic41148.somn.core.data.database.dao.AudioEventDao
import dev.vic41148.somn.core.data.repository.SomnPreferencesRepository
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Deletes sleep-talk WAV clips older than the user's retention window.
 *
 * Before this worker existed, clips were only ever removed after a successful NAS upload or when
 * the whole session was deleted. NAS sync is off by default, so for the default user every
 * recording of them talking in their sleep accumulated in filesDir indefinitely.
 *
 * The DB row survives — the audio event itself is still part of the night's history. Only the
 * recording is destroyed, and the row's clipPath is nulled so playback UI stops offering it.
 */
@HiltWorker
class ClipRetentionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val audioEventDao: AudioEventDao,
    private val preferencesRepository: SomnPreferencesRepository
) : CoroutineWorker(appContext, params) {

    companion object {
        const val TAG = "ClipRetentionWorker"
        const val WORK_NAME = "clip_retention_prune"
        const val INTERVAL_HOURS = 12L
    }

    override suspend fun doWork(): Result {
        val retentionDays = preferencesRepository.clipRetentionDays.first()
        if (retentionDays <= SomnPreferencesRepository.CLIP_RETENTION_KEEP_FOREVER) {
            Log.d(TAG, "Retention disabled by user — keeping all clips")
            return Result.success()
        }

        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
        val expired = audioEventDao.getEventsWithClipsOlderThan(cutoff)
        if (expired.isEmpty()) return Result.success()

        var deleted = 0
        for (event in expired) {
            val path = event.clipPath ?: continue
            // Clear the path even when the file is already gone, so a missing file can't leave a
            // dangling clipPath that playback UI keeps trying to open.
            runCatching { File(path).delete() }
                .onFailure { Log.e(TAG, "Failed to delete clip: $path", it) }
                .onSuccess { if (it) deleted++ }
            audioEventDao.clearClipPath(event.id)
        }

        Log.d(TAG, "Pruned $deleted of ${expired.size} expired clips (retention=${retentionDays}d)")
        return Result.success()
    }
}
