package dev.vic41148.somn.feature.settings.updates

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.vic41148.somn.core.data.repository.SomnPreferencesRepository
import dev.vic41148.somn.core.data.update.UpdateFlow
import dev.vic41148.somn.core.data.update.UpdateRepository
import dev.vic41148.somn.core.data.update.UpdateScheduler
import dev.vic41148.somn.core.domain.haptic.HapticsManager
import dev.vic41148.somn.core.domain.model.ReleaseInfo
import dev.vic41148.somn.core.domain.model.StagedRelease
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdatesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: SomnPreferencesRepository,
    private val updateFlow: UpdateFlow,
    private val updateScheduler: UpdateScheduler,
    private val updateRepository: UpdateRepository,
    private val hapticsManager: HapticsManager
) : ViewModel() {

    sealed interface Phase {
        data object Idle : Phase
        data object BackingUp : Phase
        data object Downloading : Phase
        data object DowngradePreparing : Phase
        data class Error(val message: String) : Phase
    }

    private val _autoCheck = MutableStateFlow(true)
    val autoCheck: StateFlow<Boolean> = _autoCheck.asStateFlow()

    private val _intervalDays = MutableStateFlow(1)
    val intervalDays: StateFlow<Int> = _intervalDays.asStateFlow()

    private val _lastChecked = MutableStateFlow<Long?>(null)
    val lastChecked: StateFlow<Long?> = _lastChecked.asStateFlow()

    private val _staged = MutableStateFlow(StagedRelease("", "", "", null, null, 0L))
    val staged: StateFlow<StagedRelease> = _staged.asStateFlow()

    private val _phase = MutableStateFlow<Phase>(Phase.Idle)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _progress = MutableStateFlow<Pair<Long, Long>?>(null)
    val progress: StateFlow<Pair<Long, Long>?> = _progress.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _showBackupInterstitial = MutableStateFlow(false)
    val showBackupInterstitial: StateFlow<Boolean> = _showBackupInterstitial.asStateFlow()

    private val _history = MutableStateFlow<List<ReleaseInfo>>(emptyList())
    val history: StateFlow<List<ReleaseInfo>> = _history.asStateFlow()

    private val _historyLoading = MutableStateFlow(false)
    val historyLoading: StateFlow<Boolean> = _historyLoading.asStateFlow()

    private val _historyError = MutableStateFlow<String?>(null)
    val historyError: StateFlow<String?> = _historyError.asStateFlow()

    private val _downgradeCandidate = MutableStateFlow<ReleaseInfo?>(null)
    val downgradeCandidate: StateFlow<ReleaseInfo?> = _downgradeCandidate.asStateFlow()

    private var pendingRelease: StagedRelease? = null

    init {
        viewModelScope.launch {
            preferencesRepository.updateAutoCheck.collect { _autoCheck.value = it }
        }
        viewModelScope.launch {
            preferencesRepository.updateCheckIntervalDays.collect { _intervalDays.value = it }
        }
        viewModelScope.launch {
            preferencesRepository.updateLastCheckedMs.collect {
                _lastChecked.value = if (it < 0) null else it
            }
        }
        viewModelScope.launch {
            preferencesRepository.updateStagedRelease.collect { _staged.value = it }
        }
    }

    val currentVersionName: String =
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }

    fun setAutoCheck(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateUpdateAutoCheck(enabled)
            if (enabled) updateScheduler.rescheduleForInterval(_intervalDays.value)
        }
    }

    fun setIntervalDays(days: Int) {
        viewModelScope.launch {
            preferencesRepository.updateUpdateCheckIntervalDays(days)
            updateScheduler.rescheduleForInterval(days)
        }
    }

    /** An Error phase is a terminal, retryable state - it must not block the next attempt. */
    private fun canTriggerPhaseWork() = when (_phase.value) {
        is Phase.Idle, is Phase.Error -> true
        else -> false
    }

    fun checkNow() {
        if (!canTriggerPhaseWork()) return
        updateScheduler.checkNow()
    }

    fun skip(release: StagedRelease) {
        viewModelScope.launch {
            preferencesRepository.updateUpdateSkippedVersion(release.tag)
            preferencesRepository.updateUpdateStagedRelease(null)
        }
    }

    fun requestUpdate(release: StagedRelease) {
        if (!canTriggerPhaseWork()) return
        pendingRelease = release
        viewModelScope.launch {
            if (updateFlow.backupGateRequiresInterstitial()) {
                _showBackupInterstitial.value = true
            } else {
                runUpdate(release)
            }
        }
    }

    fun onBackupInterstitialContinue() {
        _showBackupInterstitial.value = false
        pendingRelease?.let { runUpdate(it) }
    }

    fun onBackupInterstitialSetup(onGoToBackup: () -> Unit) {
        _showBackupInterstitial.value = false
        onGoToBackup()
    }

    fun clearStatus() {
        _statusMessage.value = null
    }

    fun loadHistory() {
        if (_historyLoading.value) return
        _historyLoading.value = true
        _historyError.value = null
        viewModelScope.launch {
            try {
                _history.value = updateRepository.fetchReleaseHistory()
            } catch (e: Exception) {
                _historyError.value = e.message ?: "Could not load release history."
            } finally {
                _historyLoading.value = false
            }
        }
    }

    fun requestDowngrade(release: ReleaseInfo) {
        if (!canTriggerPhaseWork()) return
        _downgradeCandidate.value = release
    }

    fun cancelDowngrade() {
        _downgradeCandidate.value = null
    }

    fun confirmDowngrade(release: ReleaseInfo) {
        _downgradeCandidate.value = null
        _phase.value = Phase.DowngradePreparing
        viewModelScope.launch {
            val backup = updateFlow.createBackup()
            if (backup.isFailure) {
                _phase.value = Phase.Error(
                    "Backup failed - downgrade aborted. ${backup.exceptionOrNull()?.message}"
                )
                hapticsManager.reject()
                return@launch
            }
            when (val outcome = updateFlow.scheduleDowngrade(release.apkUrl)) {
                is UpdateFlow.Outcome.NotInstalled -> {
                    _phase.value = Phase.Idle
                    _statusMessage.value =
                        "Backup saved (${backup.getOrNull()}). Install ${release.versionName} " +
                            "from the opened page, then restore your data on the next launch."
                    hapticsManager.backgroundComplete()
                }
                is UpdateFlow.Outcome.NoApk -> {
                    _phase.value = Phase.Error("This release has no APK attached.")
                }
                is UpdateFlow.Outcome.Failure -> {
                    _phase.value = Phase.Error(outcome.message)
                    hapticsManager.reject()
                }
                is UpdateFlow.Outcome.Installing -> Unit
            }
        }
    }

    private fun runUpdate(release: StagedRelease) {
        _phase.value = Phase.BackingUp
        viewModelScope.launch {
            val backup = updateFlow.createBackup()
            if (backup.isFailure) {
                _phase.value = Phase.Error(
                    "Backup failed - update aborted to protect your data. " +
                        backup.exceptionOrNull()?.message
                )
                hapticsManager.reject()
                return@launch
            }

            _phase.value = Phase.Downloading
            _progress.value = null
            when (val outcome = updateFlow.downloadAndInstall(release) { done, total ->
                _progress.value = done to total
            }) {
                is UpdateFlow.Outcome.Installing -> {
                    _phase.value = Phase.Idle
                    _progress.value = null
                    _statusMessage.value =
                        "Backup saved (${backup.getOrNull()}). Confirm the system install prompt."
                    preferencesRepository.updateUpdateStagedRelease(null)
                    preferencesRepository.updateUpdateSkippedVersion(release.tag)
                    hapticsManager.backgroundComplete()
                }
                is UpdateFlow.Outcome.NoApk -> {
                    _phase.value = Phase.Error("This release has no APK attached.")
                }
                is UpdateFlow.Outcome.Failure -> {
                    _phase.value = Phase.Error(outcome.message)
                    _progress.value = null
                    hapticsManager.reject()
                }
                is UpdateFlow.Outcome.NotInstalled -> Unit
            }
        }
    }
}