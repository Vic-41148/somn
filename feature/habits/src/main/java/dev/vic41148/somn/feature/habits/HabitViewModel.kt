package dev.vic41148.somn.feature.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.vic41148.somn.core.data.repository.HabitLogRepository
import dev.vic41148.somn.core.data.repository.SleepRepository
import dev.vic41148.somn.core.data.repository.TagRepository
import dev.vic41148.somn.core.data.repository.UserProfileRepository
import dev.vic41148.somn.core.domain.model.HabitEntry
import dev.vic41148.somn.core.domain.model.HabitLog
import dev.vic41148.somn.core.domain.model.RecoveryPlan
import dev.vic41148.somn.core.domain.model.SleepDebt
import dev.vic41148.somn.core.domain.model.SleepSession
import dev.vic41148.somn.core.domain.usecase.CorrelationReport
import dev.vic41148.somn.core.domain.usecase.CorrelationUseCase
import dev.vic41148.somn.core.domain.usecase.ShiftFlag
import dev.vic41148.somn.core.domain.usecase.TagImpact
import dev.vic41148.somn.core.domain.usecase.TaggedNight
import dev.vic41148.somn.core.domain.usecase.detectShifts
import dev.vic41148.somn.core.domain.usecase.tagImpact
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
    private val tagRepository: TagRepository,
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

    /** R4: proactive shift flags — empty is the common case, no news is no cards. */
    private val _shiftFlags = MutableStateFlow<List<ShiftFlag>>(emptyList())
    val shiftFlags: StateFlow<List<ShiftFlag>> = _shiftFlags.asStateFlow()

    /** R4: tag presence as binary predictors next to the big-four habits. */
    private val _tagImpacts = MutableStateFlow<List<TagImpact>>(emptyList())
    val tagImpacts: StateFlow<List<TagImpact>> = _tagImpacts.asStateFlow()

    // --- Loading / error ---

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    // --- User profile (for ADHD gating, target sleep hours) ---

    val userProfile = userProfileRepository.observeProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // R4 taxonomy: idempotent, so cold-start seeding is safe for existing users.
            runCatching { tagRepository.ensureDefaultTags() }
        }
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

                    // R4: correlations run over a 90-night settled window beside the
                    // 7-night early read — same engine, wider sample, maturity-labeled.
                    val wideSessions = sleepRepository.getRecentMainSleepSessions(100)
                    _correlationReport.value = correlationUseCase.calculate(wideSessions, habitLogs)
                    _shiftFlags.value = kotlinx.coroutines.withContext(
                        kotlinx.coroutines.Dispatchers.Default
                    ) {
                        detectShifts(wideSessions, habitLogs)
                    }
                    _tagImpacts.value = kotlinx.coroutines.withContext(
                        kotlinx.coroutines.Dispatchers.Default
                    ) {
                        computeTagImpacts(wideSessions)
                    }
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _isLoading.value = false
            }
        }
    }

    /** Groups the last 90 completed nights by tag and runs each through [tagImpact]. */
    private suspend fun computeTagImpacts(sessions: List<SleepSession>): List<TagImpact> {
        val nights = sessions.filter { it.isCompleted }.sortedBy { it.startTimeMillis }.takeLast(90)
        if (nights.isEmpty()) return emptyList()
        val tagsBySession = nights.associate { session ->
            session.id to runCatching { tagRepository.getTagsForSession(session.id) }
                .getOrDefault(emptyList()).map { it.name }.toSet()
        }
        return tagsBySession.values.flatten().distinct().mapNotNull { tagName ->
            tagImpact(
                tagName,
                nights.map { session ->
                    TaggedNight(
                        tagged = tagsBySession[session.id]?.contains(tagName) == true,
                        score = session.sleepScore
                    )
                }
            )
        }.sortedByDescending { kotlin.math.abs(it.taggedAvgScore - it.untaggedAvgScore) }
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
