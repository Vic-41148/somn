package dev.vic41148.somn.core.domain.model

/**
 * Represents a sleep score with transparent biological adjustments.
 *
 * Research doc §Part 5: "You scored 71. You're in your luteal phase and logged moderate stress.
 * Your adjusted score is 84. Here's why." — No competitor explains scores. This builds trust.
 */
data class SleepScoreAdjustment(
    val rawScore: Int,
    val adjustedScore: Int,
    val adjustmentReasons: List<AdjustmentReason>,
    val explanation: String
) {
    /** Whether the score was adjusted at all. */
    val hasAdjustments: Boolean get() = adjustmentReasons.isNotEmpty()

    /** Total adjustment applied. */
    val totalAdjustment: Int get() = adjustedScore - rawScore
}

/**
 * A single reason for a score adjustment.
 */
data class AdjustmentReason(
    val factor: String,
    val adjustment: Int,
    val explanation: String,
    val category: AdjustmentCategory
)

/**
 * Categories of score adjustment for UI grouping.
 */
enum class AdjustmentCategory {
    HORMONAL,        // Menstrual cycle, pregnancy, menopause
    AGE_CALIBRATED,  // Age-appropriate expectations
    NEURODIVERGENT,  // ADHD/ASD adjusted expectations
    SEASONAL,        // Seasonal pattern adjustment
    LIFESTYLE        // Stress, medication timing
}
