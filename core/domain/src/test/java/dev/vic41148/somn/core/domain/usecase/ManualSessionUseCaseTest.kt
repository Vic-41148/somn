package dev.vic41148.somn.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import dev.vic41148.somn.core.domain.model.SessionType
import org.junit.Test

class ManualSessionUseCaseTest {

    private val useCase = ManualSessionUseCase()

    private val start = 1_700_000_000_000L
    private val hourMillis = 60 * 60 * 1000L

    // ---- createManualSession ----

    @Test
    fun createManualSession_validTimes_returnsCompletedSession() {
        val end = start + 8 * hourMillis
        val session = useCase.createManualSession(start, end)

        assertThat(session).isNotNull()
        assertThat(session!!.startTimeMillis).isEqualTo(start)
        assertThat(session.endTimeMillis).isEqualTo(end)
        assertThat(session.sleepDurationMinutes).isEqualTo(480)
        assertThat(session.timeInBedMinutes).isEqualTo(480)
        // Manual entry has no sensor data, so sleep efficiency is assumed rather than measured.
        assertThat(session.sleepEfficiency).isEqualTo(90f)
        assertThat(session.isCompleted).isTrue()
        assertThat(session.sessionType).isEqualTo(SessionType.MAIN_SLEEP)
        assertThat(session.timezoneId).isEqualTo("UTC")
    }

    @Test
    fun createManualSession_endBeforeStart_returnsNull() {
        assertThat(useCase.createManualSession(start, start - 60_000)).isNull()
    }

    @Test
    fun createManualSession_equalTimes_returnsNull() {
        assertThat(useCase.createManualSession(start, start)).isNull()
    }

    @Test
    fun createManualSession_underFifteenMinutes_returnsNull() {
        assertThat(useCase.createManualSession(start, start + 10 * 60_000)).isNull()
    }

    @Test
    fun createManualSession_exactlyFifteenMinutes_isValid() {
        val session = useCase.createManualSession(start, start + 15 * 60_000)
        assertThat(session).isNotNull()
        assertThat(session!!.sleepDurationMinutes).isEqualTo(15)
    }

    // ---- adjustSessionTimes ----

    @Test
    fun adjustSessionTimes_changesEnd_preservesSleepToBedRatio() {
        val session = useCase.createManualSession(start, start + 8 * hourMillis)!!
        val adjusted = useCase.adjustSessionTimes(session, newEndMillis = start + 10 * hourMillis)

        assertThat(adjusted.endTimeMillis).isEqualTo(start + 10 * hourMillis)
        assertThat(adjusted.timeInBedMinutes).isEqualTo(600)
        // ratio 480/480 = 1.0, so all 10h count as sleep
        assertThat(adjusted.sleepDurationMinutes).isEqualTo(600)
        assertThat(adjusted.sleepEfficiency).isEqualTo(100f)
    }

    @Test
    fun adjustSessionTimes_invalidOrder_returnsOriginalUnchanged() {
        val session = useCase.createManualSession(start, start + 8 * hourMillis)!!
        val adjusted = useCase.adjustSessionTimes(session, newEndMillis = start - 60_000)
        assertThat(adjusted).isEqualTo(session)
    }

    // ---- extendSession ----

    @Test
    fun extendSession_addsMinutesToEndAndRecalculates() {
        val session = useCase.createManualSession(start, start + 8 * hourMillis)!!
        val extended = useCase.extendSession(session, 30)

        assertThat(extended.endTimeMillis).isEqualTo(start + 8 * hourMillis + 30 * 60_000L)
        assertThat(extended.timeInBedMinutes).isEqualTo(510)
        assertThat(extended.sleepDurationMinutes).isEqualTo(510)
    }
}
