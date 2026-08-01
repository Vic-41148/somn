package dev.vic41148.somn.core.domain.usecase

import dev.vic41148.somn.core.domain.model.AdjustmentCategory
import dev.vic41148.somn.core.domain.model.AdjustmentReason
import dev.vic41148.somn.core.domain.model.Chronotype
import dev.vic41148.somn.core.domain.model.LifeStage
import dev.vic41148.somn.core.domain.model.MenstrualCyclePhase
import dev.vic41148.somn.core.domain.model.SleepScore
import dev.vic41148.somn.core.domain.model.SleepScoreAdjustment
import dev.vic41148.somn.core.domain.model.SleepSession
import dev.vic41148.somn.core.domain.model.UserProfile
import kotlin.math.max
import kotlin.math.min

/**
 * Calculates a composite sleep score (0-100) based on 5 weighted factors,
 * with biological adjustments based on the user's profile.
 *
 * Score = (Duration × 0.25) + (Efficiency × 0.20) + (DeepSleep% × 0.20)
 *       + (Consistency × 0.20) + (WakeEvents × 0.15)
 *
 * The score is then adjusted upward based on biological factors that the
 * user cannot control (menstrual phase, age-appropriate expectations, etc.).
 * This produces both a raw score and an adjusted score with transparent explanations.
 */
class CalculateSleepScoreUseCase {

    companion object {
        // Population baseline defaults (used when no profile exists)
        private const val DEFAULT_TARGET_HOURS = 8.0f
        private const val DEFAULT_DEEP_TARGET = 20.0f
        private const val DEFAULT_EFFICIENCY_TARGET = 85.0f
    }

    /**
     * Calculate score without profile — backward compatible with existing usage.
     */
    operator fun invoke(
        session: SleepSession,
        targetHours: Float = DEFAULT_TARGET_HOURS,
        averageBedtimeVarianceMinutes: Float = 30f
    ): SleepScore {
        val durationScore = calculateDurationScore(session.sleepDurationMinutes, targetHours)
        val efficiencyScore = calculateEfficiencyScore(session.sleepEfficiency)
        val deepSleepScore = calculateDeepSleepScore(session.deepSleepPercent, DEFAULT_DEEP_TARGET)
        val consistencyScore = calculateConsistencyScore(averageBedtimeVarianceMinutes)
        val wakeEventsScore = calculateWakeEventsScore(session.wakeEvents)

        val totalScore = (
            durationScore * 0.25f +
            efficiencyScore * 0.20f +
            deepSleepScore * 0.20f +
            consistencyScore * 0.20f +
            wakeEventsScore * 0.15f
        ).toInt().coerceIn(0, 100)

        val explanation = generateExplanation(
            totalScore, durationScore, efficiencyScore,
            deepSleepScore, consistencyScore, wakeEventsScore, session
        )

        return SleepScore(
            totalScore = totalScore,
            durationScore = durationScore,
            efficiencyScore = efficiencyScore,
            deepSleepScore = deepSleepScore,
            consistencyScore = consistencyScore,
            wakeEventsScore = wakeEventsScore,
            explanation = explanation
        )
    }

    /**
     * Calculate score WITH biological profile — produces adjusted score with explanations.
     */
    fun calculateWithProfile(
        session: SleepSession,
        profile: UserProfile,
        cyclePhase: MenstrualCyclePhase? = null,
        averageBedtimeVarianceMinutes: Float = 30f
    ): SleepScoreAdjustment {
        // Use profile-calibrated targets
        val targetHours = profile.targetSleepHours
        val deepTarget = profile.deepSleepTargetPercent

        val durationScore = calculateDurationScore(session.sleepDurationMinutes, targetHours)
        val efficiencyScore = calculateEfficiencyScore(
            session.sleepEfficiency,
            adjustEfficiencyTarget(profile)
        )
        val deepSleepScore = calculateDeepSleepScore(session.deepSleepPercent, deepTarget)
        val consistencyScore = calculateConsistencyScore(
            averageBedtimeVarianceMinutes,
            profile.neurodivergentProfile.adhdMode
        )
        val wakeEventsScore = calculateWakeEventsScore(
            session.wakeEvents,
            adjustWakeExpectation(profile)
        )

        val rawScore = (
            durationScore * 0.25f +
            efficiencyScore * 0.20f +
            deepSleepScore * 0.20f +
            consistencyScore * 0.20f +
            wakeEventsScore * 0.15f
        ).toInt().coerceIn(0, 100)

        // Calculate biological adjustments
        val adjustments = mutableListOf<AdjustmentReason>()

        // Age calibration adjustment
        profile.age?.let { age ->
            if (age >= 55 && session.deepSleepPercent in (deepTarget * 0.7f)..(deepTarget * 1.3f)) {
                adjustments.add(
                    AdjustmentReason(
                        factor = "Age-calibrated deep sleep",
                        adjustment = 3,
                        explanation = "Your deep sleep of ${session.deepSleepPercent.toInt()}% is healthy for your age group.",
                        category = AdjustmentCategory.AGE_CALIBRATED
                    )
                )
            }
            if (age in 13..18 && session.sleepDurationMinutes >= (8.5 * 60).toInt()) {
                adjustments.add(
                    AdjustmentReason(
                        factor = "Adolescent sleep need met",
                        adjustment = 2,
                        explanation = "You met the 8.5+ hour recommendation for your age.",
                        category = AdjustmentCategory.AGE_CALIBRATED
                    )
                )
            }
        }

        // Menstrual cycle adjustment
        cyclePhase?.let { phase ->
            if (phase.scoreAdjustment > 0) {
                adjustments.add(
                    AdjustmentReason(
                        factor = "${phase.displayName} phase",
                        adjustment = phase.scoreAdjustment,
                        explanation = phase.sleepImpact,
                        category = AdjustmentCategory.HORMONAL
                    )
                )
            }
        }

        // Pregnancy adjustment
        if (profile.lifeStage == LifeStage.PREGNANT) {
            val trimesterAdjustment = when (profile.pregnancyTrimester) {
                1 -> 5
                2 -> 2
                3 -> 10  // Third trimester: 46-79% of women report poor sleep
                else -> 3
            }
            adjustments.add(
                AdjustmentReason(
                    factor = "Pregnancy (trimester ${profile.pregnancyTrimester ?: "?"})",
                    adjustment = trimesterAdjustment,
                    explanation = when (profile.pregnancyTrimester) {
                        1 -> "First trimester fatigue and nausea commonly fragment sleep."
                        3 -> "Third trimester sleep disruption is experienced by the majority of women."
                        else -> "Your score accounts for pregnancy-related sleep changes."
                    },
                    category = AdjustmentCategory.HORMONAL
                )
            )
        }

        // Perimenopause/menopause adjustment
        if (profile.lifeStage in listOf(LifeStage.PERIMENOPAUSE, LifeStage.MENOPAUSE)) {
            if (session.wakeEvents >= 3) {
                adjustments.add(
                    AdjustmentReason(
                        factor = "Menopausal sleep disruption",
                        adjustment = 6,
                        explanation = "Frequent awakenings are common during ${profile.lifeStage.displayName.lowercase()} — this is hormonal, not a behavioral problem.",
                        category = AdjustmentCategory.HORMONAL
                    )
                )
            }
        }

        // Postpartum adjustment
        if (profile.lifeStage == LifeStage.POSTPARTUM) {
            adjustments.add(
                AdjustmentReason(
                    factor = "Postpartum recovery",
                    adjustment = 8,
                    explanation = "Sleep fragmentation is expected during postpartum. Focus on your longest uninterrupted stretch.",
                    category = AdjustmentCategory.HORMONAL
                )
            )
        }

        // ADHD adjustment
        if (profile.neurodivergentProfile.adhdMode) {
            if (profile.chronotype in listOf(Chronotype.MODERATE_EVENING, Chronotype.DEFINITE_EVENING)) {
                adjustments.add(
                    AdjustmentReason(
                        factor = "ADHD chronotype",
                        adjustment = 3,
                        explanation = "ADHD is linked to a delayed circadian phase. Your evening pattern is neurological, not behavioral.",
                        category = AdjustmentCategory.NEURODIVERGENT
                    )
                )
            }
        }

        val totalAdjustment = adjustments.sumOf { it.adjustment }.coerceAtMost(20) // Cap at +20
        val adjustedScore = (rawScore + totalAdjustment).coerceIn(0, 100)

        // Generate explanation
        val explanation = buildAdjustedExplanation(rawScore, adjustedScore, adjustments, session)

        return SleepScoreAdjustment(
            rawScore = rawScore,
            adjustedScore = adjustedScore,
            adjustmentReasons = adjustments,
            explanation = explanation
        )
    }

    // ---- Sub-score calculations ----

    private fun calculateDurationScore(actualMinutes: Int, targetHours: Float): Int {
        val targetMinutes = targetHours * 60
        val ratio = actualMinutes / targetMinutes
        // The oversleep branch (ratio > 1.1f) used to be unreachable — `ratio >= 0.8f` is checked
        // first and matches everything oversleeping too, so a session slept way past its target
        // (e.g. ratio 2.0) hit `80 + (ratio - 0.8) * 200`, coerced straight to a perfect 100
        // instead of the intended oversleep penalty. Oversleep must be checked before the >= 0.8f
        // catch-all.
        return when {
            ratio >= 0.9f && ratio <= 1.1f -> 100
            ratio > 1.1f -> max(60, (100 - (ratio - 1.1f) * 100).toInt())
            ratio >= 0.8f -> (80 + (ratio - 0.8f) * 200).toInt()
            ratio >= 0.6f -> (40 + (ratio - 0.6f) * 200).toInt()
            else -> (ratio * 66).toInt()
        }.coerceIn(0, 100)
    }

    private fun calculateEfficiencyScore(
        efficiency: Float,
        target: Float = DEFAULT_EFFICIENCY_TARGET
    ): Int {
        return when {
            efficiency >= 90f -> 100
            efficiency >= 80f -> (80 + (efficiency - 80f) * 2).toInt()
            efficiency >= 70f -> (50 + (efficiency - 70f) * 3).toInt()
            else -> (efficiency * 0.7f).toInt()
        }.coerceIn(0, 100)
    }

    private fun calculateDeepSleepScore(
        deepPercent: Float,
        targetPercent: Float = DEFAULT_DEEP_TARGET
    ): Int {
        // Optimal band: targetPercent ± 5%
        val low = targetPercent - 5f
        val high = targetPercent + 5f
        return when {
            deepPercent in low..high -> 100
            deepPercent in (low - 5f)..low -> (70 + (deepPercent - (low - 5f)) * 6).toInt()
            deepPercent in high..(high + 10f) -> (70 + ((high + 10f) - deepPercent) * 3).toInt()
            deepPercent < (low - 5f) -> (deepPercent * 7).toInt()
            else -> 50
        }.coerceIn(0, 100)
    }

    private fun calculateConsistencyScore(
        varianceMinutes: Float,
        adhdMode: Boolean = false
    ): Int {
        // ADHD mode: more lenient consistency expectations (benchmark against personal pattern)
        val leniencyFactor = if (adhdMode) 1.5f else 1.0f

        return when {
            varianceMinutes < 15f * leniencyFactor ->
                95 + ((15 * leniencyFactor - varianceMinutes) / 3).toInt()
            varianceMinutes < 30f * leniencyFactor ->
                (80 + (30 * leniencyFactor - varianceMinutes)).toInt()
            varianceMinutes < 45f * leniencyFactor ->
                (60 + (45 * leniencyFactor - varianceMinutes) * 1.33f).toInt()
            varianceMinutes < 60f * leniencyFactor ->
                (40 + (60 * leniencyFactor - varianceMinutes) * 1.33f).toInt()
            else -> max(0, (40 - (varianceMinutes - 60 * leniencyFactor)).toInt())
        }.coerceIn(0, 100)
    }

    private fun calculateWakeEventsScore(
        wakeCount: Int,
        expectedWakes: Int = 0
    ): Int {
        val adjustedCount = max(0, wakeCount - expectedWakes)
        return when (adjustedCount) {
            0 -> 100
            1 -> 90
            2 -> 75
            3 -> 60
            4 -> 45
            5 -> 30
            else -> max(0, 30 - (adjustedCount - 5) * 10)
        }
    }

    // ---- Adjustment helpers ----

    /**
     * Adjust efficiency target based on life stage.
     * Pregnancy (trimester 3) and postpartum have lower realistic efficiency targets.
     */
    private fun adjustEfficiencyTarget(profile: UserProfile): Float {
        return when (profile.lifeStage) {
            LifeStage.PREGNANT -> when (profile.pregnancyTrimester) {
                3 -> 70f
                1 -> 78f
                else -> 82f
            }
            LifeStage.POSTPARTUM -> 65f
            LifeStage.PERIMENOPAUSE, LifeStage.MENOPAUSE -> 78f
            else -> DEFAULT_EFFICIENCY_TARGET
        }
    }

    /**
     * Adjust expected wake events based on life stage and age.
     * Older adults and menopausal women naturally wake more — don't penalise normal biology.
     */
    private fun adjustWakeExpectation(profile: UserProfile): Int {
        val ageAdjustment = when (profile.age) {
            null -> 0
            in 0..35 -> 0
            in 36..55 -> 1
            in 56..75 -> 2
            else -> 3
        }
        val stageAdjustment = when (profile.lifeStage) {
            LifeStage.PREGNANT -> if (profile.pregnancyTrimester == 3) 3 else 1
            LifeStage.POSTPARTUM -> 4
            LifeStage.PERIMENOPAUSE, LifeStage.MENOPAUSE -> 2
            else -> 0
        }
        return max(ageAdjustment, stageAdjustment)
    }

    // ---- Explanation generation ----

    private fun generateExplanation(
        total: Int, duration: Int, efficiency: Int,
        deep: Int, consistency: Int, wake: Int,
        session: SleepSession
    ): String {
        // Find the weakest factor
        val scores = mapOf(
            "sleep duration" to duration,
            "sleep efficiency" to efficiency,
            "deep sleep" to deep,
            "schedule consistency" to consistency,
            "wake events" to wake
        )
        val weakest = scores.minByOrNull { it.value }

        return when {
            total >= 80 -> "Great night! All your sleep metrics look healthy."
            total >= 60 -> "Decent sleep. Your ${weakest?.key} could use some improvement."
            total >= 40 -> "Below average night. Your ${weakest?.key} was the biggest factor — consider adjusting your routine."
            else -> "Rough night. Your ${weakest?.key} was significantly below your normal baseline."
        }
    }

    private fun buildAdjustedExplanation(
        rawScore: Int,
        adjustedScore: Int,
        adjustments: List<AdjustmentReason>,
        session: SleepSession
    ): String {
        if (adjustments.isEmpty()) {
            return when {
                rawScore >= 80 -> "Great night! All your sleep metrics look healthy."
                rawScore >= 60 -> "Decent sleep overall."
                rawScore >= 40 -> "Below average night — consider adjusting your routine."
                else -> "Rough night. Try to prioritise rest tonight."
            }
        }

        val adjustmentSummary = adjustments.joinToString("; ") { it.factor }
        return when {
            adjustedScore >= 80 -> "You scored $rawScore, adjusted to $adjustedScore. Accounting for: $adjustmentSummary. Great sleep given your circumstances!"
            adjustedScore >= 60 -> "You scored $rawScore, adjusted to $adjustedScore ($adjustmentSummary). Decent sleep — your body is doing its best."
            else -> "You scored $rawScore, adjusted to $adjustedScore ($adjustmentSummary). Consider prioritizing rest."
        }
    }
}
