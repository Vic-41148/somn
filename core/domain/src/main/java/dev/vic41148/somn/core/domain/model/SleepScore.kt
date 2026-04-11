package dev.vic41148.somn.core.domain.model

/**
 * Composite sleep score with sub-component breakdown.
 */
data class SleepScore(
    val totalScore: Int,                // 0-100
    val durationScore: Int,             // 0-100
    val efficiencyScore: Int,           // 0-100
    val deepSleepScore: Int,            // 0-100
    val consistencyScore: Int,          // 0-100
    val wakeEventsScore: Int,           // 0-100
    val explanation: String             // Plain-language summary
) {
    val rating: ScoreRating get() = when {
        totalScore >= 80 -> ScoreRating.GREAT
        totalScore >= 60 -> ScoreRating.GOOD
        totalScore >= 40 -> ScoreRating.FAIR
        else -> ScoreRating.POOR
    }
}

enum class ScoreRating(val label: String, val emoji: String) {
    GREAT("Great", "🌟"),
    GOOD("Good", "😊"),
    FAIR("Fair", "😐"),
    POOR("Poor", "😴")
}
