package dev.vic41148.somn.core.data.repository

import dev.vic41148.somn.core.data.database.dao.SleepEpochDao
import dev.vic41148.somn.core.data.database.dao.SleepSessionDao
import dev.vic41148.somn.core.data.database.entity.SleepEpochEntity
import dev.vic41148.somn.core.data.database.entity.SleepSessionEntity
import dev.vic41148.somn.core.domain.model.SleepEpoch
import dev.vic41148.somn.core.domain.model.SleepSession
import dev.vic41148.somn.core.domain.model.SleepStage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepRepository @Inject constructor(
    private val sessionDao: SleepSessionDao,
    private val epochDao: SleepEpochDao
) {

    // --- Sessions ---

    suspend fun createSession(startTimeMillis: Long): Long {
        return sessionDao.insert(
            SleepSessionEntity(startTimeMillis = startTimeMillis)
        )
    }

    suspend fun completeSession(session: SleepSession) {
        sessionDao.update(session.toEntity())
    }

    suspend fun updateSession(session: SleepSession) {
        sessionDao.update(session.toEntity())
    }

    suspend fun deleteSession(session: SleepSession) {
        sessionDao.delete(session.toEntity())
    }

    suspend fun getSession(id: Long): SleepSession? {
        return sessionDao.getById(id)?.toDomain()
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

    suspend fun getSessionsSince(fromMillis: Long): List<SleepSession> {
        return sessionDao.getSessionsSince(fromMillis).map { it.toDomain() }
    }

    suspend fun getAverageScoreSince(fromMillis: Long): Float {
        return sessionDao.getAverageScoreSince(fromMillis) ?: 0f
    }

    suspend fun getAverageDurationSince(fromMillis: Long): Float {
        return sessionDao.getAverageDurationSince(fromMillis) ?: 0f
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
        isCompleted = isCompleted
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
        isCompleted = isCompleted
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
}
