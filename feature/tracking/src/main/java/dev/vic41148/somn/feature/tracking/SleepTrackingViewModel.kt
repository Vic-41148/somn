package dev.vic41148.somn.feature.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vic41148.somn.core.data.repository.HealthConnectRepository
import dev.vic41148.somn.core.data.repository.SleepRepository
import dev.vic41148.somn.core.data.repository.SomnPreferencesRepository
import dev.vic41148.somn.core.data.repository.UserProfileRepository
import dev.vic41148.somn.core.domain.model.LifeStage
import dev.vic41148.somn.core.domain.model.SessionType
import dev.vic41148.somn.core.domain.model.SleepEpoch
import dev.vic41148.somn.core.domain.model.SleepScore
import dev.vic41148.somn.core.domain.model.SleepSession
import dev.vic41148.somn.core.domain.model.SleepStage
import dev.vic41148.somn.core.domain.model.AudioEvent
import dev.vic41148.somn.core.domain.model.TrackingMode
import dev.vic41148.somn.core.domain.usecase.CalculateSleepScoreUseCase
import dev.vic41148.somn.core.domain.usecase.ClassifySleepStageUseCase
import dev.vic41148.somn.core.domain.usecase.PostpartumFragmentationUseCase
import dev.vic41148.somn.core.notifications.DeepSleepAlertNotifier
import dev.vic41148.somn.core.notifications.HormonalPhaseNotifier
import dev.vic41148.somn.core.notifications.PPDRiskNotifier
import dev.vic41148.somn.feature.tracking.service.SleepTrackingService
import dev.vic41148.somn.feature.tracking.service.TrackingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SleepTrackingViewModel @Inject constructor(
    private val sleepRepository: SleepRepository,
    private val userProfileRepository: UserProfileRepository,
    private val preferencesRepository: SomnPreferencesRepository,
    private val healthConnectRepository: HealthConnectRepository,
    private val calculateScore: CalculateSleepScoreUseCase,
    private val classifyStage: ClassifySleepStageUseCase,
    private val fragmentationUseCase: PostpartumFragmentationUseCase,
    private val deepSleepAlertNotifier: DeepSleepAlertNotifier,
    private val hormonalPhaseNotifier: HormonalPhaseNotifier,
    private val ppdRiskNotifier: PPDRiskNotifier
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
        checkIncompleteSession()
        loadLastSession()
    }

    /**
     * REL-02: detects a tracking session abandoned by a dead/killed [SleepTrackingService]
     * (no [stopTracking] ever called) and finalizes it as partial data rather than leaving
     * it stuck open forever. Only runs when this process doesn't itself believe tracking is
     * active — a live service in this process always keeps [SleepTrackingService.trackingState]
     * at [TrackingState.TRACKING], so this can't race a genuinely in-progress night.
     */
    private fun checkIncompleteSession() {
        viewModelScope.launch {
            if (SleepTrackingService.trackingState.value != TrackingState.IDLE) return@launch

            val session = sleepRepository.getActiveSession() ?: return@launch
            val latestEpoch = sleepRepository.getLatestEpoch(session.id)
            val referenceMillis = latestEpoch?.timestampMillis ?: session.startTimeMillis
            val staleThresholdMillis = 2 * EPOCH_DURATION_MS

            if (System.currentTimeMillis() - referenceMillis > staleThresholdMillis) {
                finalizeIncompleteSession(session)
            }
        }
    }

    private suspend fun finalizeIncompleteSession(session: SleepSession) {
        // REL-02: the held-back final epoch dies with a hard-killed process (it lives in the
        // service's memory), but an interrupted stop — the normal stop path dying mid-flight in
        // THIS process — leaves it pending in the companion flow. Recover it exactly like
        // [stopTracking] does, before reading the epoch list back, so the recovered session
        // includes every epoch this process still could write.
        flushPendingFinalEpoch()

        val epochs = sleepRepository.getEpochs(session.id)
        val audioEventsForSession = sleepRepository.getAudioEvents(session.id)
        val endTimeMillis = epochs.maxOfOrNull { it.timestampMillis } ?: session.startTimeMillis
        val (targetSleepMinutes, oversleepThresholdMinutes) = loadOversleepInputs()

        val partialSession = buildCompletedSession(
            session = session,
            epochs = epochs,
            coughCount = audioEventsForSession.count { it.type == dev.vic41148.somn.core.domain.model.AudioEventType.COUGH },
            endTimeMillis = endTimeMillis,
            avgBrpm = session.avgBreathingRateBrpm,
            isPartial = true,
            targetSleepMinutes = targetSleepMinutes,
            oversleepThresholdMinutes = oversleepThresholdMinutes
        )

        val score = calculateScore(partialSession)
        sleepRepository.completeSession(partialSession.copy(sleepScore = score.totalScore))
    }

    private fun loadLastSession() {
        viewModelScope.launch {
            // SESS-04: "last night" should show the last main-sleep session, not a same-day nap.
            val sessions = sleepRepository.getRecentMainSleepSessions(1)
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

    fun startTracking(
        context: android.content.Context,
        mode: TrackingMode = TrackingMode.ACCELEROMETER,
        sessionType: dev.vic41148.somn.core.domain.model.SessionType =
            dev.vic41148.somn.core.domain.model.SessionType.MAIN_SLEEP
    ) {
        viewModelScope.launch {
            val sessionId = sleepRepository.createSession(
                System.currentTimeMillis(),
                sessionType = sessionType
            )
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

            // REL-02: the service no longer flushes its held-back final epoch with a runBlocking on
            // the main thread (that wedged Room's executors during teardown and hung every later
            // query — including the morning alerts below). Instead it exposes the epoch here and the
            // ViewModel writes it, synchronously and in order, before reading the epoch list back.
            flushPendingFinalEpoch()

            val epochs = sleepRepository.getEpochs(session.id)
            val now = System.currentTimeMillis()

            val sessionAudioEvents = sleepRepository.getAudioEvents(session.id)
            _audioEvents.value = sessionAudioEvents
            val (targetSleepMinutes, oversleepThresholdMinutes) = loadOversleepInputs()

            val completedSession = buildCompletedSession(
                session = session,
                epochs = epochs,
                coughCount = sessionAudioEvents.count { it.type == dev.vic41148.somn.core.domain.model.AudioEventType.COUGH },
                endTimeMillis = now,
                avgBrpm = SleepTrackingService.currentAvgBrpm.value,
                isPartial = false,
                targetSleepMinutes = targetSleepMinutes,
                oversleepThresholdMinutes = oversleepThresholdMinutes
            )

            // Calculate score
            val score = calculateScore(completedSession)
            val scoredSession = completedSession.copy(sleepScore = score.totalScore)

            sleepRepository.completeSession(scoredSession)
            _lastSession.value = scoredSession
            _lastScore.value = score

            notifyMorningAlerts(scoredSession)
            syncToHealthConnect(scoredSession, epochs)

            // Trigger NAS sync (silent backup + NAS upload + prune)
            try {
                val request = androidx.work.OneTimeWorkRequestBuilder<dev.vic41148.somn.core.data.backup.NasSyncWorker>()
                    .addTag(dev.vic41148.somn.core.data.backup.NasSyncWorker.TAG)
                    .build()
                androidx.work.WorkManager.getInstance(context).enqueue(request)
            } catch (e: Exception) {
                android.util.Log.e("SleepTrackingViewModel", "Failed to enqueue NAS sync", e)
            }
        }
    }

    /**
     * REL-02: writes the service's held-back final epoch if one is still pending, then clears it.
     * Shared by the user-stop path and the incomplete-session recovery path so both recover the
     * same data the 3-epoch smoothing filter deliberately held back (an epoch can't be persisted
     * until its successor arrives). Runs on the ViewModel's coroutine, never the main thread —
     * the whole point of the companion flow is that the service must not runBlocking a Room write.
     */
    private suspend fun flushPendingFinalEpoch() {
        val finalEpoch = SleepTrackingService.finalEpoch.value
        if (finalEpoch != null) {
            sleepRepository.insertEpoch(finalEpoch)
            SleepTrackingService.clearFinalEpoch()
        }
    }

    /** Fires the health-context notifications that depend on this morning's completed session. */
    private suspend fun notifyMorningAlerts(session: SleepSession) {
        deepSleepAlertNotifier.checkAndNotify(session.deepSleepPercent.toDouble())

        val profile = userProfileRepository.getProfile() ?: return

        userProfileRepository.getCurrentCyclePhase()?.let { phase ->
            hormonalPhaseNotifier.checkAndNotify(profile, phase)
        }

        if (profile.lifeStage == LifeStage.POSTPARTUM) {
            val lookback = System.currentTimeMillis() - (6L * 7 * 24 * 60 * 60 * 1000)
            // SESS-04: fragmentation risk is a nighttime signal — naps shouldn't count toward it.
            val recentSessions = sleepRepository.getMainSleepSessionsSince(lookback)
            val weeksFragmented = fragmentationUseCase(recentSessions, System.currentTimeMillis())
            ppdRiskNotifier.checkAndNotify(profile, weeksFragmented)
        }
    }

    /**
     * HEALTH-01/02: mirrors the NAS sync pattern in [stopTracking] — best-effort, opt-in, and
     * never allowed to break session completion if Health Connect is unavailable/unauthorized
     * or the write otherwise fails (both repository methods already no-op on !AUTHORIZED, this
     * catch only guards against unexpected platform exceptions).
     */
    private suspend fun syncToHealthConnect(session: SleepSession, epochs: List<SleepEpoch>) {
        if (!preferencesRepository.healthConnectEnabled.first()) return
        try {
            healthConnectRepository.syncVitalsForSession(
                sessionId = session.id,
                start = java.time.Instant.ofEpochMilli(session.startTimeMillis),
                end = java.time.Instant.ofEpochMilli(session.endTimeMillis)
            )
            healthConnectRepository.writeSleepSession(session, epochs)
        } catch (e: Exception) {
            android.util.Log.e("SleepTrackingViewModel", "Failed to sync to Health Connect", e)
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

    /** Target sleep minutes (from profile, falling back to the 8h domain default) + configured oversleep threshold. */
    private suspend fun loadOversleepInputs(): Pair<Int, Int> {
        val targetSleepMinutes = ((userProfileRepository.getProfile()?.targetSleepHours ?: 8.0f) * 60).toInt()
        val oversleepThresholdMinutes = preferencesRepository.oversleepThresholdMinutes.first()
        return targetSleepMinutes to oversleepThresholdMinutes
    }

    /** Derives final session metrics from recorded epochs. Shared by a normal user-initiated stop and REL-02's incomplete-session finalization. */
    private fun buildCompletedSession(
        session: SleepSession,
        epochs: List<SleepEpoch>,
        coughCount: Int,
        endTimeMillis: Long,
        avgBrpm: Float?,
        isPartial: Boolean,
        targetSleepMinutes: Int,
        oversleepThresholdMinutes: Int
    ): SleepSession {
        val sleepEpochs = epochs.filter { it.stage != SleepStage.AWAKE }
        val deepEpochs = epochs.filter { it.stage == SleepStage.DEEP }
        val lightEpochs = epochs.filter { it.stage == SleepStage.LIGHT }
        val remEpochs = epochs.filter { it.stage == SleepStage.REM }

        // Time in bed from wall clock
        val timeInBed = ((endTimeMillis - session.startTimeMillis) / 60_000).toInt()

        // Sleep duration: each epoch covers a 30-second window from its timestamp
        val sleepDuration = if (sleepEpochs.isNotEmpty()) {
            (sleepEpochs.size * EPOCH_DURATION_MS / 60_000).toInt()
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

        return session.copy(
            endTimeMillis = endTimeMillis,
            sleepDurationMinutes = sleepDuration,
            timeInBedMinutes = timeInBed,
            sleepEfficiency = efficiency,
            sleepOnsetMinutes = onsetMinutes,
            wakeEvents = wakeEvents,
            deepSleepPercent = deepPercent,
            lightSleepPercent = lightPercent,
            remSleepPercent = remPercent,
            avgBreathingRateBrpm = avgBrpm,
            coughEventCount = coughCount,
            isCompleted = true,
            isPartial = isPartial,
            // SESS-03: only main-sleep sessions are compared against the full-night target —
            // a long nap/commute session isn't "oversleep" against a nightly baseline.
            isOversleep = session.sessionType == SessionType.MAIN_SLEEP &&
                sleepDuration > (targetSleepMinutes + oversleepThresholdMinutes)
        )
    }

    private companion object {
        /** Mirrors the ~30s epoch-write cadence in [SleepTrackingService]'s collectors (REL-02 heartbeat). */
        const val EPOCH_DURATION_MS = 30_000L
    }
}
