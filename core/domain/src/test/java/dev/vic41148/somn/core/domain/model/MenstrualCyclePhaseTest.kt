package dev.vic41148.somn.core.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class MenstrualCyclePhaseTest {

    private val periodStart = LocalDate.of(2026, 1, 1)

    private fun phaseAtDaysSinceStart(daysSinceStart: Long, cycleLength: Int = 28) =
        MenstrualCyclePhase.currentPhase(
            lastPeriodStart = periodStart,
            cycleLength = cycleLength,
            today = periodStart.plusDays(daysSinceStart)
        )

    // ---- currentPhase() — null-safety guards ----

    @Test
    fun currentPhase_nullLastPeriodStart_returnsNull() {
        assertThat(MenstrualCyclePhase.currentPhase(lastPeriodStart = null, today = periodStart)).isNull()
    }

    @Test
    fun currentPhase_todayBeforeLastPeriodStart_returnsNull() {
        val result = MenstrualCyclePhase.currentPhase(
            lastPeriodStart = periodStart,
            today = periodStart.minusDays(1)
        )
        assertThat(result).isNull()
    }

    // ---- currentPhase() — 28-day cycle boundaries ----
    // ovulationDay = 28-14 = 14, lutealStart = 15, premenstrualStart = 21

    @Test
    fun currentPhase_cycleDayOne_isMenstrual() {
        assertThat(phaseAtDaysSinceStart(0)).isEqualTo(MenstrualCyclePhase.MENSTRUAL)
    }

    @Test
    fun currentPhase_cycleDayFive_isLastMenstrualDay() {
        assertThat(phaseAtDaysSinceStart(4)).isEqualTo(MenstrualCyclePhase.MENSTRUAL)
    }

    @Test
    fun currentPhase_cycleDaySix_isFirstFollicularDay() {
        assertThat(phaseAtDaysSinceStart(5)).isEqualTo(MenstrualCyclePhase.FOLLICULAR)
    }

    @Test
    fun currentPhase_cycleDayThirteen_isLastFollicularDay() {
        assertThat(phaseAtDaysSinceStart(12)).isEqualTo(MenstrualCyclePhase.FOLLICULAR)
    }

    @Test
    fun currentPhase_cycleDayFourteen_isOvulation() {
        assertThat(phaseAtDaysSinceStart(13)).isEqualTo(MenstrualCyclePhase.OVULATION)
    }

    @Test
    fun currentPhase_cycleDayFifteen_isFirstLutealDay() {
        assertThat(phaseAtDaysSinceStart(14)).isEqualTo(MenstrualCyclePhase.LUTEAL)
    }

    @Test
    fun currentPhase_cycleDayTwenty_isLastLutealDay() {
        assertThat(phaseAtDaysSinceStart(19)).isEqualTo(MenstrualCyclePhase.LUTEAL)
    }

    @Test
    fun currentPhase_cycleDayTwentyOne_isFirstPremenstrualDay() {
        assertThat(phaseAtDaysSinceStart(20)).isEqualTo(MenstrualCyclePhase.PREMENSTRUAL)
    }

    @Test
    fun currentPhase_cycleDayTwentyEight_isLastPremenstrualDay() {
        assertThat(phaseAtDaysSinceStart(27)).isEqualTo(MenstrualCyclePhase.PREMENSTRUAL)
    }

    @Test
    fun currentPhase_dayTwentyNine_wrapsAroundToNextCycleMenstrual() {
        assertThat(phaseAtDaysSinceStart(28)).isEqualTo(MenstrualCyclePhase.MENSTRUAL)
    }

    // ---- currentPhase() — non-default cycle length rescales boundaries ----
    // 21-day cycle: ovulationDay = 21-14 = 7, lutealStart = 8, premenstrualStart = 14

    @Test
    fun currentPhase_shortCycle_ovulationDayShiftsWithCycleLength() {
        assertThat(phaseAtDaysSinceStart(6, cycleLength = 21)).isEqualTo(MenstrualCyclePhase.OVULATION)
    }

    @Test
    fun currentPhase_shortCycle_premenstrualStartShiftsWithCycleLength() {
        assertThat(phaseAtDaysSinceStart(13, cycleLength = 21)).isEqualTo(MenstrualCyclePhase.PREMENSTRUAL)
    }

    // ---- cycleDay() ----

    @Test
    fun cycleDay_nullLastPeriodStart_returnsNull() {
        assertThat(MenstrualCyclePhase.cycleDay(lastPeriodStart = null, today = periodStart)).isNull()
    }

    @Test
    fun cycleDay_todayBeforeLastPeriodStart_returnsNull() {
        val result = MenstrualCyclePhase.cycleDay(
            lastPeriodStart = periodStart,
            today = periodStart.minusDays(1)
        )
        assertThat(result).isNull()
    }

    @Test
    fun cycleDay_returnsOneBasedDayWithinCycle() {
        val result = MenstrualCyclePhase.cycleDay(lastPeriodStart = periodStart, today = periodStart.plusDays(9))
        assertThat(result).isEqualTo(10)
    }

    @Test
    fun cycleDay_wrapsAroundAfterFullCycleLength() {
        // 28 days after start = day 29 overall, which is cycle day 1 of the next cycle.
        val result = MenstrualCyclePhase.cycleDay(lastPeriodStart = periodStart, today = periodStart.plusDays(28))
        assertThat(result).isEqualTo(1)
    }
}
