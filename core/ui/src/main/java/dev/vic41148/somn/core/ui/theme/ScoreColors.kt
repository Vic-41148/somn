package dev.vic41148.somn.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * A sleep-score band. Coarse on purpose: a ring or list row cannot communicate a 100-step
 * gradient, and this is where SleepScoreRing, HistoryScreen and any future caller agree on
 * the thresholds so colour and wording can never drift apart again.
 */
enum class ScoreTier(val label: String, val minScore: Int) {
    GREAT("Great", 80),
    GOOD("Good", 60),
    FAIR("Fair", 40),
    POOR("Poor", 0);

    companion object {
        fun of(score: Int): ScoreTier = when {
            score >= GREAT.minScore -> GREAT
            score >= GOOD.minScore -> GOOD
            score >= FAIR.minScore -> FAIR
            else -> POOR
        }
    }
}

/** Fixed 0-100 score ramp, independent of dynamic colour so scores stay comparable across themes. */
fun scoreColor(score: Int): Color = when (ScoreTier.of(score)) {
    ScoreTier.GREAT -> ScoreGreat
    ScoreTier.GOOD -> ScoreGood
    ScoreTier.FAIR -> ScoreFair
    ScoreTier.POOR -> ScorePoor
}