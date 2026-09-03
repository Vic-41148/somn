package dev.vic41148.somn.core.data.update

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.vic41148.somn.core.data.haptic.AndroidHapticsManager
import dev.vic41148.somn.core.data.repository.SomnPreferencesRepository
import dev.vic41148.somn.core.domain.model.StagedRelease
import dev.vic41148.somn.core.domain.model.VersionCompare
import kotlinx.coroutines.flow.first

/**
 * Background update check. Reads the current version from the package manager (so it never depends
 * on app-module BuildConfig), queries the releases API, and persists the result as a staged release
 * for the banner/Updates screen. A manual "Check now" passes [INPUT_FORCE] to bypass the interval
 * throttle and the master switch.
 */
@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val updateRepository: UpdateRepository,
    private val preferencesRepository: SomnPreferencesRepository,
    private val hapticsManager: AndroidHapticsManager
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val force = inputData.getBoolean(INPUT_FORCE, false)
        if (!force && !preferencesRepository.updateAutoCheck.first()) {
            return Result.success()
        }

        val lastChecked = preferencesRepository.updateLastCheckedMs.first()
        val intervalDays = preferencesRepository.updateCheckIntervalDays.first().coerceIn(1, 7)
        val withinInterval = System.currentTimeMillis() - lastChecked < intervalDays * DAY_MS
        if (!force && lastChecked > 0L && withinInterval) {
            return Result.success()
        }

        val currentVersion = currentVersionName()
        val skipped = preferencesRepository.updateSkippedVersion.first()

        return try {
            when (val result = updateRepository.checkForUpdate()) {
                is UpdateRepository.UpdateCheckResult.Available -> {
                    val release = result.release
                    val isNewer = VersionCompare.isNewer(release.versionName, currentVersion)
                    val isSkipped = release.tag == skipped
                    if (isNewer && !isSkipped) {
                        preferencesRepository.updateUpdateStagedRelease(
                            StagedRelease(
                                tag = release.tag,
                                versionName = release.versionName,
                                notes = release.notes,
                                apkUrl = release.apkUrl,
                                sha256 = release.checksumSha256,
                                atMs = System.currentTimeMillis()
                            )
                        )
                        Log.d(TAG, "Update available: ${release.tag}")
                        hapticsManager.backgroundComplete()
                    } else {
                        preferencesRepository.updateUpdateStagedRelease(null)
                    }
                }
                is UpdateRepository.UpdateCheckResult.NoUpdate -> {
                    preferencesRepository.updateUpdateStagedRelease(null)
                }
            }
            preferencesRepository.updateUpdateLastCheckedMs(System.currentTimeMillis())
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed", e)
            Result.failure()
        }
    }

    private fun currentVersionName(): String {
        return try {
            applicationContext.packageManager
                .getPackageInfo(applicationContext.packageName, 0)
                .versionName
                ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    companion object {
        const val TAG = "UpdateCheckWorker"
        const val WORK_NAME = "somn_update_check"
        const val MANUAL_WORK_NAME = "somn_update_check_now"
        const val INPUT_FORCE = "force"
        val DAY_MS = 86_400_000L
    }
}