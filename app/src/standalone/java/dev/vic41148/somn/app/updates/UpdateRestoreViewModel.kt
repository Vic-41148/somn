package dev.vic41148.somn.app.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.vic41148.somn.core.data.repository.SleepRepository
import dev.vic41148.somn.core.data.repository.SomnPreferencesRepository
import dev.vic41148.somn.core.data.update.UpdateBackupStore
import dev.vic41148.somn.core.domain.haptic.HapticsManager
import dev.vic41148.somn.core.domain.usecase.CalculateSleepScoreUseCase
import dev.vic41148.somn.core.domain.usecase.ImportSleepAsAndroidUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject

/**
 * The post-update/reinstall "we found a backup from before" prompt. After a version change or a
 * downgrade the database is empty, this VM checks for the pre-update backup the UpdateBackupStore
 * mirrored to user-visible storage, and offers to replay it through the standard Sleep-as-Android
 * CSV import. Shown once per install (Prefs flag); answering revokes the offer forever.
 */
@HiltViewModel
class UpdateRestoreViewModel @Inject constructor(
    private val preferencesRepository: SomnPreferencesRepository,
    private val updateBackupStore: UpdateBackupStore,
    private val sleepRepository: SleepRepository,
    private val importSleepAsAndroid: ImportSleepAsAndroidUseCase,
    private val calculateScore: CalculateSleepScoreUseCase,
    private val hapticsManager: HapticsManager
) : ViewModel() {

    data class RestoreOffer(val name: String, val file: File)

    private val _offer = MutableStateFlow<RestoreOffer?>(null)
    val offer: StateFlow<RestoreOffer?> = _offer.asStateFlow()

    private val _restoring = MutableStateFlow(false)
    val restoring: StateFlow<Boolean> = _restoring.asStateFlow()

    private val _resultMessage = MutableStateFlow<String?>(null)
    val resultMessage: StateFlow<String?> = _resultMessage.asStateFlow()

    private var started = false

    fun checkForBackup() {
        if (started) return
        started = true
        viewModelScope.launch {
            if (preferencesRepository.updateRestorePromptShown.first()) return@launch
            val nowHasData = sleepRepository.getRecentSessions(1).isNotEmpty()
            if (nowHasData) return@launch
            val backup = updateBackupStore.findLatestPreUpdateBackup() ?: return@launch
            _offer.value = RestoreOffer(backup.name, backup.file)
        }
    }

    fun restore(offer: RestoreOffer) {
        if (_restoring.value) return
        _restoring.value = true
        viewModelScope.launch {
            val result = runCatching { doRestore(offer) }
            _restoring.value = false
            _resultMessage.value = result.fold(
                onSuccess = { imported ->
                    hapticsManager.backgroundComplete()
                    if (imported == 1) "Restored 1 session from ${offer.name}."
                    else "Restored $imported sessions from ${offer.name}."
                },
                onFailure = {
                    hapticsManager.reject()
                    "Restore failed: ${it.message}"
                }
            )
            preferencesRepository.updateUpdateRestorePromptShown(true)
            _offer.value = null
            offer.file.delete()
        }
    }

    fun decline() {
        _offer.value = null
        viewModelScope.launch {
            preferencesRepository.updateUpdateRestorePromptShown(true)
            _offer.value?.file?.delete()
        }
    }

    fun dismissResult() {
        _resultMessage.value = null
    }

    private suspend fun doRestore(offer: RestoreOffer): Int = withContext(Dispatchers.IO) {
        val csv = when {
            offer.file.name.endsWith(".zip", ignoreCase = true) -> {
                ZipFile(offer.file).use { zip ->
                    val entry = zip.getEntry("somn_export.csv") ?: error("Backup contains no somn_export.csv")
                    zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
                }
            }
            else -> offer.file.readText()
        }
        val result = importSleepAsAndroid(csv)
        var count = 0
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
            count++
        }
        count
    }
}