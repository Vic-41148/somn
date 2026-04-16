package dev.vic41148.somn.core.domain.usecase

import dev.vic41148.somn.core.domain.model.DailyDebt
import dev.vic41148.somn.core.domain.model.DebtLevel
import dev.vic41148.somn.core.domain.model.DebtTrend
import dev.vic41148.somn.core.domain.model.RecoveryPlan
import dev.vic41148.somn.core.domain.model.SleepDebt
import dev.vic41148.somn.core.domain.model.SleepSession
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.min

/**
 * Calculates 14-day rolling sleep debt and produces a personalised recovery plan.
 *
 * Debt model:
 *   • Positive debt = slept less than target that night
 *   • Negative debt = surplus (used to offset previously accumulated debt)
 *   • Surplus is capped per night: cannot recover more than 90 min beyond target
 *     (to avoid disrupting circadian rhythm)
 *
 * Recovery plan:
 *   • Recommends sleeping an additional ≤90 min/night
 *   • Spreads recovery across minimum nights needed
 *   • Never recommends sleeping more than target + 90 min
 */
class SleepDebtUseCase {

    companion object {
        private const val WINDOW_DAYS = 14
        private const val MAX_SURPLUS_MINUTES = 90   // max recoverable per night
        private const val MAX_ADDITIONAL_PER_NIGHT = 90
    }

    /**
     * @param sessions   Up to 14 most recent completed sessions (oldest-first preferred).
     * @param targetSleepMinutes The user's personal sleep target in minutes.
     * @return A [SleepDebt] with breakdown and [RecoveryPlan].
     */
    fun calculate(
        sessions: List<SleepSession>,
        targetSleepMinutes: Int
    ): Pair<SleepDebt, RecoveryPlan> {
        val today = LocalDate.now()
        val window = (0 until WINDOW_DAYS).map { daysAgo ->
            today.minusDays(daysAgo.toLong())
        }.reversed()  // oldest → newest

        // Map sessions to dates (use the date of sleep start)
        val sessionByDate = sessions
            .filter { it.isCompleted }
            .associateBy { session ->
                java.time.Instant.ofEpochMilli(session.startTimeMillis)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
            }

        // Build per-day breakdown
        val dailyBreakdown = window.map { date ->
            val session = sessionByDate[date]
            DailyDebt(
                date = date,
                targetMinutes = targetSleepMinutes,
                actualMinutes = session?.sleepDurationMinutes ?: 0
            )
        }

        // Calculate effective total debt — surpluses can offset deficits, capped per night
        val totalDebtMinutes = dailyBreakdown.sumOf { day ->
            if (!day.hasData) 0
            else day.debtMinutes.coerceAtLeast(-MAX_SURPLUS_MINUTES) // cap surplus recovery
        }

        // Trend: compare average debt of last 7 days vs prior 7 days
        val recent7 = dailyBreakdown.takeLast(7).filter { it.hasData }
        val prior7 = dailyBreakdown.take(7).filter { it.hasData }

        val trend = if (recent7.isEmpty() || prior7.isEmpty()) {
            DebtTrend.STABLE
        } else {
            val recentAvg = recent7.map { it.debtMinutes }.average()
            val priorAvg = prior7.map { it.debtMinutes }.average()
            when {
                recentAvg < priorAvg - 10 -> DebtTrend.IMPROVING
                recentAvg > priorAvg + 10 -> DebtTrend.WORSENING
                else -> DebtTrend.STABLE
            }
        }

        val debt = SleepDebt(
            totalDebtMinutes = totalDebtMinutes.coerceAtLeast(0),
            trend = trend,
            level = DebtLevel.from(totalDebtMinutes.coerceAtLeast(0)),
            dailyBreakdown = dailyBreakdown
        )

        val plan = buildRecoveryPlan(totalDebtMinutes, targetSleepMinutes)
        return Pair(debt, plan)
    }

    private fun buildRecoveryPlan(totalDebtMinutes: Int, targetSleepMinutes: Int): RecoveryPlan {
        if (totalDebtMinutes <= 0) return RecoveryPlan.NONE

        // Recommend ≤90 min extra per night, but distribute smartly
        val additionalPerNight = min(MAX_ADDITIONAL_PER_NIGHT, totalDebtMinutes)
            .let { raw ->
                // Round to nearest 15 min for cleaner messaging
                ((raw + 7) / 15) * 15
            }
            .coerceAtLeast(15)

        val recoveryDays = ceil(totalDebtMinutes.toDouble() / additionalPerNight).toInt()

        val bedtimeShift = additionalPerNight  // go to bed this many minutes earlier

        val explanation = buildExplanation(
            totalDebtMinutes, additionalPerNight, recoveryDays, targetSleepMinutes
        )

        return RecoveryPlan(
            additionalMinutesPerNight = additionalPerNight,
            suggestedBedtimeShiftMinutes = bedtimeShift,
            estimatedRecoveryDays = recoveryDays,
            explanation = explanation
        )
    }

    private fun buildExplanation(
        debtMinutes: Int,
        additionalMinutes: Int,
        recoveryDays: Int,
        targetMinutes: Int
    ): String {
        val debtH = debtMinutes / 60
        val debtM = debtMinutes % 60
        val debtStr = if (debtH > 0) "${debtH}h ${debtM}m" else "${debtM}m"

        val addH = additionalMinutes / 60
        val addM = additionalMinutes % 60
        val addStr = if (addH > 0) "${addH}h ${addM}m" else "${addM}m"

        val targetH = targetMinutes / 60
        val targetM = targetMinutes % 60
        val newTargetMinutes = targetMinutes + additionalMinutes
        val newTargetH = newTargetMinutes / 60
        val newTargetM = newTargetMinutes % 60

        return "You've built up $debtStr of sleep debt over the last 14 days. " +
            "Try sleeping $addStr extra per night (aiming for ${newTargetH}h ${newTargetM}m instead of " +
            "${targetH}h ${targetM}m) for about $recoveryDays night${if (recoveryDays > 1) "s" else ""} " +
            "to fully recover. Go to bed $addStr earlier than usual."
    }
}
