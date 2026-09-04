package dev.vic41148.somn.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vic41148.somn.core.data.repository.SleepRepository
import dev.vic41148.somn.core.domain.model.SleepSession
import dev.vic41148.somn.core.domain.model.AudioEvent
import dev.vic41148.somn.core.domain.usecase.ExportCsvUseCase
import dev.vic41148.somn.core.domain.usecase.ReportSummary
import dev.vic41148.somn.core.domain.usecase.summarizeSessions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val sleepRepository: SleepRepository,
    private val exportCsv: ExportCsvUseCase
) : ViewModel() {

    val sessions = sleepRepository.observeCompletedSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Report range in days; null = all history. Defaults to 30 so the header reads as "recent". */
    private val _rangeDays = MutableStateFlow<Int?>(30)
    val rangeDays: StateFlow<Int?> = _rangeDays.asStateFlow()

    fun selectRange(days: Int?) {
        _rangeDays.value = days
    }

    /** Sessions inside the selected range, newest first — drives both the header stats and the list. */
    val rangedSessions: StateFlow<List<SleepSession>> = combine(sessions, rangeDays) { list, days ->
        if (days == null) list
        else {
            val cutoff = System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L
            list.filter { it.startTimeMillis >= cutoff }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Aggregate stats for the selected range; null = empty range, caller shows empty state. */
    val summary: StateFlow<ReportSummary?> = rangedSessions.map { summarizeSessions(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _selectedSession = MutableStateFlow<SleepSession?>(null)
    val selectedSession: StateFlow<SleepSession?> = _selectedSession.asStateFlow()

    private val _csvExport = MutableStateFlow<String?>(null)
    val csvExport: StateFlow<String?> = _csvExport.asStateFlow()

    private val _selectedSessionIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedSessionIds: StateFlow<Set<Long>> = _selectedSessionIds.asStateFlow()

    private val _exportProgress = MutableStateFlow<Float?>(null)
    val exportProgress: StateFlow<Float?> = _exportProgress.asStateFlow()

    private val _exportStatus = MutableStateFlow<String?>(null)
    val exportStatus: StateFlow<String?> = _exportStatus.asStateFlow()

    fun toggleSelection(sessionId: Long) {
        val current = _selectedSessionIds.value
        _selectedSessionIds.value = if (current.contains(sessionId)) {
            current - sessionId
        } else {
            current + sessionId
        }
    }

    fun clearBulkSelection() {
        _selectedSessionIds.value = emptySet()
    }

    fun deleteSelectedSessions() {
        val ids = _selectedSessionIds.value
        viewModelScope.launch {
            ids.forEach { id ->
                val session = sessions.value.find { it.id == id }
                if (session != null) {
                    sleepRepository.deleteSession(session)
                }
            }
            clearBulkSelection()
        }
    }

    fun exportSelectedSessions(context: android.content.Context, folderUri: android.net.Uri) {
        val sessionIds = _selectedSessionIds.value
        if (sessionIds.isEmpty()) return

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _exportProgress.value = 0f
            try {
                val total = sessionIds.size
                var count = 0
                
                val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, folderUri)
                
                sessionIds.forEach { sessionId ->
                    val events = sleepRepository.getAudioEventsSynchronous(sessionId)
                    events.forEach { event ->
                        event.clipPath?.let { path ->
                            val file = java.io.File(path)
                            if (file.exists()) {
                                // Create file in SAF
                                val newFile = rootDoc?.createFile("audio/wav", "somn_audio_${sessionId}_${event.id}.wav")
                                newFile?.uri?.let { destUri ->
                                    context.contentResolver.openOutputStream(destUri)?.use { out ->
                                        file.inputStream().use { input ->
                                            input.copyTo(out)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    count++
                    _exportProgress.value = count.toFloat() / total
                }
                _exportStatus.value = "Exported $count sessions!"
            } catch (e: Exception) {
                _exportStatus.value = "Export failed: ${e.message}"
            } finally {
                _exportProgress.value = null
            }
        }
    }

    fun selectSession(session: SleepSession) {
        _selectedSession.value = session
    }

    fun clearSelection() {
        _selectedSession.value = null
    }

    fun observeAudioEvents(sessionId: Long) = sleepRepository.observeAudioEvents(sessionId)

    /** HEALTH-01: one-shot fetch — external vitals are written once per sync, no live-updating Flow needed. */
    suspend fun getExternalVitals(sessionId: Long) = sleepRepository.getExternalVitals(sessionId)

    fun deleteSession(session: SleepSession) {
        viewModelScope.launch {
            sleepRepository.deleteSession(session)
            _selectedSession.value = null
        }
    }

    fun exportAllSessions() {
        viewModelScope.launch {
            val allSessions = sessions.value
            val csv = exportCsv(allSessions)
            _csvExport.value = csv
        }
    }

    fun clearExport() {
        _exportStatus.value = null
        _csvExport.value = null
    }
}
