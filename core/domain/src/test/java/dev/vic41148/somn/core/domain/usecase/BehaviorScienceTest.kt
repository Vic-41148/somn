package dev.vic41148.somn.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import dev.vic41148.somn.core.domain.model.HabitEntry
import dev.vic41148.somn.core.domain.model.HabitLog
import dev.vic41148.somn.core.domain.model.SleepSession
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class BehaviorScienceTest {

    private val zone = ZoneId.systemDefault()

    private fun sessionOn(date: LocalDate, score: Int = 70, efficiency: Float = 88f) =
        SleepSession(
            startTimeMillis = date.atTime(23, 0).atZone(zone).toInstant().toEpochMilli(),
            endTimeMillis = date.plusDays(1).atTime(7, 0).atZone(zone).toInstant().toEpochMilli(),
            sleepDurationMinutes = 420,
            timeInBedMinutes = 450,
            sleepEfficiency = efficiency,
            sleepScore = score,
            isCompleted = true
        )

    private fun alcoholLog(date: LocalDate) = HabitLog(
        date = date,
        entry = HabitEntry.Alcohol(units = 2f, timeOfDay = LocalTime.of(20, 0))
    )

    // ---- Maturity ----

    @Test
    fun `maturity labels follow confidence`() {
        assertThat(CorrelationConfidence.LOW.maturityLabel).isEqualTo("Early read")
        assertThat(CorrelationConfidence.MEDIUM.maturityLabel).isEqualTo("Firming up")
        assertThat(CorrelationConfidence.HIGH.maturityLabel).isEqualTo("Settled")
    }

    // ---- Tag impact ----

    @Test
    fun `tag impact needs five nights each side`() {
        val nights = List(4) { TaggedNight(true, 60) } + List(40) { TaggedNight(false, 72) }
        assertThat(tagImpact("Travel", nights)).isNull()
    }

    @Test
    fun `tag impact voices lower direction n-of-1`() {
        val nights = List(6) { TaggedNight(true, 64) } + List(41) { TaggedNight(false, 72) }
        val impact = tagImpact("Travel", nights)!!
        assertThat(impact.taggedAvgScore).isEqualTo(64)
        assertThat(impact.untaggedAvgScore).isEqualTo(72)
        assertThat(impact.insight).contains("8 pts lower")
        assertThat(impact.insight).contains("6 vs 41 nights")
    }

    @Test
    fun `tag impact voices higher direction`() {
        val nights = List(5) { TaggedNight(true, 80) } + List(5) { TaggedNight(false, 70) }
        val impact = tagImpact("Sauna", nights)!!
        assertThat(impact.insight).contains("10 pts higher")
    }

    @Test
    fun `tag impact boundary of exactly five counts`() {
        val nights = List(5) { TaggedNight(true, 60) } + List(5) { TaggedNight(false, 60) }
        val impact = tagImpact("Nap", nights)!!
        assertThat(impact.insight).contains("about the same")
    }

    // ---- Efficiency slide ----

    @Test
    fun `six point slide flags`() {
        val flag = efficiencySlide(
            recentEfficiencies = List(10) { 82f },
            priorEfficiencies = List(10) { 88f }
        )!!
        assertThat(flag.title).contains("6 points")
        assertThat(flag.detail).contains("88%")
    }

    @Test
    fun `small dip stays silent`() {
        assertThat(
            efficiencySlide(List(10) { 86f }, List(10) { 88f })
        ).isNull()
    }

    @Test
    fun `improvement stays silent`() {
        assertThat(
            efficiencySlide(List(10) { 92f }, List(10) { 85f })
        ).isNull()
    }

    @Test
    fun `thin sides stay silent`() {
        assertThat(efficiencySlide(List(3) { 70f }, List(10) { 90f })).isNull()
    }

    // ---- Frequency shift ----

    @Test
    fun `doubling flags with counts`() {
        val flag = frequencyShift("Alcohol", recentDays = 8, pastDays = 3)!!
        assertThat(flag.title).contains("doubled")
        assertThat(flag.detail).contains("3 → 8 nights")
    }

    @Test
    fun `new appearance flags`() {
        val flag = frequencyShift("Alcohol", recentDays = 5, pastDays = 0)!!
        assertThat(flag.title).contains("appeared")
    }

    @Test
    fun `stable frequency stays silent`() {
        assertThat(frequencyShift("Alcohol", recentDays = 5, pastDays = 4)).isNull()
        assertThat(frequencyShift("Alcohol", recentDays = 2, pastDays = 0)).isNull()
    }

    // ---- Orchestrator ----

    @Test
    fun `detectShifts finds alcohol doubling plus efficiency slide`() {
        val today = LocalDate.now()
        val sessions = (1..60).map { ago ->
            sessionOn(
                today.minusDays(ago.toLong()),
                efficiency = if (ago <= 30) 80f else 88f
            )
        }
        val logs = buildList {
            // 8 alcohol days in the recent window, 3 in the prior one.
            listOf(2, 5, 9, 13, 17, 21, 25, 29).forEach { add(alcoholLog(today.minusDays(it.toLong()))) }
            listOf(35, 45, 55).forEach { add(alcoholLog(today.minusDays(it.toLong()))) }
        }
        val flags = detectShifts(sessions, logs)
        assertThat(flags.map { it.title }).contains("Alcohol frequency doubled this month")
        assertThat(flags.map { it.title }).hasSize(2)
    }

    @Test
    fun `detectShifts quiet on healthy data`() {
        val today = LocalDate.now()
        val sessions = (1..60).map { sessionOn(today.minusDays(it.toLong())) }
        assertThat(detectShifts(sessions, emptyList())).isEmpty()
    }
}
