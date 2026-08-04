package dev.vic41148.somn.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.vic41148.somn.core.data.repository.SleepRepository
import dev.vic41148.somn.core.data.repository.SomnPreferencesRepository
import dev.vic41148.somn.core.data.repository.UserProfileRepository
import dev.vic41148.somn.core.domain.model.ChronotypeAssessment
import dev.vic41148.somn.core.domain.model.SeasonalAnalysis
import dev.vic41148.somn.core.domain.model.SocialJetLag
import dev.vic41148.somn.core.domain.usecase.ChronotypeAssessmentUseCase
import dev.vic41148.somn.core.domain.usecase.SeasonalAnalysisUseCase
import dev.vic41148.somn.core.domain.usecase.SocialJetLagUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CircadianViewModel @Inject constructor(
    private val sleepRepository: SleepRepository,
    private val userProfileRepository: UserProfileRepository,
    private val preferencesRepository: SomnPreferencesRepository,
    private val chronotypeAssessmentUseCase: ChronotypeAssessmentUseCase,
    private val socialJetLagUseCase: SocialJetLagUseCase,
    private val seasonalAnalysisUseCase: SeasonalAnalysisUseCase
) : ViewModel() {

    private val _chronotypeAssessment = MutableStateFlow<ChronotypeAssessment?>(null)
    val chronotypeAssessment: StateFlow<ChronotypeAssessment?> = _chronotypeAssessment.asStateFlow()

    private val _socialJetLag = MutableStateFlow<SocialJetLag?>(null)
    val socialJetLag: StateFlow<SocialJetLag?> = _socialJetLag.asStateFlow()

    private val _seasonalAnalysis = MutableStateFlow<SeasonalAnalysis?>(null)
    val seasonalAnalysis: StateFlow<SeasonalAnalysis?> = _seasonalAnalysis.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                combine(
                    userProfileRepository.observeProfile(),
                    // SESS-04: chronotype/social-jetlag/seasonal analysis is bedtime-based —
                    // naps/commute/shift sessions would skew it.
                    sleepRepository.observeMainSleepSessions(),
                    // Seasonal analysis can be pinned to a hemisphere; a change to the override
                    // re-runs the analysis immediately, not on the next screen open.
                    preferencesRepository.hemisphereOverride
                ) { profile, sessions, hemisphereOverride ->
                    Triple(profile, sessions, hemisphereOverride)
                }.collect { (profile, sessions, hemisphereOverride) ->
                    if (profile == null) return@collect

                    _chronotypeAssessment.value = chronotypeAssessmentUseCase.assess(profile, sessions)
                    _socialJetLag.value = socialJetLagUseCase.calculate(sessions)
                    _seasonalAnalysis.value =
                        seasonalAnalysisUseCase.analyze(sessions, hemisphereOverride = hemisphereOverride)
                    _errorMessage.value = null
                }
            } catch (e: Exception) {
                // Previously silently ignored ("Ignore for now") — any exception in
                // chronotype/social-jetlag/seasonal analysis left the screen showing stale or
                // empty state forever with zero indication anything went wrong.
                _errorMessage.value = "Couldn't load circadian insights: ${e.message ?: e::class.simpleName}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
