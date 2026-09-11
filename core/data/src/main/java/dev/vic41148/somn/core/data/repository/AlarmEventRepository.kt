package dev.vic41148.somn.core.data.repository

import dev.vic41148.somn.core.data.database.dao.AlarmEventDao
import dev.vic41148.somn.core.data.database.entity.AlarmEventEntity
import dev.vic41148.somn.core.domain.model.AlarmEvent
import dev.vic41148.somn.core.domain.model.AlarmEventType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistence for alarm lifecycle events (fired / snoozed / dismissed / confirmed-awake / missed).
 * Event rows are point-in-time snapshots, intentionally independent of the live alarm config - see
 * [AlarmEventEntity]. The events table lives in the same Room DB as everything else, so whole-file
 * backups (BackupRepository / NAS) carry the history automatically.
 */
@Singleton
class AlarmEventRepository @Inject constructor(
    private val dao: AlarmEventDao
) {

    /** Records one lifecycle event. Fire-and-forget from the caller's perspective. */
    suspend fun record(event: AlarmEvent) {
        dao.insert(event.toEntity())
    }

    suspend fun record(
        alarmId: Long,
        type: AlarmEventType,
        timestampMillis: Long = System.currentTimeMillis(),
        label: String = "",
        hour: Int = 0,
        minute: Int = 0,
        snoozeCount: Int = 0,
        detail: String = ""
    ) {
        dao.insert(
            AlarmEventEntity(
                alarmId = alarmId,
                type = type.name,
                timestampMillis = timestampMillis,
                label = label,
                hour = hour,
                minute = minute,
                snoozeCount = snoozeCount,
                detail = detail
            )
        )
    }

    fun observeRecent(limit: Int = 100): Flow<List<AlarmEvent>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    fun observeByAlarm(alarmId: Long): Flow<List<AlarmEvent>> =
        dao.observeByAlarm(alarmId).map { list -> list.map { it.toDomain() } }

    fun observeMissedSince(sinceMillis: Long): Flow<List<AlarmEvent>> =
        dao.observeMissedSince(sinceMillis).map { list -> list.map { it.toDomain() } }

    suspend fun countMissedSince(sinceMillis: Long): Int =
        dao.countMissedSince(sinceMillis)

    suspend fun clearHistory() {
        dao.deleteAll()
    }

    private fun AlarmEventEntity.toDomain() = AlarmEvent(
        id = id,
        alarmId = alarmId,
        type = AlarmEventType.valueOfSafe(type),
        timestampMillis = timestampMillis,
        label = label,
        hour = hour,
        minute = minute,
        snoozeCount = snoozeCount,
        detail = detail
    )

    private fun AlarmEvent.toEntity() = AlarmEventEntity(
        id = id,
        alarmId = alarmId,
        type = type.name,
        timestampMillis = timestampMillis,
        label = label,
        hour = hour,
        minute = minute,
        snoozeCount = snoozeCount,
        detail = detail
    )
}