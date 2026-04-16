package dev.vic41148.somn.feature.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vic41148.somn.core.data.repository.SleepRepository
import dev.vic41148.somn.core.domain.model.SleepEpoch
import dev.vic41148.somn.core.domain.model.SleepScore
import dev.vic41148.somn.core.domain.model.SleepSession
import dev.vic41148.somn.core.domain.model.SleepStage
import dev.vic41148.somn.core.domain.model.AudioEvent
import dev.vic41148.somn.core.domain.model.TrackingMode
import dev.vic41148.somn.core.domain.usecase.CalculateSleepScoreUseCase
import dev.vic41148.somn.core.domain.usecase.ClassifySleepStageUseCase
import dev.vic41148.somn.feature.tracking.service.SleepTrackingService
import dev.vic41148.somn.feature.tracking.service.TrackingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SleepTrackingViewModel @Inject constructor(
    private val sleepRepository: SleepRepository,
    private val calculateScore: CalculateSleepScoreUseCase,
    private val classifyStage: ClassifySleepStageUseCase
) : ViewModel() {

    val trackingState: StateFlow<TrackingState> = SleepTrackingService.trackingState
    val sonarCalibrationState = SleepTrackingService.sonarCalibrationState
    val activeTrackingMode    = SleepTrackingService.activeTrackingMode

    private val _lastSession = MutableStateFlow<SleepSession?>(null)
    val lastSession: StateFlow<SleepSession?> = _lastSession.asStateFlow()

    private val _lastScore = MutableStateFlow<SleepScore?>(null)
    val lastScore: StateFlow<SleepScore?> = _lastScore.asStateFlow()

    private val _epochs = MutableStateFlow<List<SleepEpoch>>(emptyList())
    val epochs: StateFlow<List<SleepEpoch>> = _epochs.asStateFlow()

    private val _audioEvents = MutableStateFlow<List<AudioEvent>>(emptyList())
    val audioEvents: StateFlow<List<AudioEvent>> = _audioEvents.asStateFlow()

    val activeSession = sleepRepository.observeActiveSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        loadLastSession()
    }

    private fun loadLastSession() {
        viewModelScope.launch {
            val sessions = sleepRepository.getRecentSessions(1)
            if (sessions.isNotEmpty()) {
                val session = sessions.first()
                _lastSession.value = session
                if (session.sleepScore > 0) {
                    _lastScore.value = calculateScore(session)
                }
                _audioEvents.value = sleepRepository.getAudioEvents(session.id)
            }
        }
    }

    fun startTracking(context: android.content.Context, mode: TrackingMode = TrackingMode.ACCELEROMETER) {
        viewModelScope.launch {
            val sessionId = sleepRepository.createSession(System.currentTimeMillis())
            SleepTrackingService.startTracking(context, sessionId, mode)

            // Observe epochs for real-time display
            launch {
                sleepRepository.observeEpochs(sessionId).collect { epochList ->
                    _epochs.value = epochList
                }
            }
        }
    }

    fun stopTracking(context: android.content.Context) {
        viewModelScope.launch {
            SleepTrackingService.stopTracking(context)

            val session = sleepRepository.getActiveSession() ?: return@launch
            val epochs = sleepRepository.getEpochs(session.id)

            // Calculate metrics from epoch timestamps (not epoch count)
            val now = System.currentTimeMillis()
            // Count audio events
            val sessionAudioEvents = sleepRepository.getAudioEvents(session.id)
            _audioEvents.value = sessionAudioEvents

            val sleepEpochs = epochs.filter { it.stage != SleepStage.AWAKE }
            val deepEpochs = epochs.filter { it.stage == SleepStage.DEEP }
            val lightEpochs = epochs.filter { it.stage == SleepStage.LIGHT }
            val remEpochs = epochs.filter { it.stage == SleepStage.REM }

            // Time in bed from wall clock
            val timeInBed = ((now - session.startTimeMillis) / 60_000).toInt()

            // Sleep duration: use actual timestamps of sleep epochs
            // Each epoch represents a 30-second window starting at its timestamp
            val sleepDuration = if (sleepEpochs.isNotEmpty()) {
                // Sum contiguous sleep blocks using epoch timestamps
                // Each epoch covers 30 seconds from its timestamp
                val epochDurationMs = 30_000L
                val totalSleepMs = sleepEpochs.size * epochDurationMs
                (totalSleepMs / 60_000).toInt()
            } else {
                0
            }

            // Efficiency: sleep duration / time in bed, clamped to 0-100%
            val efficiency = if (timeInBed > 0) {
                (sleepDuration.toFloat() / timeInBed * 100).coerceIn(0f, 100f)
            } else 0f

            // Sleep stage percentages (relative to total sleep epochs, not all epochs)
            val totalSleepEpochs = sleepEpochs.size
            val deepPercent = if (totalSleepEpochs > 0) (deepEpochs.size.toFloat() / totalSleepEpochs * 100) else 0f
            val lightPercent = if (totalSleepEpochs > 0) (lightEpochs.size.toFloat() / totalSleepEpochs * 100) else 0f
            val remPercent = if (totalSleepEpochs > 0) (remEpochs.size.toFloat() / totalSleepEpochs * 100) else 0f

            // Count wake events (transitions from sleep to awake)
            var wakeEvents = 0
            for (i in 1 until epochs.size) {
                if (epochs[i].stage == SleepStage.AWAKE && epochs[i - 1].stage != SleepStage.AWAKE) {
                    wakeEvents++
                }
            }

            // Sleep onset: time to first non-awake epoch
            val firstSleepEpoch = epochs.firstOrNull { it.stage != SleepStage.AWAKE }
            val onsetMinutes = if (firstSleepEpoch != null) {
                ((firstSleepEpoch.timestampMillis - session.startTimeMillis) / 60_000).toInt()
            } else 0

            val completedSession = session.copy(
                endTimeMillis = now,
                sleepDurationMinutes = sleepDuration,
                timeInBedMinutes = timeInBed,
                sleepEfficiency = efficiency,
                sleepOnsetMinutes = onsetMinutes,
                wakeEvents = wakeEvents,
                deepSleepPercent = deepPercent,
                lightSleepPercent = lightPercent,
                remSleepPercent = remPercent,
                avgBreathingRateBrpm = dev.vic41148.somn.feature.tracking.service.SleepTrackingService.currentAvgBrpm.value,
                coughEventCount = _audioEvents.value.count { it.type == dev.vic41148.somn.core.domain.model.AudioEventType.COUGH },
                isCompleted = true
            )

            // Calculate score
            val score = calculateScore(completedSession)
            val scoredSession = completedSession.copy(sleepScore = score.totalScore)

            sleepRepository.completeSession(scoredSession)
            _lastSession.value = scoredSession
            _lastScore.value = score
        }
    }

    fun updateMood(sessionId: Long, mood: Int) {
        viewModelScope.launch {
            val session = sleepRepository.getSession(sessionId) ?: return@launch
            sleepRepository.updateSession(session.copy(moodRating = mood))
        }
    }

    fun updateNotes(sessionId: Long, notes: String) {
        viewModelScope.launch {
            val session = sleepRepository.getSession(sessionId) ?: return@launch
            sleepRepository.updateSession(session.copy(notes = notes))
        }
    }
}
