package dev.vic41148.somn.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vic41148.somn.core.data.repository.SleepRepository
import dev.vic41148.somn.core.domain.model.SleepSession
import dev.vic41148.somn.core.domain.usecase.ExportCsvUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _selectedSession = MutableStateFlow<SleepSession?>(null)
    val selectedSession: StateFlow<SleepSession?> = _selectedSession.asStateFlow()

    private val _csvExport = MutableStateFlow<String?>(null)
    val csvExport: StateFlow<String?> = _csvExport.asStateFlow()

    fun selectSession(session: SleepSession) {
        _selectedSession.value = session
    }

    fun clearSelection() {
        _selectedSession.value = null
    }

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
        _csvExport.value = null
    }
}
