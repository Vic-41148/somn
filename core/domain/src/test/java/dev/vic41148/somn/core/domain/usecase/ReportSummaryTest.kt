package dev.vic41148.somn.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import dev.vic41148.somn.core.domain.model.SleepSession
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReportSummaryTest {

    private fun sessionOn(
        date: LocalDate,
        score: Int = 70,
        durationMinutes: Int = 420,
        efficiency: Float = 90f
    ) = SleepSession(
        startTimeMillis = date.atTime(23, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        endTimeMillis = date.plusDays(1).atTime(7, 0).atZone(ZoneId.systemDefault()).toInstant()
            .toEpochMilli(),
        sleepDurationMinutes = durationMinutes,
        timeInBedMinutes = durationMinutes + 30,
        sleepEfficiency = efficiency,
        deepSleepPercent = 18f,
        remSleepPercent = 22f,
        sleepScore = score,
        isCompleted = true
    )

    @Test
    fun `empty list returns null`() {
        assertThat(summarizeSessions(emptyList())).isNull()
    }

    @Test
    fun `incomplete sessions are ignored`() {
        val s = sessionOn(LocalDate.now()).copy(isCompleted = false)
        assertThat(summarizeSessions(listOf(s))).isNull()
    }

    @Test
    fun `averages and totals are computed`() {
        val today = LocalDate.now()
        val sessions = listOf(
            sessionOn(today.minusDays(1), score = 60, durationMinutes = 360, efficiency = 80f),
            sessionOn(today, score = 80, durationMinutes = 480, efficiency = 90f)
        )
        val summary = summarizeSessions(sessions)!!
        assertThat(summary.nights).isEqualTo(2)
        assertThat(summary.avgScore).isEqualTo(70)
        assertThat(summary.avgDurationMinutes).isEqualTo(420)
        assertThat(summary.avgEfficiencyPercent).isEqualTo(85)
        assertThat(summary.bestScore).isEqualTo(80)
        assertThat(summary.totalSleepMinutes).isEqualTo(840)
    }

    @Test
    fun `score delta is newest minus oldest`() {
        val today = LocalDate.now()
        val sessions = listOf(
            sessionOn(today.minusDays(2), score = 50),
            sessionOn(today, score = 75)
        )
        assertThat(summarizeSessions(sessions)!!.scoreDelta).isEqualTo(25)
    }

    @Test
    fun `consecutive days count as streak`() {
        val today = LocalDate.now()
        val sessions = (0..2).map { sessionOn(today.minusDays(it.toLong())) }
        assertThat(summarizeSessions(sessions)!!.streakNights).isEqualTo(3)
    }

    @Test
    fun `gap breaks the streak at the newest run`() {
        val today = LocalDate.now()
        val sessions = listOf(
            sessionOn(today.minusDays(10)),
            sessionOn(today.minusDays(1)),
            sessionOn(today)
        )
        assertThat(summarizeSessions(sessions)!!.streakNights).isEqualTo(2)
    }

    @Test
    fun `formatDurationShort never returns blank`() {
        assertThat(formatDurationShort(0)).isEqualTo("0m")
        assertThat(formatDurationShort(45)).isEqualTo("45m")
        assertThat(formatDurationShort(432)).isEqualTo("7h 12m")
    }

    @Test
    fun `rest-mode nights neither extend nor break the streak`() {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        fun millis(date: LocalDate) =
            date.atTime(23, 0).atZone(zone).toInstant().toEpochMilli()
        // 4 consecutive nights; the newest falls inside Rest Mode and must vanish
        // from the math instead of extending the streak to 4.
        val sessions = listOf(3, 2, 1, 0).map { daysAgo ->
            sessionOn(today.minusDays(daysAgo.toLong()))
        }
        val restSince = today.atTime(23, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val frozen = summarizeSessions(sessions, excludeSinceMillis = restSince)!!
        assertThat(frozen.streakNights).isEqualTo(3)
        assertThat(frozen.nights).isEqualTo(3)
    }
}
