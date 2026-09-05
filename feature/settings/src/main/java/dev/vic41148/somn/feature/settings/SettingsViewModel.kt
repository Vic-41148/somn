package dev.vic41148.somn.feature.settings

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vic41148.somn.core.data.repository.HealthConnectRepository
import dev.vic41148.somn.core.data.repository.SleepRepository
import dev.vic41148.somn.core.domain.model.HealthConnectStatus
import dev.vic41148.somn.core.domain.model.HemisphereOverride
import dev.vic41148.somn.core.domain.model.TrackingMode
import dev.vic41148.somn.core.domain.usecase.CalculateSleepScoreUseCase
import dev.vic41148.somn.core.domain.usecase.ExportCsvUseCase
import dev.vic41148.somn.core.domain.usecase.ExportJsonUseCase
import dev.vic41148.somn.core.domain.usecase.ImportSleepAsAndroidUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

import dev.vic41148.somn.core.data.repository.BackupRepository
import dev.vic41148.somn.core.data.backup.NasClient
import dev.vic41148.somn.core.data.backup.NasSyncWorker
import dev.vic41148.somn.core.data.backup.PortableCrypto
import dev.vic41148.somn.core.domain.model.NasConfig
import dev.vic41148.somn.core.domain.model.NasProtocol
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sleepRepository: SleepRepository,
    private val habitLogRepository: dev.vic41148.somn.core.data.repository.HabitLogRepository,
    private val preferencesRepository: dev.vic41148.somn.core.data.repository.SomnPreferencesRepository,
    private val backupRepository: BackupRepository,
    private val exportCsv: ExportCsvUseCase,
    private val nasClient: NasClient,
    private val healthConnectRepository: HealthConnectRepository,
    private val exportJson: ExportJsonUseCase,
    private val importSleepAsAndroid: ImportSleepAsAndroidUseCase,
    private val calculateScore: CalculateSleepScoreUseCase,
    private val portableCrypto: PortableCrypto,
    private val userProfileRepository: dev.vic41148.somn.core.data.repository.UserProfileRepository,
    private val yamnetModelRepository: dev.vic41148.somn.core.data.model.YamnetModelRepository
) : ViewModel() {

    /**
     * A freshly generated recovery key, held only until the user dismisses it. It is never read back
     * out of storage for display — this is the one and only time they can write it down.
     */
    private val _newRecoveryKey = MutableStateFlow<String?>(null)
    val newRecoveryKey: StateFlow<String?> = _newRecoveryKey.asStateFlow()

    /** Set once a restore has replaced the database and the process needs restarting. */
    private val _restartRequired = MutableStateFlow(false)
    val restartRequired: StateFlow<Boolean> = _restartRequired.asStateFlow()

    init {
        // Target Sleep Hours used to be purely local ViewModel state: the slider updated
        // _settings.value but never touched the stored UserProfile, so it always displayed the
        // hardcoded 8.0f default regardless of the user's actual saved target, and any change
        // the user made was silently discarded — score calculation, oversleep detection, and
        // sleep debt targets all read profile.targetSleepHours directly and never saw the edit.
        collectInto(userProfileRepository.observeProfile()) { state, profile ->
            state.copy(targetSleepHours = profile?.targetSleepHours ?: 8.0f)
        }
        collectInto(preferencesRepository.backupPassphraseSet) { state, isSet ->
            state.copy(backupPassphraseSet = isSet)
        }
        collectInto(preferencesRepository.healthConnectEnabled) { state, enabled ->
            state.copy(healthConnectEnabled = enabled)
        }
        collectInto(preferencesRepository.yamnetClassificationEnabled) { state, enabled ->
            state.copy(yamnetClassificationEnabled = enabled)
        }
        collectInto(preferencesRepository.hapticsEnabled) { state, enabled ->
            state.copy(hapticsEnabled = enabled)
        }
        collectInto(preferencesRepository.hapticsIntensity) { state, intensity ->
            state.copy(hapticsIntensity = intensity)
        }
        collectInto(sleepRepository.observeUnsyncedToHealthConnectCount()) { state, count ->
            state.copy(healthConnectUnsyncedCount = count)
        }
        collectInto(preferencesRepository.selectedCaptchaTaskId) { state, taskId ->
            state.copy(selectedCaptchaTaskId = taskId)
        }
        collectInto(preferencesRepository.qrCodeValue) { state, value ->
            state.copy(qrCodeValue = value)
        }
        collectInto(preferencesRepository.backupUri) { state, uri ->
            state.copy(backupUri = uri)
        }
        collectInto(preferencesRepository.trackingMode) { state, mode ->
            state.copy(trackingMode = mode)
        }
        collectInto(preferencesRepository.nasEnabled) { state, enabled ->
            state.copy(nasEnabled = enabled)
        }
        collectInto(preferencesRepository.nasHost) { state, host ->
            state.copy(nasHost = host)
        }
        collectInto(preferencesRepository.nasPath) { state, path ->
            state.copy(nasPath = path)
        }
        collectInto(preferencesRepository.nasUsername) { state, user ->
            state.copy(nasUsername = user)
        }
        // REL-05: WebDAV is the only implemented transport. Installs that stored "SMB" or
        // "NFS" before the picker was gated are coerced back to it rather than left
        // pointing at a protocol NasProtocol no longer even defines.
        collectInto(preferencesRepository.nasProtocol) { state, proto ->
            state.copy(nasProtocol = if (proto == "WEBDAV") proto else "WEBDAV")
        }
        collectInto(preferencesRepository.nasUseHttps) { state, useHttps ->
            state.copy(nasUseHttps = useHttps)
        }
        collectInto(preferencesRepository.nasPort) { state, port ->
            state.copy(nasPort = port)
        }
        collectInto(preferencesRepository.oversleepThresholdMinutes) { state, minutes ->
            state.copy(oversleepThresholdMinutes = minutes)
        }
        collectInto(preferencesRepository.wakeVerificationEnabled) { state, enabled ->
            state.copy(wakeVerificationEnabled = enabled)
        }
        collectInto(preferencesRepository.wakeVerificationWindowSeconds) { state, seconds ->
            state.copy(wakeVerificationWindowSeconds = seconds)
        }
        collectInto(preferencesRepository.useDynamicColor) { state, enabled ->
            state.copy(useDynamicColor = enabled)
        }
        collectInto(preferencesRepository.showReadinessCard) { state, enabled ->
            state.copy(showReadinessCard = enabled)
        }
        collectInto(preferencesRepository.restModeSince) { state, since ->
            state.copy(restModeSince = since)
        }
        collectInto(preferencesRepository.hemisphereOverride) { state, override ->
            state.copy(hemisphereOverride = override)
        }
        collectInto(preferencesRepository.snoreNudgeEnabled) { state, enabled ->
            state.copy(snoreNudgeEnabled = enabled)
        }
        collectInto(preferencesRepository.clipRetentionDays) { state, days ->
            state.copy(clipRetentionDays = days)
        }
    }

    /**
     * Subscribes a DataStore/Room-backed flow and folds each emission into [SettingsState],
     * logging and swallowing any stream failure (corrupted DataStore file, unexpected Room error)
     * so it can never crash the app the moment Settings opens. Mirrors the exception-proofing of
     * [refreshHealthConnectStatus]: a dead flow leaves the last known value in place rather than
     * killing the process. Every init-block subscription funnels through this.
     */
    private fun <T> collectInto(
        flow: Flow<T>,
        onEmit: (SettingsState, T) -> SettingsState
    ) {
        viewModelScope.launch {
            guardedCollect(
                flow,
                onEmit = { value -> _settings.value = onEmit(_settings.value, value) },
                onFailure = { e -> android.util.Log.e("SettingsViewModel", "Settings flow failed", e) }
            )
        }
    }

    // Settings state
    data class SettingsState(
        val targetSleepHours: Float = 8.0f,
        val sensorMode: String = "Accelerometer",
        val dndEnabled: Boolean = true,
        val batteryThreshold: Int = 15,
        val oversleepThresholdMinutes: Int = 60,
        val wakeVerificationEnabled: Boolean = true,
        val wakeVerificationWindowSeconds: Int = 15,
        /** THEME-01: whether Material You tints the app from the wallpaper on Android 12+. */
        val useDynamicColor: Boolean = true,
        /** R1: whether the Morning Ready verdict + Today outlook cards show on Home. */
        val showReadinessCard: Boolean = true,
        /** R2: Rest Mode start timestamp, null when off. */
        val restModeSince: Long? = null,
        /** Which hemisphere seasonal analysis assumes; AUTO keeps the timezone heuristic. */
        val hemisphereOverride: HemisphereOverride = HemisphereOverride.AUTO,
        val snoreNudgeEnabled: Boolean = true,
        /** Days sleep-talk recordings are kept; 0 means keep forever. */
        val clipRetentionDays: Int =
            dev.vic41148.somn.core.data.repository.SomnPreferencesRepository.DEFAULT_CLIP_RETENTION_DAYS,
        val darkMode: String = "System",
        val trackingMode: TrackingMode = TrackingMode.ACCELEROMETER,
        val selectedCaptchaTaskId: String = "math",
        val qrCodeValue: String? = null,
        val backupUri: String? = null,
        /** Transient error shown when the user picks a backup directory that only grants temporary access. */
        val backupDirectoryError: String? = null,
        /**
         * Whether a recovery passphrase exists. Without one, backups can only be written in the
         * clear locally and off-device sync is skipped entirely — an upload encrypted with the
         * device-bound Keystore key would be unreadable exactly when it is needed.
         */
        val backupPassphraseSet: Boolean = false,
        // NAS
        val nasEnabled: Boolean = false,
        val nasHost: String = "",
        val nasPath: String = "/somn",
        val nasUsername: String = "",
        val nasProtocol: String = "WEBDAV",
        val nasPort: Int = 443,
        /** Explicit TLS choice for NAS uploads — on unless the user deliberately turns it off. */
        val nasUseHttps: Boolean = true,
        val nasTestResult: String? = null,
        // Health Connect
        val healthConnectEnabled: Boolean = false,
        val healthConnectStatus: HealthConnectStatus = HealthConnectStatus.UNAVAILABLE,
        /** HEALTH-04: completed sessions never written to Health Connect — unsynced or silently dedup-skipped. Only meaningful once healthConnectEnabled is true. */
        val healthConnectUnsyncedCount: Int = 0,
        /** Task 14 (AUDIO-01) — off by default. Experimental YAMNet audio classification, gated so it can be A/B'd against the existing ZCR heuristic. Not accuracy-validated (AUDIO-02) or battery-soak-tested (AUDIO-03). */
        val yamnetClassificationEnabled: Boolean = false,
        /** App-wide haptics master switch + intensity, surfaced into state from DataStore. */
        val hapticsEnabled: Boolean = true,
        val hapticsIntensity: dev.vic41148.somn.core.domain.haptic.HapticsIntensity =
            dev.vic41148.somn.core.domain.haptic.HapticsIntensity.STANDARD
    )

    private val _settings = MutableStateFlow(SettingsState())
    val settings: StateFlow<SettingsState> = _settings.asStateFlow()

    /** R5: profile for gating the menopause check-in entry (peri/meno stages only). */
    val userProfile = userProfileRepository.observeProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** R5: completed menopause check-in answers, null until first done. */
    val menoAnswers = preferencesRepository.menoAnswers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun saveMenoAnswers(answers: List<Int>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            preferencesRepository.saveMenoAnswers(answers)
        }
    }

    private val _exportStatus = MutableStateFlow<String?>(null)
    val exportStatus: StateFlow<String?> = _exportStatus.asStateFlow()

    private val _clipDeletionStatus = MutableStateFlow<String?>(null)
    val clipDeletionStatus: StateFlow<String?> = _clipDeletionStatus.asStateFlow()

    // ---- YAMNet model download state (AUDIO-01) ----

    sealed interface YamnetModelState {
        data object Idle : YamnetModelState
        data object ConfirmingDownload : YamnetModelState
        data class Downloading(val progress: Float?) : YamnetModelState
        data class Error(val message: String) : YamnetModelState
        data object Ready : YamnetModelState
    }

    private val _yamnetModelState = MutableStateFlow<YamnetModelState>(YamnetModelState.Idle)
    val yamnetModelState: StateFlow<YamnetModelState> = _yamnetModelState.asStateFlow()

    /**
     * Consent-gated YAMNet toggle: enabling when the model is already on disk is immediate;
     * otherwise it raises the download-consent dialog. Disabling always just flips the flag.
     */
    fun onYamnetToggle(enabled: Boolean) {
        if (!enabled) {
            viewModelScope.launch { preferencesRepository.updateYamnetClassificationEnabled(false) }
            _yamnetModelState.value = YamnetModelState.Idle
            return
        }
        viewModelScope.launch {
            preferencesRepository.updateYamnetClassificationEnabled(true)
            _yamnetModelState.value = if (yamnetModelRepository.isDownloaded()) {
                YamnetModelState.Ready
            } else {
                YamnetModelState.ConfirmingDownload
            }
        }
    }

    fun dismissYamnetModelDialog() {
        _yamnetModelState.value = if (yamnetModelRepository.isDownloaded()) {
            YamnetModelState.Ready
        } else {
            YamnetModelState.Idle
        }
        viewModelScope.launch { preferencesRepository.updateYamnetClassificationEnabled(false) }
    }

    fun confirmYamnetDownload() {
        _yamnetModelState.value = YamnetModelState.Downloading(progress = null)
        viewModelScope.launch {
            try {
                yamnetModelRepository.download { downloaded, total ->
                    _yamnetModelState.value = YamnetModelState.Downloading(
                        progress = if (total > 0) downloaded.toFloat() / total.toFloat() else null
                    )
                }
                _yamnetModelState.value = YamnetModelState.Ready
                preferencesRepository.updateYamnetClassificationEnabled(true)
            } catch (e: Exception) {
                _yamnetModelState.value = YamnetModelState.Error(e.message ?: "Unknown error")
            }
        }
    }

    // ---- Haptics (app-wide master switch + intensity) ----

    fun updateHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateHapticsEnabled(enabled) }
    }

    fun updateHapticsIntensity(intensity: dev.vic41148.somn.core.domain.haptic.HapticsIntensity) {
        viewModelScope.launch { preferencesRepository.updateHapticsIntensity(intensity) }
    }

    init {
        // Must run after _settings above is initialized: unlike the DataStore .collect{}
        // launches in the first init block (which always suspend on their first emission
        // before touching _settings.value), getStatus() can return synchronously via its
        // !isAvailable() early-return — calling this from the top init block would touch
        // _settings before its property initializer ran, on any device without Health Connect.
        refreshHealthConnectStatus()
    }

    fun updateSleepTarget(hours: Float) {
        _settings.value = _settings.value.copy(targetSleepHours = hours)
        viewModelScope.launch {
            val profile = userProfileRepository.getProfile() ?: return@launch
            userProfileRepository.saveProfile(profile.copy(targetSleepHours = hours))
        }
    }

    fun updateDndEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(dndEnabled = enabled)
    }

    fun updateBatteryThreshold(threshold: Int) {
        _settings.value = _settings.value.copy(batteryThreshold = threshold)
    }

    fun updateOversleepThresholdMinutes(minutes: Int) {
        viewModelScope.launch { preferencesRepository.updateOversleepThresholdMinutes(minutes) }
    }

    fun updateWakeVerificationEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateWakeVerificationEnabled(enabled) }
    }

    fun updateWakeVerificationWindowSeconds(seconds: Int) {
        viewModelScope.launch { preferencesRepository.updateWakeVerificationWindowSeconds(seconds) }
    }

    fun updateUseDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateUseDynamicColor(enabled) }
    }

    fun updateShowReadinessCard(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateShowReadinessCard(enabled) }
    }

    /** R2: entering Rest Mode stamps now; leaving clears the boundary. */
    fun setRestMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setRestModeSince(
                if (enabled) System.currentTimeMillis() else null
            )
        }
    }

    fun updateHemisphereOverride(override: HemisphereOverride) {
        viewModelScope.launch { preferencesRepository.updateHemisphereOverride(override) }
    }

    fun updateSnoreNudgeEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateSnoreNudgeEnabled(enabled) }
    }

    fun updateClipRetentionDays(days: Int) {
        viewModelScope.launch { preferencesRepository.updateClipRetentionDays(days) }
    }

    /** Immediately destroys every sleep-talk recording on disk, without waiting for retention. */
    fun deleteAllAudioClips() {
        viewModelScope.launch {
            _clipDeletionStatus.value = try {
                val deleted = sleepRepository.deleteAllAudioClips()
                "Deleted $deleted recording${if (deleted == 1) "" else "s"}"
            } catch (e: Exception) {
                "Failed to delete recordings: ${e.message}"
            }
        }
    }

    fun clearClipDeletionStatus() {
        _clipDeletionStatus.value = null
    }

    /** R2 per-category purge: forgets every habit log. Standalone table, no cascades. */
    fun purgeHabitLogs() {
        viewModelScope.launch {
            _clipDeletionStatus.value = try {
                habitLogRepository.clearAll()
                "Cleared all habit logs"
            } catch (e: Exception) {
                "Failed to clear habit logs: ${e.message}"
            }
        }
    }

    /**
     * R2 per-category purge: deletes completed sessions older than 90 days through the
     * same [SleepRepository.deleteSession] path as single deletes, so clips, audio rows,
     * epochs, vitals and tags all follow their normal cleanup — no orphans.
     */
    fun purgeOldSessions() {
        viewModelScope.launch {
            _clipDeletionStatus.value = try {
                val cutoff = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
                val deleted = sleepRepository.deleteSessionsOlderThan(cutoff)
                "Deleted $deleted session${if (deleted == 1) "" else "s"} older than 90 days"
            } catch (e: Exception) {
                "Failed to delete old sessions: ${e.message}"
            }
        }
    }

    fun updateTrackingMode(mode: TrackingMode) {
        viewModelScope.launch {
            preferencesRepository.updateTrackingMode(mode)
        }
    }

    fun updateCaptchaTask(taskId: String) {
        viewModelScope.launch {
            preferencesRepository.updateSelectedCaptchaTask(taskId)
        }
    }

    fun updateQRCodeValue(value: String) {
        viewModelScope.launch {
            preferencesRepository.updateQrCodeValue(value)
        }
    }

    fun updateBackupUri(uri: String) {
        viewModelScope.launch {
            preferencesRepository.updateBackupUri(uri)
        }
    }

    fun updateBackupUriError(message: String) {
        _settings.value = _settings.value.copy(backupDirectoryError = message)
    }

    /**
     * Generates a recovery key, stores it, and surfaces it once for the user to record. Replacing an
     * existing key leaves older backups readable only by the old key, so the UI must confirm first.
     */
    fun generateRecoveryKey() {
        viewModelScope.launch {
            val key = portableCrypto.generateRecoveryKey()
            preferencesRepository.updateBackupPassphrase(key)
            _newRecoveryKey.value = key
        }
    }

    /** Lets the user supply their own passphrase instead of a generated key. */
    fun setRecoveryPassphrase(passphrase: String) {
        viewModelScope.launch {
            if (passphrase.isBlank()) {
                _exportStatus.value = "Recovery passphrase cannot be empty"
                return@launch
            }
            preferencesRepository.updateBackupPassphrase(passphrase)
            _exportStatus.value = "Recovery passphrase saved"
        }
    }

    fun dismissRecoveryKey() {
        _newRecoveryKey.value = null
    }

    /**
     * Restores the database from [uri]. [passphrase] is required for encrypted backups; leave null
     * for a plaintext one. On success the caller must restart the app — Room still holds the old file.
     */
    fun restoreDatabase(uri: android.net.Uri, passphrase: String?) {
        viewModelScope.launch {
            _exportStatus.value = "Restoring..."
            when (val result = backupRepository.restoreDatabase(uri, passphrase)) {
                is BackupRepository.RestoreResult.SuccessRestartRequired -> {
                    _exportStatus.value = "Restore complete — restart Somn to load it"
                    _restartRequired.value = true
                }
                is BackupRepository.RestoreResult.Failure ->
                    _exportStatus.value = "Restore failed: ${result.message}"
            }
        }
    }

    fun performManualBackup() {
        viewModelScope.launch {
            _exportStatus.value = "Backing up..."
            try {
                backupRepository.performSilentBackup()
                _exportStatus.value = "Backup successful!"
            } catch (e: Exception) {
                _exportStatus.value = "Backup failed: ${e.message}"
            }
        }
    }

    fun exportData(context: Context) {
        viewModelScope.launch {
            try {
                val sessions = sleepRepository.getRecentSessions(1000)
                val csv = exportCsv(sessions)

                val file = File(context.cacheDir, "sleep_data_export.csv")
                file.writeText(csv)

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Export Sleep Data"))

                _exportStatus.value = "Export ready!"
            } catch (e: Exception) {
                _exportStatus.value = "Export failed: ${e.message}"
            }
        }
    }

    // ── Data Portability (DATA-01/02) ───────────────────────────────

    private val _importStatus = MutableStateFlow<String?>(null)
    val importStatus: StateFlow<String?> = _importStatus.asStateFlow()

    /** DATA-01: full-fidelity JSON alongside the existing flat CSV, bundled as one ZIP to share. */
    fun exportAllDataZip(context: Context) {
        viewModelScope.launch {
            try {
                val sessions = sleepRepository.getRecentSessions(1000)
                val csv = exportCsv(sessions)
                val json = exportJson(sessions)

                val file = File(context.cacheDir, "somn_export.zip")
                java.util.zip.ZipOutputStream(file.outputStream()).use { zip ->
                    zip.putNextEntry(java.util.zip.ZipEntry("sleep_data_export.csv"))
                    zip.write(csv.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()

                    zip.putNextEntry(java.util.zip.ZipEntry("sleep_data_export.json"))
                    zip.write(json.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Export All Sleep Data"))

                _exportStatus.value = "Export ready!"
            } catch (e: Exception) {
                _exportStatus.value = "Export failed: ${e.message}"
            }
        }
    }

    /**
     * DATA-02: reads the picked Sleep as Android `sleep-export.csv`, parses it, and persists
     * every row the parser could confidently map as its own completed session. Best-effort and
     * lossy by design (see [ImportSleepAsAndroidUseCase] doc) — the result summary always
     * reports what was skipped rather than silently dropping rows.
     */
    fun importSleepAsAndroidFile(context: Context, uri: android.net.Uri) {
        viewModelScope.launch {
            _importStatus.value = "Importing..."
            try {
                val csv = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: run {
                        _importStatus.value = "Import failed: couldn't read the selected file."
                        return@launch
                    }

                val result = importSleepAsAndroid(csv)

                for (session in result.sessions) {
                    val newId = sleepRepository.createSession(
                        session.startTimeMillis,
                        session.timezoneId,
                        session.sessionType
                    )
                    val scored = session.copy(
                        id = newId,
                        sleepScore = calculateScore(session).totalScore
                    )
                    sleepRepository.completeSession(scored)
                }

                _importStatus.value = buildString {
                    append("Imported ${result.importedCount} session(s).")
                    if (result.skippedRowCount > 0) {
                        append(" ${result.skippedRowCount} row(s) skipped — see below.")
                    }
                }
            } catch (e: Exception) {
                _importStatus.value = "Import failed: ${e.message}"
            }
        }
    }

    fun clearImportStatus() {
        _importStatus.value = null
    }

    // ── NAS ──────────────────────────────────────────────────────────

    fun updateNasEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateNasEnabled(enabled) }
    }

    fun updateNasHost(host: String) {
        viewModelScope.launch { preferencesRepository.updateNasHost(host) }
    }

    fun updateNasPath(path: String) {
        viewModelScope.launch { preferencesRepository.updateNasPath(path) }
    }

    fun updateNasUsername(username: String) {
        viewModelScope.launch { preferencesRepository.updateNasUsername(username) }
    }

    /** REL-06: password is write-only here — never round-tripped back into [settings] state. */
    fun updateNasPassword(password: String) {
        viewModelScope.launch { preferencesRepository.updateNasPassword(password) }
    }

    fun updateNasProtocol(protocol: String) {
        viewModelScope.launch { preferencesRepository.updateNasProtocol(protocol) }
    }

    fun updateNasPort(port: Int) {
        viewModelScope.launch { preferencesRepository.updateNasPort(port) }
    }

    fun updateNasUseHttps(useHttps: Boolean) {
        viewModelScope.launch { preferencesRepository.updateNasUseHttps(useHttps) }
    }

    fun testNasConnection() {
        viewModelScope.launch {
            _settings.value = _settings.value.copy(nasTestResult = "Testing...")
            val s = _settings.value
            val config = NasConfig(
                host = s.nasHost,
                path = s.nasPath,
                username = s.nasUsername,
                protocol = try { NasProtocol.valueOf(s.nasProtocol) } catch (_: Exception) { NasProtocol.WEBDAV },
                port = s.nasPort,
                isEnabled = true,
                useHttps = s.nasUseHttps
            )
            val ok = nasClient.testConnection(config)
            // A failed plain-HTTP attempt is almost always Android's cleartext block rather than a
            // genuinely unreachable server, so say that instead of a bare "Connection failed".
            val failureMessage = if (s.nasUseHttps) {
                "Connection failed"
            } else {
                "Connection failed — Android blocks unencrypted HTTP. Turn HTTPS on."
            }
            _settings.value = _settings.value.copy(
                nasTestResult = if (ok) "Connected" else failureMessage
            )
        }
    }

    fun triggerNasSync(context: Context) {
        val request = OneTimeWorkRequestBuilder<NasSyncWorker>()
            .addTag(NasSyncWorker.TAG)
            .build()
        WorkManager.getInstance(context).enqueue(request)
        _exportStatus.value = "NAS sync queued"
    }

    // ── Health Connect (HEALTH-01..04) ──────────────────────────────

    /** Permission set + launcher contract for the settings screen's `rememberLauncherForActivityResult`. */
    val healthConnectRequiredPermissions get() = healthConnectRepository.requiredPermissions
    fun healthConnectPermissionsContract() = healthConnectRepository.permissionsContract()

    fun updateHealthConnectEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateHealthConnectEnabled(enabled) }
    }

    // ── YAMNet audio classification (Task 14, AUDIO-01) ─────────────

    fun updateYamnetClassificationEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateYamnetClassificationEnabled(enabled) }
    }

    /**
     * HEALTH-03: called on screen resume and right after the permission sheet returns — never cached.
     *
     * Deliberately exception-proof: this runs on Dispatchers.Main.immediate during ViewModel
     * construction (the init block), so an unexpected platform error from the Health Connect SDK
     * (service unresponsive, provider mid-update, etc.) must degrade to UNAVAILABLE instead of
     * escaping the coroutine and crashing the whole app the moment Settings opens.
     */
    fun refreshHealthConnectStatus() {
        viewModelScope.launch {
            val status = try {
                healthConnectRepository.getStatus()
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to query Health Connect status", e)
                HealthConnectStatus.UNAVAILABLE
            }
            _settings.value = _settings.value.copy(healthConnectStatus = status)
        }
    }
}

/**
 * Collects [flow], delivering every value to [onEmit], and swallows any stream failure via
 * [onFailure] instead of letting it escape — a corrupted DataStore file or unexpected Room error
 * must degrade to "keep the last known value" rather than crash the app the moment Settings
 * opens. Cancellation is always rethrown (never reported as a failure): viewModelScope
 * cancellation on ViewModel clear is normal teardown.
 *
 * Extracted out of SettingsViewModel.collectInto as a pure suspend function so the guard
 * semantics are unit-testable without constructing the ViewModel's dependency graph.
 */
internal suspend fun <T> guardedCollect(
    flow: Flow<T>,
    onEmit: (T) -> Unit,
    onFailure: (Exception) -> Unit
) {
    try {
        flow.collect(onEmit)
    } catch (e: CancellationException) {
        // viewModelScope cancellation on ViewModel clear — propagate, never log as a failure.
        throw e
    } catch (e: Exception) {
        onFailure(e)
    }
}
