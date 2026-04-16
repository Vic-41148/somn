package dev.vic41148.somn.core.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.vic41148.somn.core.data.database.entity.AudioEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioEventDao {
    @Insert
    suspend fun insert(event: AudioEventEntity): Long

    @Insert
    suspend fun insertAll(events: List<AudioEventEntity>)

    @Query("SELECT * FROM audio_events WHERE sessionId = :sessionId ORDER BY timestampMillis ASC")
    fun observeBySession(sessionId: Long): Flow<List<AudioEventEntity>>

    @Query("SELECT * FROM audio_events WHERE sessionId = :sessionId ORDER BY timestampMillis ASC")
    suspend fun getBySession(sessionId: Long): List<AudioEventEntity>

    @Query("SELECT COUNT(*) FROM audio_events WHERE sessionId = :sessionId AND type = :type")
    suspend fun getCountBySessionAndType(sessionId: Long, type: String): Int
}
