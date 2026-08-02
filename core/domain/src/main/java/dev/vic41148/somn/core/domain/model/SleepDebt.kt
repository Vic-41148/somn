package dev.vic41148.somn.core.domain.model

import java.time.LocalDate

/**
 * Represents the user's accumulated sleep debt over the last 14 days.
 *
 * Sleep debt = cumulative difference between target sleep and actual sleep.
 * Research basis §3.6: cognitive performance degrades linearly with increasing debt;
 * recovery requires ~1 additional hour/night for ~1 week per week of debt accumulated.
 */
data class SleepDebt(
    /** Total accumulated sleep shortfall in minutes over the 14-day window. */
    val totalDebtMinutes: Int,
    /** Whether debt is improving, stable, or worsening vs the prior 7-day window. */
    val trend: DebtTrend,
    /** Severity classification for UI colour-coding. */
    val level: DebtLevel,
    /** Per-day breakdown for the 14-day chart. */
    val dailyBreakdown: List<DailyDebt>
) {
    val totalDebtHours: Float get() = totalDebtMinutes / 60f
}

/**
 * Sleep debt for a single day.
 * A positive [debtMinutes] means the user slept less than their target.
 * A negative [debtMinutes] means they overslept (surplus).
 */
data class DailyDebt(
    val date: LocalDate,
    val targetMinutes: Int,
    val actualMinutes: Int,
) {
    val debtMinutes: Int get() = targetMinutes - actualMinutes
    val hasSurplus: Boolean get() = debtMinutes < 0
    val hasData: Boolean get() = actualMinutes > 0
}

/** Direction of sleep debt trend (comparing last 7 days vs prior 7 days). */
enum class DebtTrend(val displayName: String, val glyph: String) {
    IMPROVING("Improving", "↑"),
    STABLE("Stable", "→"),
    WORSENING("Worsening", "↓")
}

/**
 * Severity of accumulated sleep debt.
 * Thresholds based on research doc §3.6 — cognitive impact becomes measurable at ~1h/week.
 */
enum class DebtLevel(val displayName: String) {
    NONE("None"),          // < 30 min total
    MILD("Mild"),          // 30 min – 2 h
    MODERATE("Moderate"),  // 2 h – 5 h
    SEVERE("Severe")       // > 5 h
    ;

    companion object {
        fun from(totalDebtMinutes: Int): DebtLevel = when {
            totalDebtMinutes < 30 -> NONE
            totalDebtMinutes < 120 -> MILD
            totalDebtMinutes < 300 -> MODERATE
            else -> SEVERE
        }
    }
}
