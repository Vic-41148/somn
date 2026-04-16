package dev.vic41148.somn.core.domain.model

/**
 * A personalised sleep debt recovery plan.
 *
 * Based on research doc §3.6: recovery should be gradual — sudden large extensions
 * disrupt circadian rhythm. Target ≤90 additional minutes per night, distributed
 * across enough nights to fully clear the debt.
 */
data class RecoveryPlan(
    /** Extra minutes to sleep per night (≤90). */
    val additionalMinutesPerNight: Int,
    /** How many minutes earlier to go to bed (negative = later, unusual). */
    val suggestedBedtimeShiftMinutes: Int,
    /** Estimated nights to fully clear the debt at this rate. */
    val estimatedRecoveryDays: Int,
    /** Human-readable explanation for the UI. */
    val explanation: String
) {
    companion object {
        /** Represents no recovery needed (no debt). */
        val NONE = RecoveryPlan(
            additionalMinutesPerNight = 0,
            suggestedBedtimeShiftMinutes = 0,
            estimatedRecoveryDays = 0,
            explanation = "You're on track — no sleep debt to recover."
        )
    }
}
