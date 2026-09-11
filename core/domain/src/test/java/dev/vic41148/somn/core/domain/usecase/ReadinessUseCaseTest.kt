package dev.vic41148.somn.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import dev.vic41148.somn.core.domain.model.DebtLevel
import dev.vic41148.somn.core.domain.model.DebtTrend
import dev.vic41148.somn.core.domain.model.SleepDebt
import dev.vic41148.somn.core.domain.model.SleepSession
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReadinessUseCaseTest {

    private fun sessionOn(
        date: LocalDate,
        hour: Int = 23,
        score: Int = 75,
        durationMinutes: Int = 450
    ) = SleepSession(
        startTimeMillis = date.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        endTimeMillis = date.plusDays(1).atTime(7, 0).atZone(ZoneId.systemDefault()).toInstant()
            .toEpochMilli(),
        sleepDurationMinutes = durationMinutes,
        timeInBedMinutes = durationMinutes + 20,
        sleepEfficiency = 90f,
        sleepScore = score,
        isCompleted = true
    )

    private fun noDebt() = SleepDebt(
        totalDebtMinutes = 0,
        trend = DebtTrend.STABLE,
        level = DebtLevel.NONE,
        dailyBreakdown = emptyList()
    )

    private fun nowMillis(): Long = LocalDate.now().atTime(12, 0)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun `empty sessions return null`() {
        assertThat(assessReadiness(emptyList(), noDebt(), nowMillis = nowMillis())).isNull()
    }

    @Test
    fun `great week is READY`() {
        val today = LocalDate.now()
        val sessions = (0..6).map { sessionOn(today.minusDays(it.toLong()), score = 85) }
        val result = assessReadiness(sessions, noDebt(), nowMillis = nowMillis())!!
        assertThat(result.zone).isEqualTo(ReadinessZone.READY)
        assertThat(result.isCalibrated).isTrue()
        assertThat(result.nightsUsed).isEqualTo(7)
    }

    @Test
    fun `heavy debt drags verdict to REST`() {
        val today = LocalDate.now()
        val badHours = listOf(21, 1, 23, 2, 0, 22, 3)
        val sessions = badHours.mapIndexed { i, h ->
            sessionOn(today.minusDays(i.toLong()), hour = h, score = 38, durationMinutes = 300)
        }
        val debt = noDebt().copy(totalDebtMinutes = 600, level = DebtLevel.SEVERE)
        val result = assessReadiness(sessions, debt, nowMillis = nowMillis())!!
        assertThat(result.zone).isEqualTo(ReadinessZone.REST)
    }

    @Test
    fun `short last night caps the zone despite perfect vitals`() {
        val today = LocalDate.now()
        val sessions = (1..6).map { sessionOn(today.minusDays(it.toLong()), score = 90) } +
            sessionOn(today, score = 30, durationMinutes = 180)
        val vitals = VitalsDeviation(restingHrDeltaBpm = 0f, hrvDeltaMs = 5f, tempDeltaCelsius = 0f)
        val result = assessReadiness(sessions, noDebt(), vitals, nowMillis = nowMillis())!!
        assertThat(result.zone).isNotEqualTo(ReadinessZone.READY)
    }

    @Test
    fun `missing debt and vitals still score from sleep signals`() {
        val today = LocalDate.now()
        val sessions = (0..4).map { sessionOn(today.minusDays(it.toLong()), score = 80) }
        val result = assessReadiness(sessions, null, null, nowMillis = nowMillis())!!
        assertThat(result.zone).isEqualTo(ReadinessZone.READY)
        assertThat(result.contributors.count { it.hasData }).isEqualTo(2)
    }

    @Test
    fun `scattered bedtimes score lower than steady ones`() {
        val today = LocalDate.now()
        val steady = (0..4).map { sessionOn(today.minusDays(it.toLong()), hour = 23, score = 75) }
        val scatteredHours = listOf(21, 1, 23, 2, 22)
        val scattered = scatteredHours.mapIndexed { i, h ->
            sessionOn(today.minusDays(i.toLong()), hour = h, score = 75)
        }
        val steadyScore = assessReadiness(steady, null, null, nowMillis = nowMillis())!!
            .contributors.first { it.label == "Consistency" }.score
        val scatteredScore = assessReadiness(scattered, null, null, nowMillis = nowMillis())!!
            .contributors.first { it.label == "Consistency" }.score
        assertThat(scatteredScore).isLessThan(steadyScore)
    }

    @Test
    fun `bedtimes across midnight measure as close together`() {
        // 23:30 and 00:30 are 60 min apart, not 23h apart.
        assertThat(circularStdevMinutes(listOf(1410, 30))).isWithin(1f).of(30f)
    }

    @Test
    fun `zone boundaries match spec`() {
        assertThat(ReadinessZone.from(67)).isEqualTo(ReadinessZone.READY)
        assertThat(ReadinessZone.from(66)).isEqualTo(ReadinessZone.STEADY)
        assertThat(ReadinessZone.from(34)).isEqualTo(ReadinessZone.STEADY)
        assertThat(ReadinessZone.from(33)).isEqualTo(ReadinessZone.REST)
    }

    @Test
    fun `fewer than 3 nights is uncalibrated`() {
        val today = LocalDate.now()
        val sessions = listOf(sessionOn(today, score = 80), sessionOn(today.minusDays(1), score = 80))
        val result = assessReadiness(sessions, null, null, nowMillis = nowMillis())!!
        assertThat(result.isCalibrated).isFalse()
        assertThat(result.contributors.first { it.label == "Consistency" }.hasData).isFalse()
    }

    @Test
    fun `high prior-day activity adds contributor and boosts the verdict`() {
        val today = LocalDate.now()
        val sessions = (0..6).map { sessionOn(today.minusDays(it.toLong()), score = 80) }
        val activity = ActivityDeviation(priorDaySteps = 12_000, priorDayActiveMinutes = 60)
        val without = assessReadiness(sessions, noDebt(), nowMillis = nowMillis())!!
        val with = assessReadiness(sessions, noDebt(), activity = activity, nowMillis = nowMillis())!!
        val contributor = with.contributors.first { it.label == "Yesterday's activity" }
        assertThat(contributor.hasData).isTrue()
        assertThat(contributor.score).isEqualTo(100)
        assertThat(with.score).isGreaterThan(without.score)
    }

    @Test
    fun `missing activity is skipped not scored as quiet`() {
        val today = LocalDate.now()
        val sessions = (0..6).map { sessionOn(today.minusDays(it.toLong()), score = 80) }
        val result = assessReadiness(sessions, noDebt(), nowMillis = nowMillis())!!
        assertThat(result.contributors.none { it.label == "Yesterday's activity" }).isTrue()
    }

    @Test
    fun `zero prior-day steps scores the activity contributor zero`() {
        val today = LocalDate.now()
        val sessions = (0..4).map { sessionOn(today.minusDays(it.toLong()), score = 80) }
        val activity = ActivityDeviation(priorDaySteps = 0, priorDayActiveMinutes = 0)
        val result = assessReadiness(sessions, noDebt(), activity = activity, nowMillis = nowMillis())!!
        assertThat(result.contributors.first { it.label == "Yesterday's activity" }.score).isEqualTo(0)
    }

    @Test
    fun `partial activity data averages only what is present`() {
        val today = LocalDate.now()
        val sessions = (0..4).map { sessionOn(today.minusDays(it.toLong()), score = 80) }
        val stepsOnly = ActivityDeviation(priorDaySteps = 5_000, priorDayActiveMinutes = null)
        val result = assessReadiness(sessions, noDebt(), activity = stepsOnly, nowMillis = nowMillis())!!
        val contributor = result.contributors.first { it.label == "Yesterday's activity" }
        assertThat(contributor.score).isEqualTo(50)
        assertThat(contributor.detail).contains("steps")
    }
}
