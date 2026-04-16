package dev.vic41148.somn.core.domain.model

import java.time.LocalTime

/**
 * Social jet lag — the discrepancy between a user's biological sleep timing
 * and the timing imposed by work/social schedule (typically manifesting as a
 * weekday vs. weekend midpoint difference).
 *
 * Research basis §2.11 (Frontiers in Sleep 2025): Social jet lag is an independent
 * cardiovascular risk factor. A discrepancy of >120 minutes is associated with
 * metabolic syndrome, depression, and elevated cardiovascular risk.
 *
 * Calculation method (MCTQ standard):
 *   MSF (mid-sleep on free days) - MSW (mid-sleep on work days)
 *   where mid-sleep = (sleep onset + wake time) / 2
 */
data class SocialJetLag(
    /** Average sleep midpoint on weekday sessions (Mon–Fri). */
    val weekdayMidpoint: LocalTime,
    /** Average sleep midpoint on weekend sessions (Sat–Sun). */
    val weekendMidpoint: LocalTime,
    /** Absolute difference in minutes between weekday and weekend midpoints. */
    val jetLagMinutes: Int,
    /** Health risk classification. */
    val riskLevel: JetLagRisk,
    /** Number of weekday sessions used. */
    val weekdaySessions: Int,
    /** Number of weekend sessions used. */
    val weekendSessions: Int,
    /** Plain-language explanation, including health context for HIGH. */
    val insight: String
) {
    val jetLagHours: Float get() = jetLagMinutes / 60f

    companion object {
        /** Minimum weekday sessions required. */
        const val MIN_WEEKDAY_SESSIONS = 3
        /** Minimum weekend sessions required. */
        const val MIN_WEEKEND_SESSIONS = 2
    }
}

/**
 * Health risk classification for social jet lag magnitude.
 *
 * Thresholds based on Frontiers in Sleep 2025 and European Sleep Research Society guidance.
 */
enum class JetLagRisk(val displayName: String, val color: RiskColor) {
    /** <30 min difference — no clinical concern. */
    NONE("None", RiskColor.GREEN),
    /** 30–60 min — subclinical, worth awareness. */
    MILD("Mild", RiskColor.YELLOW),
    /** 60–120 min — increased metabolic and mood risk. */
    MODERATE("Moderate", RiskColor.ORANGE),
    /** >120 min — independent cardiovascular risk factor. */
    HIGH("High", RiskColor.RED);

    companion object {
        fun from(jetLagMinutes: Int): JetLagRisk = when {
            jetLagMinutes <  30  -> NONE
            jetLagMinutes <  60  -> MILD
            jetLagMinutes < 120  -> MODERATE
            else                 -> HIGH
        }
    }
}

/** Semantic colour token for risk level — mapped to Material 3 colours in the UI layer. */
enum class RiskColor { GREEN, YELLOW, ORANGE, RED }
