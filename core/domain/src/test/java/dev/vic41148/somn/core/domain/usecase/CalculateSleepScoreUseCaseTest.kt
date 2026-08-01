package dev.vic41148.somn.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import dev.vic41148.somn.core.domain.model.BiologicalSex
import dev.vic41148.somn.core.domain.model.Chronotype
import dev.vic41148.somn.core.domain.model.LifeStage
import dev.vic41148.somn.core.domain.model.MenstrualCyclePhase
import dev.vic41148.somn.core.domain.model.NeurodivergentProfile
import dev.vic41148.somn.core.domain.model.SleepSession
import dev.vic41148.somn.core.domain.model.UserProfile
import org.junit.Test
import java.time.LocalDate

class CalculateSleepScoreUseCaseTest {

    private val useCase = CalculateSleepScoreUseCase()

    private fun session(
        sleepDurationMinutes: Int = 480,
        sleepEfficiency: Float = 95f,
        deepSleepPercent: Float = 20f,
        wakeEvents: Int = 0
    ) = SleepSession(
        startTimeMillis = 0L,
        sleepDurationMinutes = sleepDurationMinutes,
        sleepEfficiency = sleepEfficiency,
        deepSleepPercent = deepSleepPercent,
        wakeEvents = wakeEvents,
        isCompleted = true
    )

    private fun profile(
        dateOfBirth: LocalDate? = null,
        lifeStage: LifeStage = LifeStage.DEFAULT,
        pregnancyTrimester: Int? = null,
        chronotype: Chronotype = Chronotype.UNKNOWN,
        adhdMode: Boolean = false
    ) = UserProfile(
        dateOfBirth = dateOfBirth,
        biologicalSex = BiologicalSex.NOT_SPECIFIED,
        lifeStage = lifeStage,
        chronotype = chronotype,
        neurodivergentProfile = NeurodivergentProfile(adhdMode = adhdMode),
        pregnancyTrimester = pregnancyTrimester
    )

    // ---- invoke() — no profile ----

    @Test
    fun invoke_perfectNight_scoresNinetyNine() {
        // duration=100, efficiency=100, deepSleep=100, wakeEvents=100, consistency=96 (10min variance)
        // 100*.25 + 100*.20 + 100*.20 + 96*.20 + 100*.15 = 99.2 -> truncated to 99
        val score = useCase(session(), averageBedtimeVarianceMinutes = 10f)
        assertThat(score.totalScore).isEqualTo(99)
        assertThat(score.explanation).isEqualTo("Great night! All your sleep metrics look healthy.")
    }

    @Test
    fun invoke_poorNight_scoresTwentyFive() {
        val poorSession = session(
            sleepDurationMinutes = 180,
            sleepEfficiency = 50f,
            deepSleepPercent = 5f,
            wakeEvents = 6
        )
        val score = useCase(poorSession, averageBedtimeVarianceMinutes = 90f)
        assertThat(score.totalScore).isEqualTo(25)
    }

    @Test
    fun invoke_totalScore_isClampedToZeroToHundredRange() {
        val extremeSession = session(
            sleepDurationMinutes = 0,
            sleepEfficiency = 0f,
            deepSleepPercent = 0f,
            wakeEvents = 50
        )
        val score = useCase(extremeSession, averageBedtimeVarianceMinutes = 500f)
        assertThat(score.totalScore).isAtLeast(0)
        assertThat(score.totalScore).isAtMost(100)
    }

    @Test
    fun invoke_massiveOversleep_isPenalizedNotPerfect() {
        // 16h against an 8h target (ratio 2.0) used to fall through to the same formula as
        // near-target sleep and get coerced to a perfect 100 — the oversleep branch was
        // unreachable. It must now score no higher than the oversleep floor of 60.
        val oversleptSession = session(sleepDurationMinutes = 960)
        val score = useCase(oversleptSession, averageBedtimeVarianceMinutes = 10f)
        assertThat(score.durationScore).isEqualTo(60)
    }

    /**
     * Deliberately mediocre (not perfect) session so raw score has headroom below 100 —
     * otherwise adjustedScore's final coerceIn(0,100) silently clips the very adjustment
     * deltas these tests exist to verify. Raw score works out to 79 (see calculateWithProfile
     * default-profile math below), leaving room for adjustments up to the +20 cap.
     */
    private fun moderateSession() = session(
        sleepDurationMinutes = 480,
        sleepEfficiency = 70f,
        deepSleepPercent = 10f,
        wakeEvents = 0
    )

    // ---- calculateWithProfile() — biological adjustments ----

    @Test
    fun calculateWithProfile_noTriggeredAdjustments_adjustedEqualsRaw() {
        val neutralProfile = profile(dateOfBirth = LocalDate.now().minusYears(25))
        val result = useCase.calculateWithProfile(session(), neutralProfile)
        assertThat(result.adjustmentReasons).isEmpty()
        assertThat(result.adjustedScore).isEqualTo(result.rawScore)
    }

    @Test
    fun calculateWithProfile_ageCalibratedDeepSleep_addsThreePoints() {
        val olderProfile = profile(dateOfBirth = LocalDate.now().minusYears(60))
        // deepSleepTargetPercent for age 56-75 is 12.5; band is target*0.7..target*1.3 = 8.75..16.25
        val matchingSession = session(deepSleepPercent = 12f)
        val result = useCase.calculateWithProfile(matchingSession, olderProfile)
        assertThat(result.adjustmentReasons).hasSize(1)
        assertThat(result.adjustmentReasons.single().adjustment).isEqualTo(3)
        assertThat(result.adjustedScore - result.rawScore).isEqualTo(3)
    }

    @Test
    fun calculateWithProfile_pregnancyThirdTrimester_addsTenPoints() {
        val pregnantProfile = profile(lifeStage = LifeStage.PREGNANT, pregnancyTrimester = 3)
        val result = useCase.calculateWithProfile(moderateSession(), pregnantProfile)
        assertThat(result.adjustmentReasons).hasSize(1)
        assertThat(result.adjustmentReasons.single().adjustment).isEqualTo(10)
        assertThat(result.adjustedScore - result.rawScore).isEqualTo(10)
    }

    @Test
    fun calculateWithProfile_postpartum_addsEightPointsUnconditionally() {
        val postpartumProfile = profile(lifeStage = LifeStage.POSTPARTUM)
        val result = useCase.calculateWithProfile(moderateSession(), postpartumProfile)
        assertThat(result.adjustmentReasons).hasSize(1)
        assertThat(result.adjustedScore - result.rawScore).isEqualTo(8)
    }

    @Test
    fun calculateWithProfile_menopauseWithFrequentWaking_addsSixPoints() {
        val menopauseProfile = profile(lifeStage = LifeStage.MENOPAUSE)
        val result = useCase.calculateWithProfile(
            moderateSession().copy(wakeEvents = 4), menopauseProfile
        )
        assertThat(result.adjustmentReasons).hasSize(1)
        assertThat(result.adjustedScore - result.rawScore).isEqualTo(6)
    }

    @Test
    fun calculateWithProfile_menopauseWithFewWakes_noBonusApplied() {
        val menopauseProfile = profile(lifeStage = LifeStage.MENOPAUSE)
        val result = useCase.calculateWithProfile(
            moderateSession().copy(wakeEvents = 2), menopauseProfile
        )
        assertThat(result.adjustmentReasons).isEmpty()
        assertThat(result.adjustedScore).isEqualTo(result.rawScore)
    }

    @Test
    fun calculateWithProfile_totalAdjustment_isCappedAtTwentyPoints() {
        // Pregnancy T3 (+10) + ADHD evening chronotype (+3) + menstrual cyclePhase (+8) = 21 raw, capped to 20.
        // ADHD's own leniency also raises the consistency sub-score, so this uses a lower-still
        // baseline than moderateSession() to keep rawScore + 20 safely clear of the 100 ceiling.
        val lowSession = session(
            sleepDurationMinutes = 480,
            sleepEfficiency = 50f,
            deepSleepPercent = 0f,
            wakeEvents = 0
        )
        val stackedProfile = profile(
            lifeStage = LifeStage.PREGNANT,
            pregnancyTrimester = 3,
            chronotype = Chronotype.DEFINITE_EVENING,
            adhdMode = true
        )
        val result = useCase.calculateWithProfile(
            lowSession,
            stackedProfile,
            cyclePhase = MenstrualCyclePhase.MENSTRUAL
        )
        assertThat(result.adjustmentReasons).hasSize(3)
        assertThat(result.adjustmentReasons.sumOf { it.adjustment }).isEqualTo(21)
        assertThat(result.adjustedScore - result.rawScore).isEqualTo(20)
    }

    @Test
    fun calculateWithProfile_adhdConsistencyLeniency_scoresHigherThanWithoutAdhd() {
        // 20min variance: ADHD leniency (1.5x) keeps it in the top consistency bucket,
        // non-ADHD does not — isolates the leniency branch since every other sub-score is identical.
        val baseSession = session()
        val adhdResult = useCase.calculateWithProfile(
            baseSession, profile(adhdMode = true), averageBedtimeVarianceMinutes = 20f
        )
        val nonAdhdResult = useCase.calculateWithProfile(
            baseSession, profile(adhdMode = false), averageBedtimeVarianceMinutes = 20f
        )
        assertThat(adhdResult.rawScore - nonAdhdResult.rawScore).isEqualTo(1)
    }
}
