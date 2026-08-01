package dev.vic41148.somn.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import dev.vic41148.somn.core.domain.model.DebtLevel
import dev.vic41148.somn.core.domain.model.DebtTrend
import dev.vic41148.somn.core.domain.model.RecoveryPlan
import dev.vic41148.somn.core.domain.model.SleepSession
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SleepDebtUseCaseTest {

    private val useCase = SleepDebtUseCase()
    private val today: LocalDate = LocalDate.now()

    private fun sessionOnDate(date: LocalDate, durationMinutes: Int) = SleepSession(
        startTimeMillis = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        sleepDurationMinutes = durationMinutes,
        isCompleted = true
    )

    /** Builds all 14 sessions in the rolling window, keyed by "days ago" (0 = today). */
    private fun buildWindow(durationForDaysAgo: (Int) -> Int): List<SleepSession> =
        (0..13).map { daysAgo -> sessionOnDate(today.minusDays(daysAgo.toLong()), durationForDaysAgo(daysAgo)) }

    @Test
    fun calculate_noSessions_reportsNoDebtAndNoRecoveryPlan() {
        val (debt, plan) = useCase.calculate(emptyList(), targetSleepMinutes = 480)
        assertThat(debt.totalDebtMinutes).isEqualTo(0)
        assertThat(debt.level).isEqualTo(DebtLevel.NONE)
        assertThat(debt.trend).isEqualTo(DebtTrend.STABLE)
        assertThat(plan).isEqualTo(RecoveryPlan.NONE)
    }

    @Test
    fun calculate_consistentShortfall_accumulatesDebtAndBuildsRecoveryPlan() {
        // 60min shortfall every night for 14 nights = 840min total debt.
        val sessions = buildWindow { 420 }
        val (debt, plan) = useCase.calculate(sessions, targetSleepMinutes = 480)

        assertThat(debt.totalDebtMinutes).isEqualTo(840)
        assertThat(debt.level).isEqualTo(DebtLevel.SEVERE)
        assertThat(debt.trend).isEqualTo(DebtTrend.STABLE)

        // additionalPerNight = min(90, 840) rounded to nearest 15 = 90; recoveryDays = ceil(840/90) = 10
        assertThat(plan.additionalMinutesPerNight).isEqualTo(90)
        assertThat(plan.estimatedRecoveryDays).isEqualTo(10)
        assertThat(plan.suggestedBedtimeShiftMinutes).isEqualTo(90)
    }

    @Test
    fun calculate_incompleteSessionsAreExcludedFromDebtCalculation() {
        val incompleteSession = SleepSession(
            startTimeMillis = today.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            sleepDurationMinutes = 100,
            isCompleted = false
        )
        val (debt, _) = useCase.calculate(listOf(incompleteSession), targetSleepMinutes = 480)
        // Excluded session means that day has no data — contributes 0, not a huge debt spike.
        assertThat(debt.totalDebtMinutes).isEqualTo(0)
    }

    @Test
    fun calculate_massiveSurplusOnASingleNight_isCappedAtNinetyMinutes() {
        // One night of major oversleep (target 480, actual 800 => -320min raw) should only
        // offset the total by the 90min cap, not the full 320min surplus.
        val surplusSession = sessionOnDate(today, durationMinutes = 800)
        val (debt, plan) = useCase.calculate(listOf(surplusSession), targetSleepMinutes = 480)

        assertThat(debt.totalDebtMinutes).isEqualTo(0) // -90 raw, coerced to 0 at the floor
        assertThat(debt.level).isEqualTo(DebtLevel.NONE)
        assertThat(plan).isEqualTo(RecoveryPlan.NONE)
    }

    @Test
    fun calculate_recentWeekWorseThanPriorWeek_trendIsWorsening() {
        // Prior 7 nights (days 13..7 ago) on target; recent 7 nights (days 6..0 ago) 60min short.
        val sessions = buildWindow { daysAgo -> if (daysAgo <= 6) 420 else 480 }
        val (debt, _) = useCase.calculate(sessions, targetSleepMinutes = 480)
        assertThat(debt.trend).isEqualTo(DebtTrend.WORSENING)
    }

    @Test
    fun calculate_recentWeekBetterThanPriorWeek_trendIsImproving() {
        // Prior 7 nights 60min short; recent 7 nights on target.
        val sessions = buildWindow { daysAgo -> if (daysAgo <= 6) 480 else 420 }
        val (debt, _) = useCase.calculate(sessions, targetSleepMinutes = 480)
        assertThat(debt.trend).isEqualTo(DebtTrend.IMPROVING)
    }

    @Test
    fun calculate_smallWeekOverWeekDifference_trendStaysStable() {
        // Only a 5min swing between weeks — within the +/-10min STABLE band.
        val sessions = buildWindow { daysAgo -> if (daysAgo <= 6) 475 else 480 }
        val (debt, _) = useCase.calculate(sessions, targetSleepMinutes = 480)
        assertThat(debt.trend).isEqualTo(DebtTrend.STABLE)
    }
}
