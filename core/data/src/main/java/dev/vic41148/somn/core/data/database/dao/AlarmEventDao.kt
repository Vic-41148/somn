package dev.vic41148.somn.core.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.vic41148.somn.core.data.database.entity.AlarmEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmEventDao {

    @Insert
    suspend fun insert(event: AlarmEventEntity): Long

    @Query("SELECT * FROM alarm_events ORDER BY timestampMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AlarmEventEntity>>

    @Query("SELECT * FROM alarm_events WHERE alarmId = :alarmId ORDER BY timestampMillis DESC")
    fun observeByAlarm(alarmId: Long): Flow<List<AlarmEventEntity>>

    @Query("SELECT * FROM alarm_events WHERE type = 'MISSED' AND timestampMillis >= :sinceMillis ORDER BY timestampMillis DESC")
    fun observeMissedSince(sinceMillis: Long): Flow<List<AlarmEventEntity>>

    @Query("SELECT COUNT(*) FROM alarm_events WHERE type = 'MISSED' AND timestampMillis >= :sinceMillis")
    suspend fun countMissedSince(sinceMillis: Long): Int

    @Query("DELETE FROM alarm_events")
    suspend fun deleteAll()
}