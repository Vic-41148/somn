package dev.vic41148.somn.core.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.vic41148.somn.core.data.database.entity.SleepEpochEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepEpochDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(epoch: SleepEpochEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(epochs: List<SleepEpochEntity>)

    @Query("SELECT * FROM sleep_epochs WHERE sessionId = :sessionId ORDER BY timestampMillis ASC")
    fun observeBySession(sessionId: Long): Flow<List<SleepEpochEntity>>

    @Query("SELECT * FROM sleep_epochs WHERE sessionId = :sessionId ORDER BY timestampMillis ASC")
    suspend fun getBySession(sessionId: Long): List<SleepEpochEntity>

    @Query("SELECT * FROM sleep_epochs WHERE sessionId = :sessionId ORDER BY timestampMillis DESC LIMIT 1")
    suspend fun getLatestEpoch(sessionId: Long): SleepEpochEntity?

    @Query("SELECT * FROM sleep_epochs WHERE sessionId = :sessionId ORDER BY timestampMillis DESC LIMIT :count")
    suspend fun getRecentEpochs(sessionId: Long, count: Int): List<SleepEpochEntity>

    @Query("SELECT COUNT(*) FROM sleep_epochs WHERE sessionId = :sessionId AND stage = :stage")
    suspend fun countByStage(sessionId: Long, stage: String): Int

    @Query("DELETE FROM sleep_epochs WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: Long)
}
