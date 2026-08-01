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

    @Query("SELECT * FROM audio_events WHERE sessionId = :sessionId ORDER BY timestampMillis ASC")
    fun getBySessionSync(sessionId: Long): List<AudioEventEntity>

    @Query("SELECT COUNT(*) FROM audio_events WHERE sessionId = :sessionId AND type = :type")
    suspend fun getCountBySessionAndType(sessionId: Long, type: String): Int

    @Query("SELECT * FROM audio_events WHERE syncedToNas = 0 AND clipPath IS NOT NULL AND timestampMillis < :cutoffMillis")
    suspend fun getUnsyncedAudioEventsOlderThan(cutoffMillis: Long): List<AudioEventEntity>

    @Query("UPDATE audio_events SET syncedToNas = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)

    @Query("UPDATE audio_events SET clipPath = NULL WHERE id = :id")
    suspend fun clearClipPath(id: Long)

    /**
     * Clips eligible for retention pruning. Un-synced clips are included deliberately: if NAS
     * sync has not managed to upload a recording within the retention window, the retention
     * promise wins over the backup convenience.
     */
    @Query("SELECT * FROM audio_events WHERE clipPath IS NOT NULL AND timestampMillis < :cutoffMillis")
    suspend fun getEventsWithClipsOlderThan(cutoffMillis: Long): List<AudioEventEntity>

    @Query("SELECT * FROM audio_events WHERE clipPath IS NOT NULL")
    suspend fun getEventsWithClips(): List<AudioEventEntity>

    @Query("DELETE FROM audio_events WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: Long)
}
