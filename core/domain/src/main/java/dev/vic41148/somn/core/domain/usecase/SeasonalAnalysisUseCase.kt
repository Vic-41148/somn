package dev.vic41148.somn.core.domain.usecase

import dev.vic41148.somn.core.domain.model.HemisphereOverride
import dev.vic41148.somn.core.domain.model.MIN_SESSIONS_PER_SEASON
import dev.vic41148.somn.core.domain.model.Season
import dev.vic41148.somn.core.domain.model.SeasonalAnalysis
import dev.vic41148.somn.core.domain.model.SeasonalPattern
import dev.vic41148.somn.core.domain.model.SeasonalTrend
import dev.vic41148.somn.core.domain.model.SeasonalTrendType
import dev.vic41148.somn.core.domain.model.SleepSession
import java.time.Instant
import java.time.ZoneId

/**
 * Detects seasonal patterns in sleep duration and quality.
 *
 * Method:
 *   1. Map each session to a [Season] based on its start month and hemisphere
 *   2. Aggregate mean duration, score, and efficiency per season
 *   3. Compare the current season against the user's annual average
 *   4. Classify trend: WINTER_HYPERSOMNIA / SUMMER_INSOMNIA / STABLE / INSUFFICIENT
 *
 * Hemisphere determination:
 *   - The use case uses the UTC offset of the session's stored timezone as a proxy
 *   - Positive offsets (east of UTC) are assumed Northern Hemisphere by default
 *   - Negative offsets beyond -3h are assumed Southern Hemisphere
 *   - This heuristic is imperfect. A user settings override ([HemisphereOverride]) pins it
 *
 * Research basis §2.8: SAD affects 1.4–9.9% of the population. Subsyndromal SAD
 * ("winter blues") affects ~14.3% (NIMH). Both winter and summer patterns are circadian
 * disruptions, not behavioral problems. Light therapy timing is the evidence-based intervention.
 */
class SeasonalAnalysisUseCase {

    /**
     * @param sessions All completed sleep sessions (no date range limit — more = better).
     * @param deviceTimezoneId The current device timezone for hemisphere detection.
     * @param hemisphereOverride User override. [HemisphereOverride.AUTO] keeps the UTC-offset heuristic.
     */
    fun analyze(
        sessions: List<SleepSession>,
        deviceTimezoneId: String = ZoneId.systemDefault().id,
        hemisphereOverride: HemisphereOverride = HemisphereOverride.AUTO
    ): SeasonalAnalysis {
        val northernHemisphere = when (hemisphereOverride) {
            HemisphereOverride.AUTO -> isNorthernHemisphere(deviceTimezoneId)
            HemisphereOverride.NORTHERN -> true
            HemisphereOverride.SOUTHERN -> false
        }
        val currentSeason = currentSeason(deviceTimezoneId, northernHemisphere)

        val completed = sessions.filter { it.isCompleted && it.endTimeMillis > 0 }

        if (completed.isEmpty()) {
            return SeasonalAnalysis(
                currentSeason    = currentSeason,
                seasonalPatterns = emptyList(),
                currentTrend     = null,
                insight          = null
            )
        }

        // Group sessions by season
        val bySeasonRaw: Map<Season, List<SleepSession>> = completed.groupBy { session ->
            val zone  = runCatching { ZoneId.of(session.timezoneId) }.getOrDefault(ZoneId.systemDefault())
            val month = Instant.ofEpochMilli(session.startTimeMillis).atZone(zone).monthValue
            Season.fromMonth(month, northernHemisphere)
        }

        // Build patterns only for seasons with sufficient data
        val seasonalPatterns: List<SeasonalPattern> = bySeasonRaw
            .filter { (_, sessions) -> sessions.size >= MIN_SESSIONS_PER_SEASON }
            .map { (season, sessions) ->
                SeasonalPattern(
                    season               = season,
                    avgDurationMinutes   = sessions.map { it.sleepDurationMinutes.toFloat() }.average().toFloat(),
                    avgScore             = sessions.map { it.sleepScore.toFloat() }.average().toFloat(),
                    avgEfficiency        = sessions.map { it.sleepEfficiency }.average().toFloat(),
                    sessionCount         = sessions.size
                )
            }
            .sortedBy { pattern ->
                // Sort: Spring, Summer, Autumn, Winter
                Season.values().indexOf(pattern.season)
            }

        val currentTrend = computeTrend(currentSeason, seasonalPatterns)

        return SeasonalAnalysis(
            currentSeason    = currentSeason,
            seasonalPatterns = seasonalPatterns,
            currentTrend     = currentTrend,
            insight          = buildInsight(currentSeason, currentTrend, seasonalPatterns.size)
        )
    }

    // ---- Trend detection ----

    private fun computeTrend(
        currentSeason: Season,
        patterns: List<SeasonalPattern>
    ): SeasonalTrend? {
        if (patterns.size < 2) return null  // need at least current + one other for comparison

        val currentPattern = patterns.find { it.season == currentSeason } ?: return null
        val annualAvgDuration = patterns.map { it.avgDurationMinutes }.average().toFloat()
        val delta = currentPattern.avgDurationMinutes - annualAvgDuration

        // Thresholds: ±30 min deviation from annual average is meaningful
        val type = when {
            currentSeason == Season.WINTER && delta > 30f  -> SeasonalTrendType.WINTER_HYPERSOMNIA
            currentSeason == Season.SUMMER && delta < -30f -> SeasonalTrendType.SUMMER_INSOMNIA
            else                                           -> SeasonalTrendType.STABLE
        }

        return SeasonalTrend(
            type                 = type,
            durationDeltaMinutes = delta,
            insight              = buildTrendInsight(type, delta, currentSeason)
        )
    }

    private fun buildTrendInsight(type: SeasonalTrendType, delta: Float, season: Season): String {
        val absDelta = kotlin.math.abs(delta).toInt()
        return when (type) {
            SeasonalTrendType.WINTER_HYPERSOMNIA ->
                "You're sleeping about ${absDelta}m longer this winter than your annual average. " +
                "This is consistent with winter-pattern circadian change — shorter days reduce " +
                "morning light exposure, causing melatonin overproduction and earlier sleep pressure. " +
                "Morning light therapy between 7–9 AM can help anchor your rhythm."
            SeasonalTrendType.SUMMER_INSOMNIA ->
                "You're sleeping about ${absDelta}m less this summer than your annual average. " +
                "Extended daylight suppresses melatonin and delays sleep onset. " +
                "Blackout curtains and keeping your bedroom cool (18–20°C) can help. " +
                "Aim for consistent wind-down at the same time regardless of outside brightness."
            SeasonalTrendType.STABLE ->
                "Your sleep duration is consistent across seasons — no significant seasonal drift detected."
            SeasonalTrendType.INSUFFICIENT ->
                "Not enough cross-season data yet to detect a seasonal pattern."
        }
    }

    private fun buildInsight(
        currentSeason: Season,
        trend: SeasonalTrend?,
        patternCount: Int
    ): String? {
        if (patternCount == 0) return null
        if (trend == null) {
            return "Tracking across ${Season.values().size} seasons. Currently ${patternCount} season(s) " +
                   "have enough data. Keep tracking through ${currentSeason.displayName} " +
                   "to build your seasonal baseline."
        }
        return trend.insight
    }

    // ---- Hemisphere & season detection ----

    /**
     * Determine the current season from the device timezone.
     */
    private fun currentSeason(timezoneId: String, northernHemisphere: Boolean): Season {
        val zone  = runCatching { ZoneId.of(timezoneId) }.getOrDefault(ZoneId.systemDefault())
        val month = java.time.LocalDate.now(zone).monthValue
        return Season.fromMonth(month, northernHemisphere)
    }

    /**
     * Heuristic: UTC offset < -3 hours is assumed Southern Hemisphere (much of South America,
     * South Africa, Australia, New Zealand). UTC offsets ≥ -3h are assumed Northern Hemisphere.
     *
     * This covers ~90% of users correctly. [HemisphereOverride] in settings pins the rest.
     */
    private fun isNorthernHemisphere(timezoneId: String): Boolean {
        return try {
            val zone   = ZoneId.of(timezoneId)
            val offset = zone.rules.getOffset(java.time.Instant.now()).totalSeconds
            // Rough heuristic: offsets beyond -3h are typically southern
            offset >= -3 * 3600
        } catch (e: Exception) {
            true  // default to Northern Hemisphere
        }
    }
}
