package dev.vic41148.somn.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import dev.vic41148.somn.core.domain.model.DebtLevel
import dev.vic41148.somn.core.domain.model.DebtTrend
import dev.vic41148.somn.core.domain.model.SleepDebt
import org.junit.Test

class OutlookEngineTest {

    private fun readiness(zone: ReadinessZone) = ReadinessResult(
        score = 80, zone = zone, contributors = emptyList(), nightsUsed = 7, isCalibrated = true
    )

    private fun debt() = SleepDebt(
        totalDebtMinutes = 90, trend = DebtTrend.IMPROVING,
        level = DebtLevel.MILD, dailyBreakdown = emptyList()
    )

    @Test
    fun `null readiness never returns blank morning`() {
        val s = buildOutlook(null, null, null, isMorning = true)
        assertThat(s).isNotEmpty()
    }

    @Test
    fun `null readiness never returns blank evening`() {
        val s = buildOutlook(null, null, null, isMorning = false)
        assertThat(s).isNotEmpty()
    }

    @Test
    fun `morning READY names the zone and the advice`() {
        val s = buildOutlook(readiness(ReadinessZone.READY), debt(), null, isMorning = true)
        assertThat(s).contains("primed")
        assertThat(s).contains("push")
    }

    @Test
    fun `morning REST advises rest`() {
        val s = buildOutlook(readiness(ReadinessZone.REST), debt(), null, isMorning = true)
        assertThat(s).contains("Recovery day")
        assertThat(s).contains("light")
    }

    @Test
    fun `correlation insight is appended when present`() {
        val insight = "Late caffeine adds 20 min to your onset."
        val s = buildOutlook(readiness(ReadinessZone.STEADY), debt(), insight, isMorning = true)
        assertThat(s).contains(insight)
    }

    @Test
    fun `evening uses recovery hint when present`() {
        val s = buildOutlook(readiness(ReadinessZone.STEADY), debt(), null, 30, isMorning = false)
        assertThat(s).contains("30 extra minutes")
    }

    @Test
    fun `evening without hint falls back to consistency line`() {
        val s = buildOutlook(readiness(ReadinessZone.STEADY), null, null, null, isMorning = false)
        assertThat(s).contains("consistent bedtime")
    }

    @Test
    fun `rest mode reframes the morning copy`() {
        val s = buildOutlook(readiness(ReadinessZone.READY), debt(), null, isMorning = true, restMode = true)
        assertThat(s).contains("Rest Mode is on")
        assertThat(s).contains("streak")
    }

    @Test
    fun `luteal coaching appends to morning copy`() {
        val s = buildOutlook(
            readiness(ReadinessZone.STEADY), debt(), null,
            isMorning = true, cycleCoaching = "Luteal phase: test coaching."
        )
        assertThat(s).contains("Luteal phase: test coaching.")
    }

    @Test
    fun `evening copy ignores cycle coaching`() {
        val s = buildOutlook(
            readiness(ReadinessZone.STEADY), null, null, null,
            isMorning = false, cycleCoaching = "Luteal phase: test coaching."
        )
        assertThat(s).doesNotContain("Luteal phase")
    }
}
