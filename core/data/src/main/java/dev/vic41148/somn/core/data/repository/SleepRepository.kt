package dev.vic41148.somn.core.data.repository

import androidx.room.withTransaction
import dev.vic41148.somn.core.data.database.SleepDatabase
import dev.vic41148.somn.core.data.database.dao.SleepEpochDao
import dev.vic41148.somn.core.data.database.dao.SleepSessionDao
import dev.vic41148.somn.core.data.database.dao.AudioEventDao
import dev.vic41148.somn.core.data.database.dao.ExternalVitalsDao
import dev.vic41148.somn.core.data.database.entity.SleepEpochEntity
import dev.vic41148.somn.core.data.database.entity.SleepSessionEntity
import dev.vic41148.somn.core.data.database.entity.AudioEventEntity
import dev.vic41148.somn.core.data.database.entity.ExternalVitalsEntity
import dev.vic41148.somn.core.domain.model.SleepEpoch
import dev.vic41148.somn.core.domain.model.SleepSession
import dev.vic41148.somn.core.domain.model.SleepStage
import dev.vic41148.somn.core.domain.model.SessionType
import dev.vic41148.somn.core.domain.model.AudioEvent
import dev.vic41148.somn.core.domain.model.AudioEventType
import dev.vic41148.somn.core.domain.model.ExternalVitalsSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepRepository @Inject constructor(
    private val database: SleepDatabase,
    private val sessionDao: SleepSessionDao,
    private val epochDao: SleepEpochDao,
    private val audioEventDao: AudioEventDao,
    private val externalVitalsDao: ExternalVitalsDao
) {

    /**
     * Runs [block] in a single Room transaction: a mid-import crash rolls everything back
     * instead of leaving half an import behind.
     */
    suspend fun <R> inTransaction(block: suspend () -> R): R = database.withTransaction(block)

    /**
     * Full wipe: every clip file on disk, then every table. Preferences are cleared
     * separately by the caller (they live in another repository). The DB key file stays —
     * a fresh empty database under the same key is exactly a fresh install.
     */
    suspend fun deleteAllData() {
        audioEventDao.getEventsWithClips().forEach { entity ->
            entity.clipPath?.let { path -> runCatching { java.io.File(path).delete() } }
        }
        database.clearAllTables()
    }

    // --- Sessions ---

    suspend fun createSession(
        startTimeMillis: Long,
        timezoneId: String = java.time.ZoneId.systemDefault().id,
        sessionType: SessionType = SessionType.MAIN_SLEEP
    ): Long {
        return sessionDao.insert(
            SleepSessionEntity(
                startTimeMillis = startTimeMillis,
                timezoneId = timezoneId,
                sessionType = sessionType.name
            )
        )
    }

    suspend fun completeSession(session: SleepSession) {
        sessionDao.update(session.toEntity())
    }

    suspend fun updateSession(session: SleepSession) {
        sessionDao.update(session.toEntity())
    }

    suspend fun insertManualSession(session: SleepSession) {
        sessionDao.insert(session.toEntity())
    }

    suspend fun deleteSession(session: SleepSession) {
        val events = getAudioEvents(session.id)
        events.forEach { event ->
            event.clipPath?.let { path ->
                val file = java.io.File(path)
                if (file.exists()) {
                    file.delete()
                }
            }
        }
        // AudioEventEntity has no FK/cascade to sleep_sessions (unlike SleepEpochEntity, which
        // does), so without this the audio_events rows for a deleted session were orphaned in
        // the DB forever — clip files got cleaned up above, but the rows themselves never did.
        audioEventDao.deleteBySession(session.id)
        sessionDao.delete(session.toEntity())
    }

    suspend fun getSession(id: Long): SleepSession? {
        return sessionDao.getById(id)?.toDomain()
    }

    /**
     * R2 per-category purge: deletes completed sessions older than the cutoff via
     * [deleteSession], so clip files, audio rows go explicitly and epochs/vitals/tags
     * follow their FK cascades — same path as single-session delete, no orphans.
     *
     * @return how many sessions were deleted.
     */
    suspend fun deleteSessionsOlderThan(cutoffMillis: Long): Int {
        val old = sessionDao.getSessionsOlderThan(cutoffMillis)
        old.forEach { deleteSession(it.toDomain()) }
        return old.size
    }

    /** Emits the session whenever its row changes — the review screen keys its data on this, never the shared lastSession flow. */
    fun observeSession(id: Long): Flow<SleepSession?> {
        return sessionDao.observeById(id).map { it?.toDomain() }
    }

    fun observeActiveSession(): Flow<SleepSession?> {
        return sessionDao.observeActiveSession().map { it?.toDomain() }
    }

    suspend fun getActiveSession(): SleepSession? {
        return sessionDao.getActiveSession()?.toDomain()
    }

    fun observeCompletedSessions(): Flow<List<SleepSession>> {
        return sessionDao.observeAllCompleted().map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun getRecentSessions(limit: Int): List<SleepSession> {
        return sessionDao.getRecentSessions(limit).map { it.toDomain() }
    }

    /** SESS-04: main-sleep-only variant for consistency/streak/circadian aggregates — excludes naps/commute/shift. */
    suspend fun getRecentMainSleepSessions(limit: Int): List<SleepSession> {
        return sessionDao.getRecentMainSleepSessions(limit).map { it.toDomain() }
    }

    suspend fun getSessionsSince(fromMillis: Long): List<SleepSession> {
        return sessionDao.getSessionsSince(fromMillis).map { it.toDomain() }
    }

    /** SESS-04: main-sleep-only variant for consistency/streak/circadian aggregates — excludes naps/commute/shift. */
    suspend fun getMainSleepSessionsSince(fromMillis: Long): List<SleepSession> {
        return sessionDao.getMainSleepSessionsSince(fromMillis).map { it.toDomain() }
    }

    /** SESS-04: main-sleep-only variant for consistency/streak/circadian aggregates — excludes naps/commute/shift. */
    fun observeMainSleepSessions(): Flow<List<SleepSession>> {
        return sessionDao.observeMainSleepSessions().map { list -> list.map { it.toDomain() } }
    }

    suspend fun getAverageScoreSince(fromMillis: Long): Float {
        return sessionDao.getAverageScoreSince(fromMillis) ?: 0f
    }

    suspend fun getAverageDurationSince(fromMillis: Long): Float {
        return sessionDao.getAverageDurationSince(fromMillis) ?: 0f
    }

    /** HEALTH-04: count of completed sessions Health Connect sync has not successfully written yet (unsynced or silently dedup-skipped). */
    fun observeUnsyncedToHealthConnectCount(): Flow<Int> {
        return sessionDao.observeUnsyncedToHealthConnectCount()
    }

    // --- Epochs ---

    suspend fun insertEpoch(epoch: SleepEpoch) {
        epochDao.insert(epoch.toEntity())
    }

    suspend fun insertEpochs(epochs: List<SleepEpoch>) {
        epochDao.insertAll(epochs.map { it.toEntity() })
    }

    fun observeEpochs(sessionId: Long): Flow<List<SleepEpoch>> {
        return epochDao.observeBySession(sessionId).map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun getEpochs(sessionId: Long): List<SleepEpoch> {
        return epochDao.getBySession(sessionId).map { it.toDomain() }
    }

    suspend fun getLatestEpoch(sessionId: Long): SleepEpoch? {
        return epochDao.getLatestEpoch(sessionId)?.toDomain()
    }

    // --- Audio Events ---

    suspend fun insertAudioEvent(event: AudioEvent) {
        audioEventDao.insert(event.toEntity())
    }

    fun observeAudioEvents(sessionId: Long): Flow<List<AudioEvent>> {
        return audioEventDao.observeBySession(sessionId).map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun getAudioEvents(sessionId: Long): List<AudioEvent> {
        return audioEventDao.getBySession(sessionId).map { it.toDomain() }
    }

    fun getAudioEventsSynchronous(sessionId: Long): List<AudioEvent> {
        return audioEventDao.getBySessionSync(sessionId).map { it.toDomain() }
    }

    /**
     * Deletes every sleep-talk recording on disk and forgets their paths. The audio events stay
     * in the history — only the audio itself goes. Backs the "delete all recordings" control in
     * Settings, so a user who wants the recordings gone does not have to wait for retention to
     * catch up or delete whole sessions to get there.
     *
     * @return how many clip files were actually removed.
     */
    suspend fun deleteAllAudioClips(): Int {
        var deleted = 0
        audioEventDao.getEventsWithClips().forEach { entity ->
            entity.clipPath?.let { path ->
                if (java.io.File(path).delete()) deleted++
            }
            audioEventDao.clearClipPath(entity.id)
        }
        return deleted
    }

    // --- External Vitals (HEALTH-01) ---

    suspend fun upsertExternalVitals(vitals: ExternalVitalsSnapshot) {
        externalVitalsDao.upsert(vitals.toEntity())
    }

    suspend fun getExternalVitals(sessionId: Long): ExternalVitalsSnapshot? {
        return externalVitalsDao.getForSession(sessionId)?.toDomain()
    }

    // --- Mappers ---

    private fun SleepSessionEntity.toDomain() = SleepSession(
        id = id,
        startTimeMillis = startTimeMillis,
        endTimeMillis = endTimeMillis,
        sleepDurationMinutes = sleepDurationMinutes,
        timeInBedMinutes = timeInBedMinutes,
        sleepEfficiency = sleepEfficiency,
        sleepOnsetMinutes = sleepOnsetMinutes,
        wakeEvents = wakeEvents,
        deepSleepPercent = deepSleepPercent,
        lightSleepPercent = lightSleepPercent,
        remSleepPercent = remSleepPercent,
        sleepScore = sleepScore,
        moodRating = moodRating,
        notes = notes,
        isCompleted = isCompleted,
        timezoneId = timezoneId,
        isHomeSleep = isHomeSleep,
        alarmUsed = alarmUsed,
        avgBreathingRateBrpm = avgBreathingRateBrpm,
        coughEventCount = coughEventCount,
        isPartial = isPartial,
        sessionType = try { SessionType.valueOf(sessionType) } catch (e: Exception) { SessionType.MAIN_SLEEP },
        isOversleep = isOversleep,
        healthConnectRecordId = healthConnectRecordId
    )

    private fun SleepSession.toEntity() = SleepSessionEntity(
        id = id,
        startTimeMillis = startTimeMillis,
        endTimeMillis = endTimeMillis,
        sleepDurationMinutes = sleepDurationMinutes,
        timeInBedMinutes = timeInBedMinutes,
        sleepEfficiency = sleepEfficiency,
        sleepOnsetMinutes = sleepOnsetMinutes,
        wakeEvents = wakeEvents,
        deepSleepPercent = deepSleepPercent,
        lightSleepPercent = lightSleepPercent,
        remSleepPercent = remSleepPercent,
        sleepScore = sleepScore,
        moodRating = moodRating,
        notes = notes,
        isCompleted = isCompleted,
        timezoneId = timezoneId,
        isHomeSleep = isHomeSleep,
        alarmUsed = alarmUsed,
        avgBreathingRateBrpm = avgBreathingRateBrpm,
        coughEventCount = coughEventCount,
        isPartial = isPartial,
        sessionType = sessionType.name,
        isOversleep = isOversleep,
        healthConnectRecordId = healthConnectRecordId
    )

    private fun SleepEpochEntity.toDomain() = SleepEpoch(
        id = id,
        sessionId = sessionId,
        timestampMillis = timestampMillis,
        stage = try { SleepStage.valueOf(stage) } catch (e: Exception) { SleepStage.UNKNOWN },
        movementMagnitude = movementMagnitude,
        movementVariability = movementVariability
    )

    private fun SleepEpoch.toEntity() = SleepEpochEntity(
        id = id,
        sessionId = sessionId,
        timestampMillis = timestampMillis,
        stage = stage.name,
        movementMagnitude = movementMagnitude,
        movementVariability = movementVariability
    )

    private fun AudioEventEntity.toDomain() = AudioEvent(
        id = id,
        sessionId = sessionId,
        timestampMillis = timestampMillis,
        durationSeconds = durationSeconds,
        type = try { AudioEventType.valueOf(type) } catch (e: Exception) { AudioEventType.ANOMALY },
        intensityDecibels = intensityDecibels,
        clipPath = clipPath,
        syncedToNas = syncedToNas
    )

    private fun AudioEvent.toEntity() = AudioEventEntity(
        id = id,
        sessionId = sessionId,
        timestampMillis = timestampMillis,
        durationSeconds = durationSeconds,
        type = type.name,
        intensityDecibels = intensityDecibels,
        clipPath = clipPath,
        syncedToNas = syncedToNas
    )

    private fun ExternalVitalsEntity.toDomain() = ExternalVitalsSnapshot(
        sessionId = sessionId,
        avgHeartRateBpm = avgHeartRateBpm,
        restingHeartRateBpm = restingHeartRateBpm,
        avgHeartRateVariabilityMs = avgHeartRateVariabilityMs,
        avgSpo2Percent = avgSpo2Percent,
        minSpo2Percent = minSpo2Percent,
        avgSkinTemperatureCelsius = avgSkinTemperatureCelsius,
        sourceApp = sourceApp
    )

    private fun ExternalVitalsSnapshot.toEntity() = ExternalVitalsEntity(
        sessionId = sessionId,
        avgHeartRateBpm = avgHeartRateBpm,
        restingHeartRateBpm = restingHeartRateBpm,
        avgHeartRateVariabilityMs = avgHeartRateVariabilityMs,
        avgSpo2Percent = avgSpo2Percent,
        minSpo2Percent = minSpo2Percent,
        avgSkinTemperatureCelsius = avgSkinTemperatureCelsius,
        sourceApp = sourceApp
    )
}
