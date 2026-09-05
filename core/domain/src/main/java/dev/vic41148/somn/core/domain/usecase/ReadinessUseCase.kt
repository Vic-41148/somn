package dev.vic41148.somn.core.domain.usecase

import dev.vic41148.somn.core.domain.model.SleepDebt
import dev.vic41148.somn.core.domain.model.SleepSession
import kotlin.math.sqrt

/**
 * Morning readiness verdict — "should I push today", computed from data already in the DB.
 * The WHOOP-Recovery / Oura-Readiness half of the loop Somn was missing: the sleep score
 * describes last night, readiness prescribes today.
 *
 * Rules, all deliberate:
 * - Personal baselines only (14-day windows over the user's own history), never
 *   population norms.
 * - Graceful degradation: phone-only users with no Health Connect vitals still get a
 *   number from sleep signals alone. Missing contributors are skipped (weights
 *   renormalize) and reported as `hasData = false` — never scored as zero.
 * - Debt dominance: a very short last night caps the verdict no matter how good the
 *   vitals look, because one bad night of physiology readings cannot offset lost sleep.
 * - Pure function, unit-tested independently of any ViewModel (same pattern as
 *   [summarizeSessions]).
 */
enum class ReadinessZone(val displayName: String) {
    READY("Ready"),
    STEADY("Steady"),
    REST("Rest")
    ;

    companion object {
        fun from(score: Int): ReadinessZone = when {
            score >= 67 -> READY
            score >= 34 -> STEADY
            else -> REST
        }
    }
}

data class ReadinessContributor(
    val label: String,
    /** One-line plain-language explanation, e.g. "1h 20m over 14 nights · Improving". */
    val detail: String,
    /** 0–100 sub-score. */
    val score: Int,
    val hasData: Boolean
)

data class ReadinessResult(
    val score: Int,
    val zone: ReadinessZone,
    val contributors: List<ReadinessContributor>,
    /** Completed nights inside the 14-day window — drives the n/14 calibration counter. */
    val nightsUsed: Int,
    val isCalibrated: Boolean
)

/**
 * Last-night wearable deltas vs the user's own 14-day median. All null when Health
 * Connect vitals are unavailable — the engine degrades to sleep signals (see above).
 * Positive HRV delta is good (higher variability = better recovery). For RHR and
 * temperature, closeness to baseline is good in either direction.
 */
data class VitalsDeviation(
    val restingHrDeltaBpm: Float? = null,
    val hrvDeltaMs: Float? = null,
    val tempDeltaCelsius: Float? = null
) {
    val hasAnyData: Boolean get() =
        restingHrDeltaBpm != null || hrvDeltaMs != null || tempDeltaCelsius != null
}

/**
 * Prior-day movement, for the activity contributor. Null fields when Health Connect has
 * nothing — the engine degrades to sleep signals instead of scoring missing data as zero.
 *
 * Scored against daily targets (10,000 steps / 45 active minutes) rather than a personal
 * baseline because step history is not persisted — an honest, documented exception to the
 * "personal baselines only" rule, and one that never fires without data.
 */
data class ActivityDeviation(
    val priorDaySteps: Int? = null,
    val priorDayActiveMinutes: Int? = null
) {
    val hasAnyData: Boolean get() = priorDaySteps != null || priorDayActiveMinutes != null
}

private const val WINDOW_DAYS = 14
private const val CALIBRATED_NIGHTS = 3

/** Null when there is nothing to assess — callers render calibration/empty state. */
fun assessReadiness(
    sessions: List<SleepSession>,
    debt: SleepDebt?,
    vitals: VitalsDeviation? = null,
    /** R6: prior-day movement, contributes only when Health Connect has data. */
    activity: ActivityDeviation? = null,
    nowMillis: Long = System.currentTimeMillis(),
    /** Rest Mode boundary: sick nights leave the window entirely. */
    excludeSinceMillis: Long? = null
): ReadinessResult? {
    val cutoff = nowMillis - WINDOW_DAYS * 24 * 60 * 60 * 1000L
    val window = sessions.filter {
        it.isCompleted && it.startTimeMillis >= cutoff &&
            (excludeSinceMillis == null || it.startTimeMillis < excludeSinceMillis)
    }.sortedBy { it.startTimeMillis }
    if (window.isEmpty()) return null
    val last = window.last()

    val contributors = mutableListOf<ReadinessContributor>()

    // Last night (weight 0.35) — the score Somn already computes, reused directly.
    contributors.add(
        ReadinessContributor(
            label = "Last night",
            detail = "Score ${last.sleepScore} · ${formatDurationShort(last.sleepDurationMinutes)}",
            score = last.sleepScore,
            hasData = true
        )
    )

    // Debt position (weight 0.25) — 0 debt scores 100, 10h+ scores 0, linear between.
    if (debt != null) {
        val debtScore = ((1f - debt.totalDebtMinutes / 600f).coerceIn(0f, 1f) * 100).toInt()
        contributors.add(
            ReadinessContributor(
                label = "Sleep debt",
                detail = "${formatDurationShort(debt.totalDebtMinutes)} over 14 nights · ${debt.trend.displayName}",
                score = debtScore,
                hasData = true
            )
        )
    }

    // Consistency (weight 0.20) — bedtime variance across the window. Needs 3+ nights.
    val bedtimes = window.map { millisToMinutesOfDay(it.startTimeMillis) }
    if (bedtimes.size >= CALIBRATED_NIGHTS) {
        val stdev = circularStdevMinutes(bedtimes)
        val consistencyScore = ((1f - (stdev - 30f) / 150f).coerceIn(0f, 1f) * 100).toInt()
        contributors.add(
            ReadinessContributor(
                label = "Consistency",
                detail = "Bedtimes vary ±${stdev.toInt()} min",
                score = consistencyScore,
                hasData = true
            )
        )
    } else {
        contributors.add(
            ReadinessContributor(
                label = "Consistency",
                detail = "Needs $CALIBRATED_NIGHTS+ nights",
                score = 0,
                hasData = false
            )
        )
    }

    // Vitals (weight 0.20) — deviation from personal baseline, when available.
    if (vitals != null && vitals.hasAnyData) {
        val subs = mutableListOf<Float>()
        vitals.restingHrDeltaBpm?.let { d ->
            subs.add(((1f - (kotlin.math.abs(d) - 2f) / 6f).coerceIn(0f, 1f)) * 100)
        }
        vitals.hrvDeltaMs?.let { d ->
            // Higher-than-baseline HRV is good. Only shortfalls deduct.
            subs.add(((1f + d / 10f).coerceIn(0f, 1f)) * 100)
        }
        vitals.tempDeltaCelsius?.let { d ->
            subs.add(((1f - (kotlin.math.abs(d) - 0.2f) / 0.6f).coerceIn(0f, 1f)) * 100)
        }
        val vitalsScore = if (subs.isEmpty()) 0 else subs.average().toInt()
        contributors.add(
            ReadinessContributor(
                label = "Overnight vitals",
                detail = vitalsDetail(vitals),
                score = vitalsScore,
                hasData = subs.isNotEmpty()
            )
        )
    }

    // Activity (weight 0.10) — prior-day movement vs daily targets, when Health Connect
    // has steps or exercise sessions. Absent by default (no data → skipped, not zero).
    if (activity != null && activity.hasAnyData) {
        contributors.add(
            ReadinessContributor(
                label = "Yesterday's activity",
                detail = activityDetail(activity),
                score = activityScore(activity),
                hasData = true
            )
        )
    }

    val weights = mapOf(
        "Last night" to 0.30f,
        "Sleep debt" to 0.25f,
        "Consistency" to 0.15f,
        "Overnight vitals" to 0.20f,
        "Yesterday's activity" to 0.10f
    )
    val available = contributors.filter { it.hasData }
    val totalWeight = available.sumOf { (weights[it.label] ?: 0f).toDouble() }.toFloat()
    var score = if (totalWeight <= 0f) 0
    else (available.sumOf { it.score * (weights[it.label] ?: 0f).toDouble() } / totalWeight).toInt()

    // Debt dominance: a very short last night caps the verdict — good vitals cannot
    // offset lost sleep.
    if (last.sleepScore < 45) score = minOf(score, 59)

    return ReadinessResult(
        score = score.coerceIn(0, 100),
        zone = ReadinessZone.from(score.coerceIn(0, 100)),
        contributors = contributors,
        nightsUsed = window.size,
        isCalibrated = window.size >= CALIBRATED_NIGHTS
    )
}

private fun millisToMinutesOfDay(millis: Long): Int {
    val zone = java.time.ZoneId.systemDefault()
    val t = java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
    return t.hour * 60 + t.minute
}

/**
 * Standard deviation of bedtimes in minutes, midnight-aware: a 23:30 and a 00:30
 * bedtime are 60 minutes apart, not 23 hours. When the raw spread exceeds 12h the
 * early-morning times shift a day forward before measuring.
 */
fun circularStdevMinutes(minutesOfDay: List<Int>): Float {
    if (minutesOfDay.size < 2) return 0f
    val min = minutesOfDay.min()
    val max = minutesOfDay.max()
    val shifted = if (max - min > 720) minutesOfDay.map { if (it < 720) it + 1440 else it }
    else minutesOfDay
    val mean = shifted.average()
    return sqrt(shifted.sumOf { (it - mean) * (it - mean) } / shifted.size).toFloat()
}

private fun vitalsDetail(vitals: VitalsDeviation): String {
    val parts = mutableListOf<String>()
    vitals.restingHrDeltaBpm?.let {
        parts.add("RHR ${if (it >= 0) "+" else ""}${"%.0f".format(it)} bpm vs usual")
    }
    vitals.hrvDeltaMs?.let {
        parts.add("HRV ${if (it >= 0) "+" else ""}${"%.0f".format(it)} ms vs usual")
    }
    vitals.tempDeltaCelsius?.let {
        parts.add("temp ${if (it >= 0) "+" else ""}${"%.1f".format(it)}°C vs usual")
    }
    return parts.joinToString(" · ").ifEmpty { "No vitals data" }
}

/** 0–100 from steps toward 10k and active minutes toward 45, averaged across whichever are present. */
private fun activityScore(activity: ActivityDeviation): Int {
    val subs = mutableListOf<Float>()
    activity.priorDaySteps?.let { subs.add((it / 10_000f).coerceIn(0f, 1f) * 100f) }
    activity.priorDayActiveMinutes?.let { subs.add((it / 45f).coerceIn(0f, 1f) * 100f) }
    return if (subs.isEmpty()) 0 else (subs.average()).toInt()
}

private fun activityDetail(activity: ActivityDeviation): String {
    val parts = mutableListOf<String>()
    activity.priorDaySteps?.let { parts.add("%,d steps".format(it)) }
    activity.priorDayActiveMinutes?.let { parts.add("$it active min") }
    return parts.joinToString(" · ")
}
