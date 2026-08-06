package dev.vic41148.somn.feature.onboarding

import com.google.common.truth.Truth.assertThat
import dev.vic41148.somn.core.data.database.dao.UserProfileDao
import dev.vic41148.somn.core.data.database.entity.UserProfileEntity
import dev.vic41148.somn.core.data.repository.UserProfileRepository
import dev.vic41148.somn.core.domain.model.BiologicalSex
import dev.vic41148.somn.core.domain.model.Chronotype
import dev.vic41148.somn.core.domain.model.LifeStage
import dev.vic41148.somn.feature.onboarding.OnboardingViewModel.OnboardingStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests for the onboarding data-quality gate (deep-dive finding #5): the rMEQ scoring
 * math, the LIFE_STAGE skip logic, the DOB → recommended-hours auto-set, the setLifeStage
 * opt-out clearing, and the completeOnboarding state→profile mapping. The repository is driven
 * through a hand-rolled in-memory [FakeUserProfileDao] — no Robolectric or mocking library.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private fun viewModel(dao: FakeUserProfileDao = FakeUserProfileDao()) =
        OnboardingViewModel(UserProfileRepository(dao))

    /**
     * Runs [block] with Main pointing at runTest's scheduler, drains any coroutines the
     * ViewModel launched in viewModelScope (completeOnboarding), and restores Main.
     */
    private fun TestScope.runWithMain(block: () -> Unit) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            block()
            advanceUntilIdle()
        } finally {
            Dispatchers.resetMain()
        }
    }

    // --- rMEQ scoring (setMeqAnswer) ---

    @Test
    fun `partial meq answers keep score null and chronotype unknown`() {
        val vm = viewModel()
        vm.setMeqAnswer(0, 5)
        vm.setMeqAnswer(1, 5)
        vm.setMeqAnswer(2, 4)
        assertThat(vm.state.value.meqScore).isNull()
        assertThat(vm.state.value.chronotype).isEqualTo(Chronotype.UNKNOWN)
    }

    @Test
    fun `meq all maximum answers score 25 definite morning`() {
        val vm = viewModel()
        vm.setMeqAnswer(0, 5)
        vm.setMeqAnswer(1, 5)
        vm.setMeqAnswer(2, 4)
        vm.setMeqAnswer(3, 5)
        vm.setMeqAnswer(4, 6)
        assertThat(vm.state.value.meqScore).isEqualTo(25)
        assertThat(vm.state.value.chronotype).isEqualTo(Chronotype.DEFINITE_MORNING)
    }

    @Test
    fun `meq all minimum answers score 4 definite evening`() {
        val vm = viewModel()
        vm.setMeqAnswer(0, 1)
        vm.setMeqAnswer(1, 1)
        vm.setMeqAnswer(2, 1)
        vm.setMeqAnswer(3, 1)
        vm.setMeqAnswer(4, 0)
        assertThat(vm.state.value.meqScore).isEqualTo(4)
        assertThat(vm.state.value.chronotype).isEqualTo(Chronotype.DEFINITE_EVENING)
    }

    @Test
    fun `meq band boundaries map to the correct chronotype`() {
        // 17 → MODERATE_MORNING (17..20)
        val vm17 = viewModel()
        vm17.setMeqAnswer(0, 4)
        vm17.setMeqAnswer(1, 4)
        vm17.setMeqAnswer(2, 3)
        vm17.setMeqAnswer(3, 3)
        vm17.setMeqAnswer(4, 3)
        assertThat(vm17.state.value.meqScore).isEqualTo(17)
        assertThat(vm17.state.value.chronotype).isEqualTo(Chronotype.MODERATE_MORNING)

        // 12 → MODERATE_EVENING (9..12)
        val vm12 = viewModel()
        vm12.setMeqAnswer(0, 3)
        vm12.setMeqAnswer(1, 3)
        vm12.setMeqAnswer(2, 2)
        vm12.setMeqAnswer(3, 2)
        vm12.setMeqAnswer(4, 2)
        assertThat(vm12.state.value.meqScore).isEqualTo(12)
        assertThat(vm12.state.value.chronotype).isEqualTo(Chronotype.MODERATE_EVENING)
    }

    @Test
    fun `re-answering a meq question overwrites instead of double counting`() {
        val vm = viewModel()
        vm.setMeqAnswer(0, 5)
        vm.setMeqAnswer(0, 1) // Q0 re-answered; must overwrite, not add a 6th answer.
        vm.setMeqAnswer(1, 5)
        vm.setMeqAnswer(2, 4)
        vm.setMeqAnswer(3, 5)
        vm.setMeqAnswer(4, 6)
        assertThat(vm.state.value.meqAnswers).hasSize(5)
        assertThat(vm.state.value.meqScore).isEqualTo(21) // 1+5+4+5+6
        assertThat(vm.state.value.chronotype).isEqualTo(Chronotype.DEFINITE_MORNING)
    }

    // --- LIFE_STAGE skip logic (forward + backward) ---

    @Test
    fun `non female skips life stage going forward`() {
        val vm = viewModel() // default NOT_SPECIFIED
        vm.nextStep() // WELCOME -> BIRTH_DATE
        assertThat(vm.state.value.currentStep).isEqualTo(OnboardingStep.BIRTH_DATE)
        vm.nextStep() // -> BIOLOGICAL_SEX
        vm.nextStep() // must skip LIFE_STAGE -> NEURODIVERGENT
        assertThat(vm.state.value.currentStep).isEqualTo(OnboardingStep.NEURODIVERGENT)
    }

    @Test
    fun `female lands on life stage going forward`() {
        val vm = viewModel()
        vm.setBiologicalSex(BiologicalSex.FEMALE)
        vm.nextStep() // WELCOME -> BIRTH_DATE
        vm.nextStep() // -> BIOLOGICAL_SEX
        vm.nextStep() // -> LIFE_STAGE
        assertThat(vm.state.value.currentStep).isEqualTo(OnboardingStep.LIFE_STAGE)
    }

    @Test
    fun `non female skips life stage going backward`() {
        val vm = viewModel()
        vm.nextStep()
        vm.nextStep()
        vm.nextStep() // -> NEURODIVERGENT (LIFE_STAGE skipped)
        assertThat(vm.state.value.currentStep).isEqualTo(OnboardingStep.NEURODIVERGENT)
        vm.previousStep() // must skip LIFE_STAGE -> BIOLOGICAL_SEX
        assertThat(vm.state.value.currentStep).isEqualTo(OnboardingStep.BIOLOGICAL_SEX)
    }

    @Test
    fun `female lands on life stage going backward`() {
        val vm = viewModel()
        vm.setBiologicalSex(BiologicalSex.FEMALE)
        vm.nextStep()
        vm.nextStep()
        vm.nextStep() // -> LIFE_STAGE
        assertThat(vm.state.value.currentStep).isEqualTo(OnboardingStep.LIFE_STAGE)
        vm.nextStep() // -> NEURODIVERGENT
        vm.previousStep() // back to LIFE_STAGE
        assertThat(vm.state.value.currentStep).isEqualTo(OnboardingStep.LIFE_STAGE)
    }

    @Test
    fun `next step at the end stays put`() {
        val vm = viewModel()
        repeat(8) { vm.nextStep() } // lands on COMPLETE (LIFE_STAGE skipped for NOT_SPECIFIED)
        assertThat(vm.state.value.currentStep).isEqualTo(OnboardingStep.COMPLETE)
        vm.nextStep()
        assertThat(vm.state.value.currentStep).isEqualTo(OnboardingStep.COMPLETE)
    }

    @Test
    fun `previous step at welcome stays put`() {
        val vm = viewModel()
        assertThat(vm.state.value.currentStep).isEqualTo(OnboardingStep.WELCOME)
        vm.previousStep()
        assertThat(vm.state.value.currentStep).isEqualTo(OnboardingStep.WELCOME)
    }

    // --- setDateOfBirth → recommended sleep target ---

    @Test
    fun `setDateOfBirth under 13 auto sets the age band target`() {
        val vm = viewModel()
        vm.setDateOfBirth(LocalDate.now().minusYears(6))
        assertThat(vm.state.value.targetSleepHours).isEqualTo(10.0f) // 6-12 band
    }

    @Test
    fun `setDateOfBirth adult auto sets 8 hours`() {
        val vm = viewModel()
        vm.setDateOfBirth(LocalDate.now().minusYears(30))
        assertThat(vm.state.value.targetSleepHours).isEqualTo(8.0f) // 19-64 band
    }

    // --- derived input getters (the cycle/pregnancy conditional-input seam) ---

    @Test
    fun `derived input getters track sex and stage`() {
        val vm = viewModel()
        assertThat(vm.state.value.showLifeStageStep).isFalse() // NOT_SPECIFIED default
        assertThat(vm.state.value.showCycleInput).isFalse()
        assertThat(vm.state.value.showPregnancyInput).isFalse()

        vm.setBiologicalSex(BiologicalSex.FEMALE)
        assertThat(vm.state.value.showLifeStageStep).isTrue()

        vm.setLifeStage(LifeStage.CYCLING)
        assertThat(vm.state.value.showCycleInput).isTrue()
        assertThat(vm.state.value.showPregnancyInput).isFalse()

        vm.setLifeStage(LifeStage.PREGNANT)
        assertThat(vm.state.value.showCycleInput).isFalse()
        assertThat(vm.state.value.showPregnancyInput).isTrue()
    }

    // --- setLifeStage opt-out clearing (DEFAULT-only, preserving stage switches) ---

    @Test
    fun `opting out to default clears cycle inputs`() {
        val vm = viewModel()
        vm.setLifeStage(LifeStage.CYCLING)
        vm.setCycleLength(25)
        vm.setLastPeriodStart(LocalDate.of(2026, 7, 20))
        vm.setLifeStage(LifeStage.DEFAULT)
        assertThat(vm.state.value.cycleLength).isEqualTo(28)
        assertThat(vm.state.value.lastPeriodStart).isNull()
    }

    @Test
    fun `opting out to default clears pregnancy trimester`() {
        val vm = viewModel()
        vm.setLifeStage(LifeStage.PREGNANT)
        vm.setPregnancyTrimester(2)
        vm.setLifeStage(LifeStage.DEFAULT)
        assertThat(vm.state.value.pregnancyTrimester).isNull()
    }

    @Test
    fun `switching between stages preserves entered data`() {
        val vm = viewModel()
        vm.setLifeStage(LifeStage.CYCLING)
        vm.setCycleLength(25)
        vm.setLastPeriodStart(LocalDate.of(2026, 7, 20))
        vm.setLifeStage(LifeStage.POSTPARTUM) // not an opt-out — must not wipe the data
        assertThat(vm.state.value.cycleLength).isEqualTo(25)
        assertThat(vm.state.value.lastPeriodStart).isEqualTo(LocalDate.of(2026, 7, 20))
    }

    // --- completeOnboarding mapping ---

    @Test
    fun `completeOnboarding persists a fully populated profile`() = runTest {
        val dao = FakeUserProfileDao()
        val vm = viewModel(dao)
        val dob = LocalDate.now().minusYears(6)
        vm.setDateOfBirth(dob)
        vm.setBiologicalSex(BiologicalSex.FEMALE)
        vm.setLifeStage(LifeStage.CYCLING)
        vm.setCycleLength(25)
        vm.setLastPeriodStart(LocalDate.of(2026, 7, 20))
        vm.setAdhdMode(true)
        vm.setMeqAnswer(0, 5)
        vm.setMeqAnswer(1, 5)
        vm.setMeqAnswer(2, 4)
        vm.setMeqAnswer(3, 5)
        vm.setMeqAnswer(4, 6)
        vm.setTargetSleepHours(9.5f)

        var completed = false
        runWithMain {
            vm.completeOnboarding { completed = true }
        }

        assertThat(completed).isTrue()
        assertThat(vm.state.value.isSaving).isFalse()
        val saved = requireNotNull(dao.savedProfile)
        assertThat(saved.dateOfBirth).isEqualTo(dob.toString())
        assertThat(saved.biologicalSex).isEqualTo("FEMALE")
        assertThat(saved.lifeStage).isEqualTo("CYCLING")
        assertThat(saved.cycleLength).isEqualTo(25)
        assertThat(saved.lastPeriodStartDate).isEqualTo("2026-07-20")
        assertThat(saved.adhdMode).isTrue()
        assertThat(saved.medicationTracking).isTrue() // auto-enabled with ADHD mode
        assertThat(saved.chronotype).isEqualTo("DEFINITE_MORNING")
        assertThat(saved.chronotypeMeqScore).isEqualTo(25)
        assertThat(saved.targetSleepHours).isEqualTo(9.5f)
        assertThat(saved.onboardingCompleted).isTrue()
    }

    @Test
    fun `completeOnboarding without quiz keeps chronotype unknown and defaults intact`() =
        runTest {
            val dao = FakeUserProfileDao()
            val vm = viewModel(dao)
            runWithMain {
                vm.completeOnboarding {}
            }
            val saved = requireNotNull(dao.savedProfile)
            assertThat(saved.chronotype).isEqualTo("UNKNOWN")
            assertThat(saved.chronotypeMeqScore).isNull()
            assertThat(saved.biologicalSex).isEqualTo("NOT_SPECIFIED")
            assertThat(saved.lifeStage).isEqualTo("DEFAULT")
            assertThat(saved.targetSleepHours).isEqualTo(8.0f)
            assertThat(saved.medicationTracking).isFalse()
            assertThat(saved.onboardingCompleted).isTrue()
        }
}

/** In-memory [UserProfileDao] — captures the last upsert so tests can assert the mapping. */
private class FakeUserProfileDao : UserProfileDao {
    var savedProfile: UserProfileEntity? = null

    override suspend fun upsert(profile: UserProfileEntity) {
        savedProfile = profile
    }

    override suspend fun getProfile(): UserProfileEntity? = savedProfile

    override fun observeProfile(): Flow<UserProfileEntity?> = flowOf(savedProfile)

    override suspend fun isOnboardingCompleted(): Boolean? = savedProfile?.onboardingCompleted

    override fun observeOnboardingCompleted(): Flow<Boolean?> =
        flowOf(savedProfile?.onboardingCompleted)

    override suspend fun markOnboardingCompleted() {
        savedProfile = savedProfile?.copy(onboardingCompleted = true)
    }

    override suspend fun updateLastPeriodStart(date: String) {
        savedProfile = savedProfile?.copy(lastPeriodStartDate = date)
    }

    override suspend fun updatePregnancyTrimester(trimester: Int) {
        savedProfile = savedProfile?.copy(pregnancyTrimester = trimester)
    }
}
