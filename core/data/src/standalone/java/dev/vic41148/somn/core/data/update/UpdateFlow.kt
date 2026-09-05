package dev.vic41148.somn.core.data.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.vic41148.somn.core.data.backup.CreateExportZipUseCase
import dev.vic41148.somn.core.data.repository.SomnPreferencesRepository
import dev.vic41148.somn.core.domain.model.StagedRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level orchestration for the update flow: the backup-first gate, the mandatory export backup,
 * download + checksum verification, and the hand-off to the system installer. Shared by the Home
 * banner, the Settings Updates section, and the Updates screen so the safety rules live in exactly
 * one place.
 */
@Singleton
class UpdateFlow @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updateRepository: UpdateRepository,
    private val updateBackupStore: UpdateBackupStore,
    private val createExportZipUseCase: CreateExportZipUseCase,
    private val preferencesRepository: SomnPreferencesRepository
) {

    sealed class Outcome {
        data class Installing(val apk: File) : Outcome()
        data class Failure(val message: String) : Outcome()
        data object NoApk : Outcome()
        data object NotInstalled : Outcome()
    }

    /**
     * Spec gate: if the user has neither NAS Sync nor a backup recovery passphrase, the update
     * must surface a one-time "[Set Up Backup] / [Continue Anyway]" interstitial first. Every path
     * that proceeds still takes the mandatory backup regardless - the gate only decides whether we
     * ask first, never whether we protect the data.
     */
    suspend fun backupGateRequiresInterstitial(): Boolean {
        val nasEnabled = preferencesRepository.nasEnabled.first()
        val passphraseSet = !preferencesRepository.getBackupPassphrase().isNullOrBlank()
        return !nasEnabled && !passphraseSet
    }

    /**
     * The mandatory pre-update backup: full export ZIP in app-private storage (survives an
     * in-place update). Returns the backup file name, or the failure that must abort the update.
     */
    suspend fun createBackup(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "pre_update").apply { mkdirs() }
            val target = File(dir, preUpdateBackupFileName())
            createExportZipUseCase.create(target)
            updateBackupStore.keepPreUpdateBackup(target)
            target.name
        }
    }

    /** Downloads → verifies the published checksum → hands to the system installer. No install
     *  happens on a mismatch: the file is deleted and the failure reported. */
    suspend fun downloadAndInstall(
        release: StagedRelease,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Outcome {
        val url = release.apkUrl ?: return Outcome.NoApk
        val apk = updateRepository.prepareDownloadFile()
        return try {
            updateRepository.downloadApk(url, apk, onProgress)
            updateRepository.verifyChecksum(apk, release.sha256)
            val failure = launchSystemInstaller(apk)
            if (failure == null) Outcome.Installing(apk) else Outcome.Failure(failure)
        } catch (e: UpdateException) {
            Outcome.Failure(e.message ?: "Download failed.")
        }
    }

    /** Launches the platform installer overlay for [apk]; returns the failure, or null on success. */
    fun launchSystemInstaller(apk: File): String? {
        return try {
            val authorities = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authorities, apk)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            null
        } catch (e: ActivityNotFoundException) {
            e.message ?: "No installer found on this device."
        } catch (e: Exception) {
            e.message ?: "Could not open the installer."
        }
    }

    /**
     * Downgrade path. Android refuses in-place versionCode downgrades, so the honest flow is:
     * mandatory backup first, open the browser at the older release's APK, then ask the system to
     * uninstall this build. After the fresh install the [UpdateBackupStore] copy powers the
     * "Restore from backup made on [date]?" prompt on first launch.
     */
    fun scheduleDowngrade(apkUrl: String?): Outcome {
        if (apkUrl.isNullOrBlank()) return Outcome.NoApk
        val startedBrowser = try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
        if (!startedBrowser) return Outcome.Failure("Could not open the release download page.")
        val uninstallLaunched = try {
            val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${context.packageName}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
        return if (uninstallLaunched) Outcome.NotInstalled else Outcome.Failure("Could not start uninstall.")
    }
}