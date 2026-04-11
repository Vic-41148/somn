package dev.vic41148.somn.core.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Menstrual cycle phase determination based on cycle day.
 *
 * Research doc §2.2: Estrogen and progesterone fluctuate across the ~28-day cycle
 * and directly alter sleep architecture at a hormonal level. Phase-by-phase sleep
 * effects are well-documented (Sleep and Biological Rhythms, 2025).
 *
 * Key implication: symptom frequency has a stronger impact on sleep than cycle phase
 * alone (Frontiers in Physiology, 2025) — the app must track both.
 */
enum class MenstrualCyclePhase(
    val displayName: String,
    val sleepImpact: String,
    val scoreAdjustment: Int
) {
    MENSTRUAL(
        displayName = "Menstrual",
        sleepImpact = "Cramps and pain may fragment sleep. This is biological, not a sleep problem.",
        scoreAdjustment = 8
    ),
    FOLLICULAR(
        displayName = "Follicular",
        sleepImpact = "Best sleep window of the cycle — faster onset, higher efficiency.",
        scoreAdjustment = 0
    ),
    OVULATION(
        displayName = "Ovulation",
        sleepImpact = "Sleep often improves around ovulation.",
        scoreAdjustment = 0
    ),
    LUTEAL(
        displayName = "Luteal",
        sleepImpact = "Body temperature elevated, REM reduced. Sleep feels heavy but may be less restorative.",
        scoreAdjustment = 5
    ),
    PREMENSTRUAL(
        displayName = "Premenstrual",
        sleepImpact = "Worst sleep quality of the cycle. More awakenings, lighter sleep — this is hormonal.",
        scoreAdjustment = 10
    );

    companion object {
        /**
         * Calculate current cycle phase from last period start and cycle length.
         *
         * @param lastPeriodStart Start date of the last menstrual period
         * @param cycleLength Typical cycle length in days (default 28)
         * @param today Current date
         * @return Current phase, or null if data is insufficient
         */
        fun currentPhase(
            lastPeriodStart: LocalDate?,
            cycleLength: Int = 28,
            today: LocalDate = LocalDate.now()
        ): MenstrualCyclePhase? {
            if (lastPeriodStart == null) return null

            val daysSinceStart = ChronoUnit.DAYS.between(lastPeriodStart, today).toInt()
            if (daysSinceStart < 0) return null

            // Normalise to current cycle
            val cycleDay = (daysSinceStart % cycleLength) + 1

            // Adjust phase boundaries based on actual cycle length
            val ovulationDay = cycleLength - 14 // Luteal phase is relatively constant at ~14 days
            val lutealStart = ovulationDay + 1
            val premenstrualStart = cycleLength - 7

            return when (cycleDay) {
                in 1..5 -> MENSTRUAL
                in 6 until ovulationDay -> FOLLICULAR
                ovulationDay -> OVULATION
                in lutealStart until premenstrualStart -> LUTEAL
                in premenstrualStart..cycleLength -> PREMENSTRUAL
                else -> FOLLICULAR
            }
        }

        /**
         * Get the cycle day number (1-based) from last period start.
         */
        fun cycleDay(
            lastPeriodStart: LocalDate?,
            cycleLength: Int = 28,
            today: LocalDate = LocalDate.now()
        ): Int? {
            if (lastPeriodStart == null) return null
            val daysSinceStart = ChronoUnit.DAYS.between(lastPeriodStart, today).toInt()
            if (daysSinceStart < 0) return null
            return (daysSinceStart % cycleLength) + 1
        }
    }
}
