package dev.vic41148.somn.core.domain.model

/**
 * Seasonal sleep pattern analysis. Detects winter hypersomnia and summer insomnia —
 * manifestations of Seasonal Affective Disorder and circadian disruption caused by
 * day-length changes.
 *
 * Research basis §2.8: SAD affects 1.4–9.9% of the US population (higher at northern
 * latitudes), with subsyndromal SAD ("winter blues") affecting ~14.3%. Both winter-pattern
 * (melatonin overproduction → hypersomnia) and summer-pattern (reduced melatonin →
 * insomnia) are fundamentally circadian disruptions, not behavioral problems (NIMH).
 */
data class SeasonalAnalysis(
    /** Season at time of analysis. */
    val currentSeason: Season,
    /**
     * Aggregated stats per season, for seasons with sufficient data.
     * May be empty if no historical cross-season data.
     */
    val seasonalPatterns: List<SeasonalPattern>,
    /**
     * Current seasonal trend relative to the user's annual average.
     * Null if fewer than 2 seasons have sufficient data.
     */
    val currentTrend: SeasonalTrend?,
    /** Education or contextual insight, or null if no data. */
    val insight: String?
) {
    /** Whether there's enough data across seasons to draw conclusions. */
    val hasMultiSeasonData: Boolean get() = seasonalPatterns.size >= 2
}

/**
 * Aggregated sleep metrics for a single calendar season.
 */
data class SeasonalPattern(
    val season: Season,
    val avgDurationMinutes: Float,
    val avgScore: Float,
    val avgEfficiency: Float,
    val sessionCount: Int
) {
    val avgDurationHours: Float get() = avgDurationMinutes / 60f
}

/**
 * Detected seasonal trend relative to the user's annual baseline.
 * Includes the magnitude of deviation and a user-facing insight.
 */
data class SeasonalTrend(
    val type: SeasonalTrendType,
    /** How much current season avg duration differs from annual avg, in minutes. */
    val durationDeltaMinutes: Float,
    /** Plain-language explanation with science context. */
    val insight: String
)

/**
 * Seasons mapped to months (Northern Hemisphere default).
 * SeasonalAnalysisUseCase accounts for Southern Hemisphere via timezone-based inversion.
 */
enum class Season(val displayName: String) {
    SPRING("Spring"),
    SUMMER("Summer"),
    AUTUMN("Autumn"),
    WINTER("Winter");

    companion object {
        /**
         * Returns the season for a given month (1–12).
         * @param month 1-indexed month.
         * @param northernHemisphere If false, seasons are inverted.
         */
        fun fromMonth(month: Int, northernHemisphere: Boolean = true): Season {
            val northern = when (month) {
                12, 1, 2  -> WINTER
                3, 4, 5   -> SPRING
                6, 7, 8   -> SUMMER
                9, 10, 11 -> AUTUMN
                else      -> SPRING
            }
            return if (northernHemisphere) northern else when (northern) {
                WINTER -> SUMMER
                SUMMER -> WINTER
                SPRING -> AUTUMN
                AUTUMN -> SPRING
            }
        }
    }
}

/**
 * Classification of the detected seasonal sleep trend.
 */
enum class SeasonalTrendType(val displayName: String) {
    /**
     * Sleeping noticeably longer in winter than annual average.
     * Circadian mechanism: melatonin overproduction from reduced daylight.
     */
    WINTER_HYPERSOMNIA("Winter Hypersomnia"),
    /**
     * Sleeping shorter or worse in summer.
     * Circadian mechanism: reduced melatonin from extended light exposure + heat.
     */
    SUMMER_INSOMNIA("Summer Insomnia"),
    /** No meaningful seasonal deviation detected. */
    STABLE("Stable"),
    /** Insufficient cross-season data. */
    INSUFFICIENT("Insufficient Data")
}

/** Minimum qualifying sessions per season for statistical validity. */
const val MIN_SESSIONS_PER_SEASON = 7

/**
 * User override for which hemisphere the seasons are computed against.
 *
 * [AUTO] keeps the [dev.vic41148.somn.core.domain.usecase.SeasonalAnalysisUseCase] UTC-offset
 * heuristic (positive offsets assumed Northern, beyond -3h assumed Southern). Selecting
 * [NORTHERN] or [SOUTHERN] pins the season mapping so users near the equator or on the wrong
 * side of a timezone boundary can correct a misdetected season.
 */
enum class HemisphereOverride(val displayName: String) {
    AUTO("Auto"),
    NORTHERN("Northern"),
    SOUTHERN("Southern")
}
