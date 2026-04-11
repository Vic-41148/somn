package dev.vic41148.somn.core.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.vic41148.somn.core.data.database.entity.SleepSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SleepSessionEntity): Long

    @Update
    suspend fun update(session: SleepSessionEntity)

    @Delete
    suspend fun delete(session: SleepSessionEntity)

    @Query("SELECT * FROM sleep_sessions WHERE id = :id")
    suspend fun getById(id: Long): SleepSessionEntity?

    @Query("SELECT * FROM sleep_sessions WHERE id = :id")
    fun observeById(id: Long): Flow<SleepSessionEntity?>

    @Query("SELECT * FROM sleep_sessions WHERE isCompleted = 1 ORDER BY startTimeMillis DESC")
    fun observeAllCompleted(): Flow<List<SleepSessionEntity>>

    @Query("SELECT * FROM sleep_sessions WHERE isCompleted = 0 LIMIT 1")
    suspend fun getActiveSession(): SleepSessionEntity?

    @Query("SELECT * FROM sleep_sessions WHERE isCompleted = 0 LIMIT 1")
    fun observeActiveSession(): Flow<SleepSessionEntity?>

    @Query("SELECT * FROM sleep_sessions WHERE isCompleted = 1 ORDER BY startTimeMillis DESC LIMIT :limit")
    suspend fun getRecentSessions(limit: Int): List<SleepSessionEntity>

    @Query("SELECT * FROM sleep_sessions WHERE isCompleted = 1 AND startTimeMillis >= :fromMillis ORDER BY startTimeMillis DESC")
    suspend fun getSessionsSince(fromMillis: Long): List<SleepSessionEntity>

    @Query("SELECT AVG(sleepScore) FROM sleep_sessions WHERE isCompleted = 1 AND startTimeMillis >= :fromMillis")
    suspend fun getAverageScoreSince(fromMillis: Long): Float?

    @Query("SELECT AVG(sleepDurationMinutes) FROM sleep_sessions WHERE isCompleted = 1 AND startTimeMillis >= :fromMillis")
    suspend fun getAverageDurationSince(fromMillis: Long): Float?

    @Query("SELECT COUNT(*) FROM sleep_sessions WHERE isCompleted = 1")
    suspend fun getTotalCompletedCount(): Int
}
