package dev.vic41148.somn.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vic41148.somn.core.data.repository.UserProfileRepository
import dev.vic41148.somn.core.domain.model.BiologicalSex
import dev.vic41148.somn.core.domain.model.Chronotype
import dev.vic41148.somn.core.domain.model.LifeStage
import dev.vic41148.somn.core.domain.model.NeurodivergentProfile
import dev.vic41148.somn.core.domain.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val profileRepository: UserProfileRepository
) : ViewModel() {

    data class OnboardingState(
        val currentStep: OnboardingStep = OnboardingStep.WELCOME,
        val dateOfBirth: LocalDate? = null,
        val biologicalSex: BiologicalSex = BiologicalSex.NOT_SPECIFIED,
        val lifeStage: LifeStage = LifeStage.DEFAULT,
        val adhdMode: Boolean = false,
        val asdMode: Boolean = false,
        val chronotype: Chronotype = Chronotype.UNKNOWN,
        val meqScore: Int? = null,
        val meqAnswers: Map<Int, Int> = emptyMap(),
        val targetSleepHours: Float = 8.0f,
        val cycleLength: Int = 28,
        val lastPeriodStart: LocalDate? = null,
        val pregnancyTrimester: Int? = null,
        val isSaving: Boolean = false
    ) {
        val showLifeStageStep: Boolean
            get() = biologicalSex == BiologicalSex.FEMALE

        val showCycleInput: Boolean
            get() = lifeStage == LifeStage.CYCLING

        val showPregnancyInput: Boolean
            get() = lifeStage == LifeStage.PREGNANT

        val progress: Float
            get() {
                val steps = OnboardingStep.entries
                val currentIndex = steps.indexOf(currentStep)
                return (currentIndex + 1).toFloat() / steps.size
            }
    }

    enum class OnboardingStep {
        WELCOME,
        BIRTH_DATE,
        BIOLOGICAL_SEX,
        LIFE_STAGE,
        NEURODIVERGENT,
        CHRONOTYPE_QUIZ,
        SLEEP_GOAL,
        PERMISSIONS,
        COMPLETE
    }

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun setDateOfBirth(date: LocalDate) {
        _state.update { it.copy(dateOfBirth = date) }
        // Auto-set recommended sleep target based on age. This used to duplicate a subset of
        // UserProfile.recommendedSleepHours's age brackets inline (only 13-18/19-64/else),
        // collapsing every age under 13 into the 65+ "7.5h" bucket — a young child would be
        // recommended *less* sleep than an adult instead of the 10-14h they actually need.
        // Deferring to the canonical property keeps this in sync with the one source of truth.
        val recommended = UserProfile(dateOfBirth = date).recommendedSleepHours
        _state.update { it.copy(targetSleepHours = recommended) }
    }

    fun setBiologicalSex(sex: BiologicalSex) {
        _state.update { it.copy(biologicalSex = sex) }
    }

    fun setLifeStage(stage: LifeStage) {
        _state.update { it.copy(lifeStage = stage) }
    }

    fun setAdhdMode(enabled: Boolean) {
        _state.update { it.copy(adhdMode = enabled) }
    }

    fun setAsdMode(enabled: Boolean) {
        _state.update { it.copy(asdMode = enabled) }
    }

    fun setMeqAnswer(questionIndex: Int, answer: Int) {
        _state.update { state ->
            val answers = state.meqAnswers.toMutableMap()
            answers[questionIndex] = answer
            val totalScore = if (answers.size == 5) answers.values.sum() else null
            state.copy(
                meqAnswers = answers,
                meqScore = totalScore,
                chronotype = totalScore?.let { Chronotype.fromMeqScore(it) } ?: Chronotype.UNKNOWN
            )
        }
    }

    fun setTargetSleepHours(hours: Float) {
        _state.update { it.copy(targetSleepHours = hours) }
    }

    fun setCycleLength(days: Int) {
        _state.update { it.copy(cycleLength = days) }
    }

    fun setLastPeriodStart(date: LocalDate) {
        _state.update { it.copy(lastPeriodStart = date) }
    }

    fun setPregnancyTrimester(trimester: Int) {
        _state.update { it.copy(pregnancyTrimester = trimester) }
    }

    fun nextStep() {
        _state.update { state ->
            val steps = OnboardingStep.entries
            val currentIndex = steps.indexOf(state.currentStep)
            var nextIndex = currentIndex + 1

            // Skip life stage step if not female
            if (steps.getOrNull(nextIndex) == OnboardingStep.LIFE_STAGE && !state.showLifeStageStep) {
                nextIndex++
            }

            val nextStep = steps.getOrNull(nextIndex) ?: state.currentStep
            state.copy(currentStep = nextStep)
        }
    }

    fun previousStep() {
        _state.update { state ->
            val steps = OnboardingStep.entries
            val currentIndex = steps.indexOf(state.currentStep)
            var prevIndex = currentIndex - 1

            // Skip life stage step if not female
            if (steps.getOrNull(prevIndex) == OnboardingStep.LIFE_STAGE && !state.showLifeStageStep) {
                prevIndex--
            }

            val prevStep = steps.getOrNull(prevIndex) ?: state.currentStep
            state.copy(currentStep = prevStep)
        }
    }

    fun completeOnboarding(onComplete: () -> Unit) {
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val s = _state.value
            val profile = UserProfile(
                dateOfBirth = s.dateOfBirth,
                biologicalSex = s.biologicalSex,
                lifeStage = s.lifeStage,
                chronotype = s.chronotype,
                chronotypeMeqScore = s.meqScore,
                neurodivergentProfile = NeurodivergentProfile(
                    adhdMode = s.adhdMode,
                    asdMode = s.asdMode,
                    medicationTracking = s.adhdMode // auto-enable if ADHD mode is on
                ),
                targetSleepHours = s.targetSleepHours,
                cycleLength = s.cycleLength,
                lastPeriodStartDate = s.lastPeriodStart,
                pregnancyTrimester = s.pregnancyTrimester,
                onboardingCompleted = true
            )
            profileRepository.saveProfile(profile)
            _state.update { it.copy(isSaving = false) }
            onComplete()
        }
    }
}
