package dev.vic41148.somn.feature.onboarding.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.vic41148.somn.feature.onboarding.OnboardingViewModel
import dev.vic41148.somn.feature.onboarding.OnboardingViewModel.OnboardingStep

/**
 * Root composable that orchestrates the multi-step onboarding flow.
 * Manages animated transitions between steps and back navigation.
 */
@Composable
fun OnboardingFlow(
    onOnboardingComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    BackHandler(enabled = state.currentStep != OnboardingStep.WELCOME) {
        viewModel.previousStep()
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Progress bar (hidden on welcome and complete)
        if (state.currentStep !in listOf(OnboardingStep.WELCOME, OnboardingStep.COMPLETE)) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }

        AnimatedContent(
            targetState = state.currentStep,
            transitionSpec = {
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally { fullWidth -> direction * fullWidth } + fadeIn())
                    .togetherWith(slideOutHorizontally { fullWidth -> -direction * fullWidth } + fadeOut())
            },
            modifier = Modifier.fillMaxSize(),
            label = "onboarding_step"
        ) { step ->
            when (step) {
                OnboardingStep.WELCOME -> WelcomeScreen(
                    onNext = { viewModel.nextStep() }
                )
                OnboardingStep.BIRTH_DATE -> BirthDateScreen(
                    selectedDate = state.dateOfBirth,
                    onDateSelected = { viewModel.setDateOfBirth(it) },
                    onNext = { viewModel.nextStep() },
                    onBack = { viewModel.previousStep() }
                )
                OnboardingStep.BIOLOGICAL_SEX -> BiologicalSexScreen(
                    selected = state.biologicalSex,
                    onSelected = { viewModel.setBiologicalSex(it) },
                    onNext = { viewModel.nextStep() },
                    onBack = { viewModel.previousStep() }
                )
                OnboardingStep.LIFE_STAGE -> LifeStageScreen(
                    selected = state.lifeStage,
                    onSelected = { viewModel.setLifeStage(it) },
                    cycleLength = state.cycleLength,
                    onCycleLengthChanged = { viewModel.setCycleLength(it) },
                    lastPeriodStart = state.lastPeriodStart,
                    onLastPeriodStartChanged = { viewModel.setLastPeriodStart(it) },
                    pregnancyTrimester = state.pregnancyTrimester,
                    onPregnancyTrimesterChanged = { viewModel.setPregnancyTrimester(it) },
                    onNext = { viewModel.nextStep() },
                    onBack = { viewModel.previousStep() }
                )
                OnboardingStep.NEURODIVERGENT -> NeurodivergentScreen(
                    adhdEnabled = state.adhdMode,
                    onAdhdToggled = { viewModel.setAdhdMode(it) },
                    asdEnabled = state.asdMode,
                    onAsdToggled = { viewModel.setAsdMode(it) },
                    onNext = { viewModel.nextStep() },
                    onBack = { viewModel.previousStep() }
                )
                OnboardingStep.CHRONOTYPE_QUIZ -> ChronotypeQuizScreen(
                    answers = state.meqAnswers,
                    chronotype = state.chronotype,
                    onAnswer = { question, answer -> viewModel.setMeqAnswer(question, answer) },
                    onNext = { viewModel.nextStep() },
                    onBack = { viewModel.previousStep() }
                )
                OnboardingStep.SLEEP_GOAL -> SleepGoalScreen(
                    targetHours = state.targetSleepHours,
                    recommendedHours = state.dateOfBirth?.let {
                        val age = java.time.Period.between(it, java.time.LocalDate.now()).years
                        when (age) {
                            in 13..18 -> 9.0f
                            in 19..64 -> 8.0f
                            else -> 7.5f
                        }
                    } ?: 8.0f,
                    onTargetChanged = { viewModel.setTargetSleepHours(it) },
                    onNext = { viewModel.nextStep() },
                    onBack = { viewModel.previousStep() }
                )
                OnboardingStep.PERMISSIONS -> PermissionsScreen(
                    onNext = { viewModel.nextStep() },
                    onBack = { viewModel.previousStep() }
                )
                OnboardingStep.COMPLETE -> OnboardingCompleteScreen(
                    state = state,
                    isSaving = state.isSaving,
                    onComplete = { viewModel.completeOnboarding(onOnboardingComplete) }
                )
            }
        }
    }
}
