package dev.vic41148.somn.core.domain.usecase

import dev.vic41148.somn.core.domain.model.HabitEntry
import dev.vic41148.somn.core.domain.model.HabitLog
import dev.vic41148.somn.core.domain.model.SleepSession
import kotlin.math.sqrt

/**
 * Computes Pearson correlation between lifestyle habits and sleep quality metrics.
 *
 * Each of the 4 correlations is calculated independently:
 *   1. Caffeine (after 14:00) → sleep onset latency (longer = worse)
 *   2. Alcohol units       → sleep efficiency (lower = worse)
 *   3. Stress level        → wake events (more = worse)
 *   4. Exercise that day   → sleep score (higher = better)
 *
 * Requires a minimum of [MIN_DATA_POINTS] paired data points for statistical validity.
 * Returns null for any correlation that lacks sufficient data.
 */
class CorrelationUseCase {

    companion object {
        const val MIN_DATA_POINTS = 7
    }

    /**
     * @param sessions  All completed sleep sessions (each provides the outcome metrics).
     * @param habitLogs All habit logs (each provides the predictor variables, matched by date).
     */
    fun calculate(
        sessions: List<SleepSession>,
        habitLogs: List<HabitLog>
    ): CorrelationReport {
        // Index: sessionDate → session (using session start date as the key)
        val sessionByDate = sessions
            .filter { it.isCompleted }
            .associateBy { session ->
                java.time.Instant.ofEpochMilli(session.startTimeMillis)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
            }

        // Index: date → list of HabitLog
        val habitsByDate = habitLogs.groupBy { it.date }

        return CorrelationReport(
            caffeineToOnset = computeCaffeineCorrelation(sessionByDate, habitsByDate),
            alcoholToEfficiency = computeAlcoholCorrelation(sessionByDate, habitsByDate),
            stressToWakes = computeStressCorrelation(sessionByDate, habitsByDate),
            exerciseToScore = computeExerciseCorrelation(sessionByDate, habitsByDate)
        )
    }

    // ---- Individual correlations ----

    /** Late caffeine (after 14:00) → sleep onset latency minutes. */
    private fun computeCaffeineCorrelation(
        sessionByDate: Map<java.time.LocalDate, SleepSession>,
        habitsByDate: Map<java.time.LocalDate, List<HabitLog>>
    ): CorrelationResult? {
        val pairs = sessionByDate.keys.mapNotNull { date ->
            val session = sessionByDate[date] ?: return@mapNotNull null
            val lateCaffeineMg = habitsByDate[date.minusDays(1)]  // habits logged day before sleep
                ?.filterIsInstance<HabitLog>()
                ?.filter { log ->
                    val entry = log.entry
                    entry is HabitEntry.Caffeine && entry.timeOfDay.hour >= 14
                }
                ?.sumOf { (it.entry as HabitEntry.Caffeine).mg }
                ?.toFloat() ?: 0f
            Pair(lateCaffeineMg, session.sleepOnsetMinutes.toFloat())
        }

        if (pairs.size < MIN_DATA_POINTS) return null

        val r = pearson(pairs)
        return CorrelationResult(
            factor = "Late Caffeine",
            unit = "mg after 2 PM",
            outcomeMetric = "sleep onset",
            outcomeUnit = "min",
            correlation = r,
            dataPoints = pairs.size,
            insight = buildCaffeineInsight(r, pairs)
        )
    }

    /** Alcohol units → sleep efficiency. */
    private fun computeAlcoholCorrelation(
        sessionByDate: Map<java.time.LocalDate, SleepSession>,
        habitsByDate: Map<java.time.LocalDate, List<HabitLog>>
    ): CorrelationResult? {
        val pairs = sessionByDate.keys.mapNotNull { date ->
            val session = sessionByDate[date] ?: return@mapNotNull null
            val units = habitsByDate[date.minusDays(1)]
                ?.sumOf { log ->
                    if (log.entry is HabitEntry.Alcohol) log.entry.units.toDouble() else 0.0
                }
                ?.toFloat() ?: 0f
            Pair(units, session.sleepEfficiency)
        }

        if (pairs.size < MIN_DATA_POINTS) return null

        val r = pearson(pairs)
        return CorrelationResult(
            factor = "Alcohol",
            unit = "units",
            outcomeMetric = "sleep efficiency",
            outcomeUnit = "%",
            correlation = r,
            dataPoints = pairs.size,
            insight = buildAlcoholInsight(r, pairs)
        )
    }

    /** Stress level → wake events. */
    private fun computeStressCorrelation(
        sessionByDate: Map<java.time.LocalDate, SleepSession>,
        habitsByDate: Map<java.time.LocalDate, List<HabitLog>>
    ): CorrelationResult? {
        val pairs = sessionByDate.keys.mapNotNull { date ->
            val session = sessionByDate[date] ?: return@mapNotNull null
            val stressEntry = habitsByDate[date.minusDays(1)]
                ?.mapNotNull { if (it.entry is HabitEntry.Stress) it.entry else null }
                ?.maxByOrNull { it.level }  // take peak stress for the day
            val stressLevel = stressEntry?.level?.toFloat() ?: return@mapNotNull null
            Pair(stressLevel, session.wakeEvents.toFloat())
        }

        if (pairs.size < MIN_DATA_POINTS) return null

        val r = pearson(pairs)
        return CorrelationResult(
            factor = "Stress",
            unit = "level (1-5)",
            outcomeMetric = "wake events",
            outcomeUnit = "count",
            correlation = r,
            dataPoints = pairs.size,
            insight = buildStressInsight(r, pairs)
        )
    }

    /** Exercise duration × intensity → sleep score. */
    private fun computeExerciseCorrelation(
        sessionByDate: Map<java.time.LocalDate, SleepSession>,
        habitsByDate: Map<java.time.LocalDate, List<HabitLog>>
    ): CorrelationResult? {
        val pairs = sessionByDate.keys.mapNotNull { date ->
            val session = sessionByDate[date] ?: return@mapNotNull null
            // Count exercise on the same day as sleep start OR the day before — an evening
            // workout logged the same calendar day (before bed) was previously dropped entirely,
            // since only date.minusDays(1) was queried despite this doc comment's stated intent.
            val exerciseMinutes = (habitsByDate[date].orEmpty() + habitsByDate[date.minusDays(1)].orEmpty())
                .sumOf { log ->
                    if (log.entry is HabitEntry.Exercise) log.entry.durationMinutes.toDouble() else 0.0
                }
                .toFloat()
            Pair(exerciseMinutes, session.sleepScore.toFloat())
        }

        if (pairs.size < MIN_DATA_POINTS) return null

        val r = pearson(pairs)
        return CorrelationResult(
            factor = "Exercise",
            unit = "minutes",
            outcomeMetric = "sleep score",
            outcomeUnit = "pts",
            correlation = r,
            dataPoints = pairs.size,
            insight = buildExerciseInsight(r, pairs)
        )
    }

    // ---- Pearson correlation ----

    private fun pearson(pairs: List<Pair<Float, Float>>): Float {
        val n = pairs.size
        val xs = pairs.map { it.first }
        val ys = pairs.map { it.second }

        val meanX = xs.average()
        val meanY = ys.average()

        val numerator = pairs.sumOf { (x, y) -> (x - meanX) * (y - meanY) }
        val denomX = sqrt(xs.sumOf { x -> (x - meanX) * (x - meanX) })
        val denomY = sqrt(ys.sumOf { y -> (y - meanY) * (y - meanY) })

        return if (denomX == 0.0 || denomY == 0.0) 0f
        else (numerator / (denomX * denomY)).toFloat()
    }

    // ---- Insight generators ----

    private fun buildCaffeineInsight(r: Float, pairs: List<Pair<Float, Float>>): String {
        val avgWithCaffeine = pairs.filter { it.first > 0 }.map { it.second }.average()
        val avgWithout = pairs.filter { it.first == 0f }.map { it.second }.average()
        return when {
            r > 0.3f && avgWithCaffeine.isFinite() && avgWithout.isFinite() ->
                "Caffeine after 2 PM is associated with ${(avgWithCaffeine - avgWithout).toInt()} min longer sleep onset for you."
            r > 0.15f ->
                "There's a mild link between late caffeine and longer sleep onset in your data."
            else ->
                "Late caffeine doesn't appear to significantly affect your sleep onset time."
        }
    }

    private fun buildAlcoholInsight(r: Float, pairs: List<Pair<Float, Float>>): String {
        return when {
            r < -0.3f ->
                "Alcohol is associated with noticeably lower sleep efficiency in your data."
            r < -0.15f ->
                "There's a mild negative association between alcohol and your sleep efficiency."
            else ->
                "Alcohol doesn't show a strong effect on your sleep efficiency in your current data."
        }
    }

    private fun buildStressInsight(r: Float, pairs: List<Pair<Float, Float>>): String {
        return when {
            r > 0.3f ->
                "Higher stress days are linked to more wake events in your data. Wind-down practices may help."
            r > 0.15f ->
                "There's a mild link between high-stress days and restless nights for you."
            else ->
                "Your sleep doesn't appear to be strongly affected by stress levels in your data."
        }
    }

    private fun buildExerciseInsight(r: Float, pairs: List<Pair<Float, Float>>): String {
        return when {
            r > 0.3f ->
                "Days with exercise are associated with higher sleep scores for you. Keep it up!"
            r > 0.15f ->
                "Exercise shows a mild positive link with your sleep quality."
            else ->
                "Exercise timing and duration haven't shown a strong pattern with your sleep scores yet."
        }
    }
}

/** Result for a single lifestyle → sleep metric correlation. */
data class CorrelationResult(
    val factor: String,
    val unit: String,
    val outcomeMetric: String,
    val outcomeUnit: String,
    /** Pearson r (-1.0 to 1.0). */
    val correlation: Float,
    val dataPoints: Int,
    val insight: String
) {
    /** How settled this read is, given how many nights it was computed from. */
    val confidence: CorrelationConfidence get() = CorrelationConfidence.from(dataPoints)
    val strength: CorrelationStrength get() = when {
        kotlin.math.abs(correlation) < 0.15f -> CorrelationStrength.NONE
        kotlin.math.abs(correlation) < 0.3f -> CorrelationStrength.MILD
        kotlin.math.abs(correlation) < 0.5f -> CorrelationStrength.MODERATE
        else -> CorrelationStrength.STRONG
    }
    val isPositive: Boolean get() = correlation >= 0
}

enum class CorrelationStrength(val displayName: String) {
    NONE("No link"),
    MILD("Mild link"),
    MODERATE("Moderate link"),
    STRONG("Strong link")
}

/**
 * Statistical confidence of a correlation result, scaled by sample size. Distinct from
 * [CorrelationStrength] (which describes the magnitude of the effect): a "STRONG" r computed
 * from 7 nights is still an early, tentative read, while the same r from 30+ nights is far
 * more settled. Small-sample Pearson coefficients carry very wide confidence intervals, so
 * this qualifier exists so the UI never presents a bare-minimum finding as a settled one.
 */
enum class CorrelationConfidence(val displayName: String, val minNights: Int) {
    /** 7-13 nights — above the data floor but statistically underpowered; may flip with a few more nights. */
    LOW("Low confidence", 7),
    /** 14-29 nights — two to four weeks of paired data; pattern is visible but not yet stable. */
    MEDIUM("Medium confidence", 14),
    /** 30+ nights — a month or more; close to the point where small-to-moderate effects become distinguishable from noise. */
    HIGH("High confidence", 30);

    companion object {
        fun from(dataPoints: Int): CorrelationConfidence = when {
            dataPoints < MEDIUM.minNights -> LOW
            dataPoints < HIGH.minNights -> MEDIUM
            else -> HIGH
        }
    }
}

/** All four lifestyle correlations bundled. */
data class CorrelationReport(
    val caffeineToOnset: CorrelationResult?,       // null if insufficient data
    val alcoholToEfficiency: CorrelationResult?,
    val stressToWakes: CorrelationResult?,
    val exerciseToScore: CorrelationResult?
) {
    val hasAnyData: Boolean get() =
        caffeineToOnset != null || alcoholToEfficiency != null ||
        stressToWakes != null || exerciseToScore != null

    val availableCorrelations: List<CorrelationResult> get() =
        listOfNotNull(caffeineToOnset, alcoholToEfficiency, stressToWakes, exerciseToScore)
}
