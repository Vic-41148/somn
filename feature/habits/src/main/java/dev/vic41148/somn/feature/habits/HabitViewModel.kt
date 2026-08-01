package dev.vic41148.somn.feature.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.vic41148.somn.core.data.repository.HabitLogRepository
import dev.vic41148.somn.core.data.repository.SleepRepository
import dev.vic41148.somn.core.data.repository.UserProfileRepository
import dev.vic41148.somn.core.domain.model.HabitEntry
import dev.vic41148.somn.core.domain.model.HabitLog
import dev.vic41148.somn.core.domain.model.RecoveryPlan
import dev.vic41148.somn.core.domain.model.SleepDebt
import dev.vic41148.somn.core.domain.usecase.CorrelationReport
import dev.vic41148.somn.core.domain.usecase.CorrelationUseCase
import dev.vic41148.somn.core.domain.usecase.SleepDebtUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class HabitViewModel @Inject constructor(
    private val habitLogRepository: HabitLogRepository,
    private val sleepRepository: SleepRepository,
    private val userProfileRepository: UserProfileRepository,
    private val sleepDebtUseCase: SleepDebtUseCase,
    private val correlationUseCase: CorrelationUseCase
) : ViewModel() {

    // --- Today's habits ---

    val todayLogs: StateFlow<List<HabitLog>> = habitLogRepository
        .getLogsForDate(LocalDate.now())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // --- Medication log (ADHD-gated) ---

    val medicationLogs: StateFlow<List<HabitLog>> = habitLogRepository
        .getMedicationLogs(limit = 30)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // --- Sleep debt ---

    private val _sleepDebt = MutableStateFlow<SleepDebt?>(null)
    val sleepDebt: StateFlow<SleepDebt?> = _sleepDebt.asStateFlow()

    private val _recoveryPlan = MutableStateFlow<RecoveryPlan?>(null)
    val recoveryPlan: StateFlow<RecoveryPlan?> = _recoveryPlan.asStateFlow()

    // --- Correlations ---

    private val _correlationReport = MutableStateFlow<CorrelationReport?>(null)
    val correlationReport: StateFlow<CorrelationReport?> = _correlationReport.asStateFlow()

    // --- Loading / error ---

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    // --- User profile (for ADHD gating, target sleep hours) ---

    val userProfile = userProfileRepository.observeProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        loadDebtAndCorrelations()
    }

    private fun loadDebtAndCorrelations() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                combine(
                    habitLogRepository.getAllLogs(),
                    userProfileRepository.observeProfile()
                ) { habitLogs, profile ->
                    Pair(habitLogs, profile)
                }.collect { result ->
                    val habitLogs = result.first
                    val profile = result.second
                    // SESS-04: naps/commute/shift sessions would dilute nightly debt/correlation aggregates.
                    val sessions = sleepRepository.getRecentMainSleepSessions(14)
                    val targetMinutes = ((profile?.targetSleepHours ?: 8f) * 60).toInt()

                    val debtAndPlan = sleepDebtUseCase.calculate(sessions, targetMinutes)
                    _sleepDebt.value = debtAndPlan.first
                    _recoveryPlan.value = debtAndPlan.second

                    _correlationReport.value = correlationUseCase.calculate(sessions, habitLogs)
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _isLoading.value = false
            }
        }
    }

    // ---- Actions ----

    fun logEntry(entry: HabitEntry, date: LocalDate = LocalDate.now(), notes: String = "") {
        viewModelScope.launch {
            try {
                habitLogRepository.log(entry, date, notes)
                _toastMessage.value = "Logged!"
            } catch (e: Exception) {
                _toastMessage.value = "Failed to save — please try again."
            }
        }
    }

    fun deleteLog(log: HabitLog) {
        viewModelScope.launch {
            habitLogRepository.delete(log)
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }
}
