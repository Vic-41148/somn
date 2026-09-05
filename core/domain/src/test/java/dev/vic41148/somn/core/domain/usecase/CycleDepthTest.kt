package dev.vic41148.somn.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import dev.vic41148.somn.core.domain.model.MenstrualCyclePhase
import org.junit.Test

class CycleDepthTest {

    // ---- Temp refinement ----

    @Test
    fun `no cycle data explains itself`() {
        val r = refinePhase(null, emptyList())
        assertThat(r.source).isEqualTo(PhaseSource.CALENDAR)
        assertThat(r.note).contains("enable cycle tracking")
    }

    @Test
    fun `thin temp history stays calendar with count`() {
        val r = refinePhase(MenstrualCyclePhase.FOLLICULAR, listOf(36.1f, 36.2f, null))
        assertThat(r.phase).isEqualTo(MenstrualCyclePhase.FOLLICULAR)
        assertThat(r.source).isEqualTo(PhaseSource.CALENDAR)
        assertThat(r.tempNights).isEqualTo(2)
        assertThat(r.note).contains("2/6 temperature nights")
    }

    @Test
    fun `sustained rise moves follicular to luteal`() {
        val temps = List(4) { 36.1f } + List(4) { 36.5f }
        val r = refinePhase(MenstrualCyclePhase.FOLLICULAR, temps)
        assertThat(r.phase).isEqualTo(MenstrualCyclePhase.LUTEAL)
        assertThat(r.source).isEqualTo(PhaseSource.CALENDAR_AND_TEMP)
        assertThat(r.note).contains("ovulation likely passed")
    }

    @Test
    fun `flat temps keep calendar phase`() {
        val temps = List(8) { 36.2f }
        val r = refinePhase(MenstrualCyclePhase.FOLLICULAR, temps)
        assertThat(r.phase).isEqualTo(MenstrualCyclePhase.FOLLICULAR)
        assertThat(r.source).isEqualTo(PhaseSource.CALENDAR_AND_TEMP)
    }

    @Test
    fun `rise never moves an already-luteal phase`() {
        val temps = List(4) { 36.1f } + List(4) { 36.6f }
        val r = refinePhase(MenstrualCyclePhase.LUTEAL, temps)
        assertThat(r.phase).isEqualTo(MenstrualCyclePhase.LUTEAL)
    }

    @Test
    fun `nulls are skipped not counted`() {
        val temps = listOf(36.1f, null, 36.1f, null, 36.1f, 36.1f, 36.5f, 36.5f, 36.5f, 36.5f)
        val r = refinePhase(MenstrualCyclePhase.OVULATION, temps)
        assertThat(r.phase).isEqualTo(MenstrualCyclePhase.LUTEAL)
        assertThat(r.tempNights).isEqualTo(8)
    }

    // ---- Luteal coaching ----

    @Test
    fun `luteal and premenstrual coach, others silent`() {
        assertThat(lutealCoaching(MenstrualCyclePhase.LUTEAL)).contains("extra 20 minutes")
        assertThat(lutealCoaching(MenstrualCyclePhase.PREMENSTRUAL)).contains("hormonal")
        assertThat(lutealCoaching(MenstrualCyclePhase.FOLLICULAR)).isNull()
        assertThat(lutealCoaching(null)).isNull()
    }

    // ---- Menopause questionnaire ----

    @Test
    fun `bands split the range in thirds`() {
        assertThat(scoreMenopause(List(10) { 0 })).isEqualTo(MenoBand.MINIMAL)
        assertThat(scoreMenopause(List(10) { 1 })).isEqualTo(MenoBand.MODERATE)
        assertThat(scoreMenopause(List(10) { 2 })).isEqualTo(MenoBand.MODERATE)
        assertThat(scoreMenopause(List(10) { 3 })).isEqualTo(MenoBand.SIGNIFICANT)
    }

    @Test
    fun `significant band points at a doctor not a diagnosis`() {
        assertThat(MenoBand.SIGNIFICANT.summary).contains("mentioning to a doctor")
        assertThat(MenoBand.SIGNIFICANT.summary).contains("not a")
    }

    @Test
    fun `wrong answer count throws`() {
        var thrown = false
        try {
            scoreMenopause(listOf(1, 2, 3))
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertThat(thrown).isTrue()
    }

    // ---- Life-stage banner ----

    @Test
    fun `pregnancy banner carries trimester`() {
        assertThat(lifeStageBanner("PREGNANT", 2)).contains("trimester 2")
        assertThat(lifeStageBanner("POSTPARTUM", null)).contains("Newborn phase")
        assertThat(lifeStageBanner("CYCLING", null)).isNull()
    }
}
