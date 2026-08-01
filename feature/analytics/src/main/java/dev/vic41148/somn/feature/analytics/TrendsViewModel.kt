package dev.vic41148.somn.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.vic41148.somn.core.data.repository.SleepRepository
import dev.vic41148.somn.core.data.repository.UserProfileRepository
import dev.vic41148.somn.core.domain.model.MenstrualCyclePhase
import dev.vic41148.somn.core.domain.model.SleepSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** DATA-03: which metric the trend line currently plots — one at a time, since the metrics don't share a scale. */
enum class TrendMetric(val displayName: String) {
    SCORE("Score"),
    DURATION_HOURS("Duration"),
    EFFICIENCY("Efficiency"),
    DEEP_PERCENT("Deep Sleep"),
    REM_PERCENT("REM Sleep")
}

/** DATA-04: a run of consecutive days in the same menstrual cycle phase, in wall-clock millis. [startMillis, endMillis). */
data class CyclePhaseRun(val phase: MenstrualCyclePhase, val startMillis: Long, val endMillis: Long)

@HiltViewModel
class TrendsViewModel @Inject constructor(
    private val sleepRepository: SleepRepository,
    private val userProfileRepository: UserProfileRepository
) : ViewModel() {

    private val _selectedMetric = MutableStateFlow(TrendMetric.SCORE)
    val selectedMetric: StateFlow<TrendMetric> = _selectedMetric.asStateFlow()

    // SESS-04: trends are a bedtime-consistency signal — naps/commute/shift sessions would skew it,
    // same reasoning CircadianViewModel already applies.
    val sessions: StateFlow<List<SleepSession>> = sleepRepository.observeMainSleepSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** DATA-04: null when the user's profile doesn't have cycle tracking enabled/configured — screen hides the overlay entirely rather than showing an empty one. */
    val cyclePhaseRuns: StateFlow<List<CyclePhaseRun>?> = combine(
        userProfileRepository.observeProfile(),
        sessions
    ) { profile, sessions ->
        // Captured into a local: lastPeriodStartDate lives in another module (core:domain), so
        // Kotlin won't smart-cast it to non-null from the guard below.
        val lastPeriodStart = profile?.lastPeriodStartDate
        if (profile == null || !profile.showCycleFeatures || lastPeriodStart == null) return@combine null
        if (sessions.isEmpty()) return@combine null

        val zone = ZoneId.systemDefault()
        val firstDay = Instant.ofEpochMilli(sessions.minOf { it.startTimeMillis }).atZone(zone).toLocalDate()
        val lastDay = Instant.ofEpochMilli(sessions.maxOf { it.startTimeMillis }).atZone(zone).toLocalDate()

        buildPhaseRuns(lastPeriodStart, profile.cycleLength, firstDay, lastDay, zone)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun selectMetric(metric: TrendMetric) {
        _selectedMetric.value = metric
    }

    fun valueFor(session: SleepSession, metric: TrendMetric): Float = when (metric) {
        TrendMetric.SCORE -> session.sleepScore.toFloat()
        TrendMetric.DURATION_HOURS -> session.sleepDurationMinutes / 60f
        TrendMetric.EFFICIENCY -> session.sleepEfficiency
        TrendMetric.DEEP_PERCENT -> session.deepSleepPercent
        TrendMetric.REM_PERCENT -> session.remSleepPercent
    }

    /** Walks [firstDay, lastDay] day by day, compressing consecutive same-phase days into runs rather than one band per day. */
    private fun buildPhaseRuns(
        lastPeriodStart: LocalDate,
        cycleLength: Int,
        firstDay: LocalDate,
        lastDay: LocalDate,
        zone: ZoneId
    ): List<CyclePhaseRun> {
        val runs = mutableListOf<CyclePhaseRun>()
        var runStart: LocalDate? = null
        var runPhase: MenstrualCyclePhase? = null
        var cursor = firstDay

        fun flush(endExclusive: LocalDate) {
            val start = runStart ?: return
            val phase = runPhase ?: return
            runs.add(
                CyclePhaseRun(
                    phase = phase,
                    startMillis = start.atStartOfDay(zone).toInstant().toEpochMilli(),
                    endMillis = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
                )
            )
        }

        while (!cursor.isAfter(lastDay)) {
            val phase = MenstrualCyclePhase.currentPhase(lastPeriodStart, cycleLength, cursor)
            if (phase != runPhase) {
                flush(cursor)
                runStart = cursor
                runPhase = phase
            }
            cursor = cursor.plusDays(1)
        }
        flush(lastDay.plusDays(1))

        return runs
    }
}
