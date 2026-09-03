package dev.vic41148.somn.app.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.vic41148.somn.core.data.repository.SomnPreferencesRepository
import dev.vic41148.somn.core.data.update.UpdateFlow
import dev.vic41148.somn.core.data.update.UpdateScheduler
import dev.vic41148.somn.core.domain.haptic.HapticsManager
import dev.vic41148.somn.core.domain.model.StagedRelease
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State behind the Home-screen update banner. Every safety decision (backup gate, mandatory backup,
 * download + checksum verification, system installer hand-off) lives in [UpdateFlow] - this VM only
 * carries the banner's ephemeral visibility and progress. "Not now" just hides the card for this
 * session; the permanent "don't nag again" lives in the Settings Updates section.
 */
@HiltViewModel
class UpdateBannerViewModel @Inject constructor(
    private val preferencesRepository: SomnPreferencesRepository,
    private val updateFlow: UpdateFlow,
    private val updateScheduler: UpdateScheduler,
    private val hapticsManager: HapticsManager
) : ViewModel() {

    enum class DownloadPhase { Idle, BackingUp, Downloading }

    private val _staged = MutableStateFlow(StagedRelease("", "", "", null, null, 0L))
    val staged: StateFlow<StagedRelease> = _staged.asStateFlow()

    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    private val _phase = MutableStateFlow(DownloadPhase.Idle)
    val phase: StateFlow<DownloadPhase> = _phase.asStateFlow()

    private val _progress = MutableStateFlow<Pair<Long, Long>?>(null)
    val progress: StateFlow<Pair<Long, Long>?> = _progress.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _showBackupInterstitial = MutableStateFlow(false)
    val showBackupInterstitial: StateFlow<Boolean> = _showBackupInterstitial.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.updateStagedRelease.collect { staged ->
                _staged.value = staged
                _visible.value = staged.isPresent
            }
        }
        // One fresh probe when the app opens, so the banner reflects reality on first frame rather
        // than whatever the last periodic worker found (or missed while offline).
        viewModelScope.launch {
            val autoCheck = preferencesRepository.updateAutoCheck.first()
            if (autoCheck && _staged.value.let { !it.isPresent }) {
                updateScheduler.checkNow()
            }
        }
    }

    fun dismiss() {
        _visible.value = false
        hapticsManager.tick()
    }

    fun requestUpdate(staged: StagedRelease) {
        if (_phase.value != DownloadPhase.Idle) return
        viewModelScope.launch {
            if (updateFlow.backupGateRequiresInterstitial()) {
                _showBackupInterstitial.value = true
            } else {
                runUpdate(staged)
            }
        }
    }

    fun onInterstitialContinue() {
        _showBackupInterstitial.value = false
        _staged.value.let { if (it.isPresent) runUpdate(it) }
    }

    fun onInterstitialSetup() {
        _showBackupInterstitial.value = false
        _visible.value = false
    }

    fun clearError() {
        _error.value = null
    }

    private fun runUpdate(staged: StagedRelease) {
        _error.value = null
        _phase.value = DownloadPhase.BackingUp
        viewModelScope.launch {
            val backup = updateFlow.createBackup()
            if (backup.isFailure) {
                _phase.value = DownloadPhase.Idle
                _error.value = "Backup failed - update aborted. ${backup.exceptionOrNull()?.message}"
                hapticsManager.reject()
                return@launch
            }

            _phase.value = DownloadPhase.Downloading
            _progress.value = null
            when (val outcome = updateFlow.downloadAndInstall(staged) { done, total ->
                _progress.value = done to total
            }) {
                is UpdateFlow.Outcome.Installing -> {
                    _phase.value = DownloadPhase.Idle
                    _visible.value = false
                    hapticsManager.backgroundComplete()
                }
                is UpdateFlow.Outcome.NoApk -> {
                    _phase.value = DownloadPhase.Idle
                    _error.value = "This release has no APK."
                }
                is UpdateFlow.Outcome.Failure -> {
                    _phase.value = DownloadPhase.Idle
                    _progress.value = null
                    _error.value = outcome.message
                    hapticsManager.reject()
                }
                is UpdateFlow.Outcome.NotInstalled -> Unit
            }
        }
    }
}