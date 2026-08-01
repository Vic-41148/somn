package dev.vic41148.somn.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import dev.vic41148.somn.core.domain.model.HabitEntry
import dev.vic41148.somn.core.domain.model.HabitLog
import dev.vic41148.somn.core.domain.model.SleepSession
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class CorrelationUseCaseTest {

    private val useCase = CorrelationUseCase()
    private val startDate: LocalDate = LocalDate.of(2026, 1, 1)

    private fun sessionAt(
        date: LocalDate,
        onsetMinutes: Int = 20,
        efficiency: Float = 90f,
        wakeEvents: Int = 1,
        score: Int = 80
    ) = SleepSession(
        startTimeMillis = date.atTime(23, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        sleepOnsetMinutes = onsetMinutes,
        sleepEfficiency = efficiency,
        wakeEvents = wakeEvents,
        sleepScore = score,
        isCompleted = true
    )

    @Test
    fun calculate_fewerThanSevenDataPoints_returnsNullForEveryCorrelation() {
        val sessions = (0 until 6).map { i -> sessionAt(startDate.plusDays(i.toLong())) }
        val report = useCase.calculate(sessions, emptyList())

        assertThat(report.caffeineToOnset).isNull()
        assertThat(report.alcoholToEfficiency).isNull()
        assertThat(report.stressToWakes).isNull()
        assertThat(report.exerciseToScore).isNull()
        assertThat(report.hasAnyData).isFalse()
    }

    @Test
    fun calculate_noCaffeineLogged_correlationIsZeroWithNoStrength() {
        // Constant predictor (mg always defaults to 0 with no habit logs) -> Pearson denominator
        // is zero, so the use case must report r=0 rather than dividing by zero.
        val sessions = (0 until 7).map { i -> sessionAt(startDate.plusDays(i.toLong()), onsetMinutes = 10 + i * 5) }
        val report = useCase.calculate(sessions, emptyList())

        val result = report.caffeineToOnset
        assertThat(result).isNotNull()
        assertThat(result!!.correlation).isEqualTo(0f)
        assertThat(result.strength).isEqualTo(CorrelationStrength.NONE)
    }

    @Test
    fun calculate_lateCaffeinePerfectlyTracksLongerOnset_reportsStrongPositiveCorrelation() {
        // Session on day D pairs with habit logs dated D-1 (the night before sleep).
        val sessions = (0 until 7).map { i -> sessionAt(startDate.plusDays(i.toLong()), onsetMinutes = 10 + i * 10) }
        val habitLogs = (0 until 7).map { i ->
            HabitLog(
                date = startDate.plusDays(i.toLong()).minusDays(1),
                entry = HabitEntry.Caffeine(mg = i * 20, timeOfDay = LocalTime.of(15, 0))
            )
        }
        val report = useCase.calculate(sessions, habitLogs)

        val result = report.caffeineToOnset
        assertThat(result).isNotNull()
        assertThat(result!!.correlation).isWithin(0.01f).of(1.0f)
        assertThat(result.isPositive).isTrue()
        assertThat(result.strength).isEqualTo(CorrelationStrength.STRONG)
        assertThat(result.dataPoints).isEqualTo(7)
    }

    @Test
    fun calculate_earlyCaffeineIsExcludedFromLateCaffeineCorrelation() {
        // Caffeine logged before 14:00 must not count toward the "late caffeine" predictor.
        val sessions = (0 until 7).map { i -> sessionAt(startDate.plusDays(i.toLong()), onsetMinutes = 10 + i * 10) }
        val habitLogs = (0 until 7).map { i ->
            HabitLog(
                date = startDate.plusDays(i.toLong()).minusDays(1),
                // Increasing mg but all logged at 8 AM — should be filtered out entirely.
                entry = HabitEntry.Caffeine(mg = i * 20, timeOfDay = LocalTime.of(8, 0))
            )
        }
        val report = useCase.calculate(sessions, habitLogs)

        val result = report.caffeineToOnset
        assertThat(result).isNotNull()
        assertThat(result!!.correlation).isEqualTo(0f)
    }

    @Test
    fun calculate_alcoholPerfectlyTracksLowerEfficiency_reportsStrongNegativeCorrelation() {
        val sessions = (0 until 7).map { i -> sessionAt(startDate.plusDays(i.toLong()), efficiency = 95f - i * 5f) }
        val habitLogs = (0 until 7).map { i ->
            HabitLog(
                date = startDate.plusDays(i.toLong()).minusDays(1),
                entry = HabitEntry.Alcohol(units = i.toFloat(), timeOfDay = LocalTime.of(21, 0))
            )
        }
        val report = useCase.calculate(sessions, habitLogs)

        val result = report.alcoholToEfficiency
        assertThat(result).isNotNull()
        assertThat(result!!.correlation).isWithin(0.01f).of(-1.0f)
        assertThat(result.isPositive).isFalse()
        assertThat(result.strength).isEqualTo(CorrelationStrength.STRONG)
    }

    @Test
    fun calculate_higherStressTracksMoreWakeEvents_reportsStrongPositiveCorrelation() {
        val stressLevels = listOf(1, 1, 2, 3, 4, 5, 5)
        val wakeEvents = listOf(0, 0, 1, 2, 3, 4, 4)
        val sessions = (0 until 7).map { i -> sessionAt(startDate.plusDays(i.toLong()), wakeEvents = wakeEvents[i]) }
        val habitLogs = (0 until 7).map { i ->
            HabitLog(
                date = startDate.plusDays(i.toLong()).minusDays(1),
                entry = HabitEntry.Stress(level = stressLevels[i])
            )
        }
        val report = useCase.calculate(sessions, habitLogs)

        val result = report.stressToWakes
        assertThat(result).isNotNull()
        assertThat(result!!.isPositive).isTrue()
        assertThat(result.strength).isEqualTo(CorrelationStrength.STRONG)
    }

    @Test
    fun calculate_exerciseTracksHigherSleepScore_reportsStrongPositiveCorrelation() {
        val durations = listOf(0, 10, 20, 30, 40, 50, 60)
        val scores = listOf(50, 55, 60, 70, 75, 85, 95)
        val sessions = (0 until 7).map { i -> sessionAt(startDate.plusDays(i.toLong()), score = scores[i]) }
        val habitLogs = (0 until 7).map { i ->
            HabitLog(
                date = startDate.plusDays(i.toLong()).minusDays(1),
                entry = HabitEntry.Exercise(
                    type = dev.vic41148.somn.core.domain.model.ExerciseType.RUNNING,
                    durationMinutes = durations[i],
                    timeOfDay = LocalTime.of(18, 0)
                )
            )
        }
        val report = useCase.calculate(sessions, habitLogs)

        val result = report.exerciseToScore
        assertThat(result).isNotNull()
        assertThat(result!!.isPositive).isTrue()
        assertThat(result.strength).isEqualTo(CorrelationStrength.STRONG)
        assertThat(report.hasAnyData).isTrue()
        assertThat(report.availableCorrelations).contains(result)
    }
}
