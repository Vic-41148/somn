package dev.vic41148.somn.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import dev.vic41148.somn.core.domain.model.SessionType
import org.junit.Test

class ManualSessionUseCaseTest {

    private val useCase = ManualSessionUseCase(CalculateSleepScoreUseCase())

    private val start = 1_700_000_000_000L
    private val hourMillis = 60 * 60 * 1000L

    // ---- createManualSession ----

    @Test
    fun createManualSession_validTimes_returnsCompletedFlaggedSession() {
        val end = start + 8 * hourMillis
        val session = useCase.createManualSession(start, end)

        assertThat(session).isNotNull()
        assertThat(session!!.startTimeMillis).isEqualTo(start)
        assertThat(session.endTimeMillis).isEqualTo(end)
        assertThat(session.sleepDurationMinutes).isEqualTo(480)
        assertThat(session.timeInBedMinutes).isEqualTo(480)
        assertThat(session.sleepEfficiency).isEqualTo(100f)
        assertThat(session.isCompleted).isTrue()
        // The whole point: a manual entry must be distinguishable from a tracked one so the UI
        // can label it honestly and never imply measured sensor data.
        assertThat(session.isManualEntry).isTrue()
        assertThat(session.sessionType).isEqualTo(SessionType.MAIN_SLEEP)
        assertThat(session.timezoneId).isEqualTo(java.time.ZoneId.systemDefault().id)
        assertThat(session.sleepScore).isIn(0..100)
    }

    @Test
    fun createManualSession_fullTargetNight_scoresByDurationOnly() {
        val session = useCase.createManualSession(start, start + 8 * hourMillis)
        // 8h against the 8h default target -> duration ratio 1.0 -> 100. The full composite
        // would punish the (unknown) 0% deep sleep; the manual score must not.
        assertThat(session!!.sleepScore).isEqualTo(100)
    }

    @Test
    fun createManualSession_shortNight_scoresByDurationOnly() {
        val session = useCase.createManualSession(start, start + 3 * hourMillis)
        // 3h -> ratio 0.375 -> 0.375 * 66 = 24.75 -> 24
        assertThat(session!!.sleepScore).isEqualTo(24)
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
        // 10h against the 8h target -> ratio 1.25 -> oversleep floor 60..85, so the duration-
        // based manual score must move off its original 100 (it would have stayed stale at 100).
        assertThat(adjusted.sleepScore).isEqualTo(85)
    }

    @Test
    fun adjustSessionTimes_invalidOrder_returnsOriginalUnchanged() {
        val session = useCase.createManualSession(start, start + 8 * hourMillis)!!
        val adjusted = useCase.adjustSessionTimes(session, newEndMillis = start - 60_000)
        assertThat(adjusted).isEqualTo(session)
    }

    @Test
    fun adjustSessionTimes_manualFlagIsPreserved() {
        val session = useCase.createManualSession(start, start + 8 * hourMillis)!!
        val adjusted = useCase.adjustSessionTimes(session, newStartMillis = start + 60_000)
        assertThat(adjusted.isManualEntry).isTrue()
    }

    // ---- extendSession ----

    @Test
    fun extendSession_addsMinutesToEndAndRecalculates() {
        val session = useCase.createManualSession(start, start + 8 * hourMillis)!!
        val extended = useCase.extendSession(session, 30)

        assertThat(extended.endTimeMillis).isEqualTo(start + 8 * hourMillis + 30 * 60_000)
        assertThat(extended.timeInBedMinutes).isEqualTo(510)
    }
}
