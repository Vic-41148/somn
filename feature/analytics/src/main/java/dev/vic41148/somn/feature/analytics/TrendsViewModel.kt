package dev.vic41148.somn.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.vic41148.somn.core.data.repository.SleepRepository
import dev.vic41148.somn.core.data.repository.UserProfileRepository
import dev.vic41148.somn.core.domain.model.MenstrualCyclePhase
import dev.vic41148.somn.core.domain.model.SleepSession
import dev.vic41148.somn.core.domain.usecase.formatDurationShort
import dev.vic41148.somn.core.domain.usecase.lifeStageBanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** DATA-03: which metric the trend line currently plots — one at a time, since the metrics do not share a scale. */
enum class TrendMetric(val displayName: String) {
    SCORE("Score"),
    DURATION_HOURS("Duration"),
    EFFICIENCY("Efficiency"),
    DEEP_PERCENT("Deep Sleep"),
    REM_PERCENT("REM Sleep")
}

/** One metric's headline numbers for the selected range — null when fewer than 2 sessions. */
data class TrendStats(
    val current: String,
    val average: String,
    val best: String,
    val delta: String,
    val deltaPositive: Boolean,
    val nights: Int
)
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
    // the same reasoning CircadianViewModel already applies.
    /** DATA-03: one metric at a time, since the metrics do not share a scale. */
    val sessions: StateFlow<List<SleepSession>> = sleepRepository.observeMainSleepSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Profile-derived deep-sleep target percent (age-adjusted). It is null until onboarding completes. */
    val deepSleepTargetPercent: StateFlow<Float?> = userProfileRepository.observeProfile()
        .map { it?.deepSleepTargetPercent }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** DATA-04: null when the user's profile does not have cycle tracking enabled/configured — screen hides the overlay entirely rather than showing an empty one. */    val cyclePhaseRuns: StateFlow<List<CyclePhaseRun>?> = combine(
        userProfileRepository.observeProfile(),
        sessions
    ) { profile, sessions ->
        // The code captures it into a local: lastPeriodStartDate lives in another module (core:domain), so
        // Kotlin will not smart-cast it to non-null from the guard below.
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

    /**
     * R5: pregnancy/postpartum context banner — the phase-run overlay above only fits
     * cycling users, so non-cycling life stages get their trend context as words.
     */
    val lifeStageBanner: StateFlow<String?> = userProfileRepository.observeProfile()
        .map { profile ->
            if (profile == null) null
            else lifeStageBanner(profile.lifeStage.name, profile.pregnancyTrimester)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Report range in days. A null value means all history. */
    private val _rangeDays = MutableStateFlow<Int?>(90)
    val rangeDays: StateFlow<Int?> = _rangeDays.asStateFlow()

    fun selectRange(days: Int?) {
        _rangeDays.value = days
    }

    /** Sessions inside the selected range, oldest first for charting. */
    val rangedSessions: StateFlow<List<SleepSession>> = combine(sessions, rangeDays) { list, days ->
        val filtered = if (days == null) list
        else {
            val cutoff = System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L
            list.filter { it.startTimeMillis >= cutoff }
        }
        filtered.sortedBy { it.startTimeMillis }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Headline numbers for the selected metric + range. A null value means fewer than 2 sessions. */
    val trendStats: StateFlow<TrendStats?> = combine(rangedSessions, _selectedMetric) { list, metric ->
        if (list.size < 2) return@combine null
        val values = list.map { valueFor(it, metric) }
        val current = values.last()
        val avg = values.average().toFloat()
        val best = values.max()
        val delta = current - values.first()
        TrendStats(
            current = formatTrendValue(current, metric),
            average = formatTrendValue(avg, metric),
            best = formatTrendValue(best, metric),
            delta = (if (delta >= 0) "+" else "") + formatTrendValue(delta, metric, signed = true),
            deltaPositive = delta >= 0,
            nights = list.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun formatTrendValue(value: Float, metric: TrendMetric, signed: Boolean = false): String =
        when (metric) {
            TrendMetric.SCORE -> "${value.toInt()}"
            TrendMetric.DURATION_HOURS -> {
                val mins = (value * 60).toInt()
                val sign = if (signed && mins > 0) "+" else ""
                val abs = kotlin.math.abs(mins)
                val body = formatDurationShort(abs)
                if (mins < 0) "-$body" else "$sign$body"
            }
            TrendMetric.EFFICIENCY -> "${value.toInt()}%"
            TrendMetric.DEEP_PERCENT -> "${value.toInt()}%"
            TrendMetric.REM_PERCENT -> "${value.toInt()}%"
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
