package dev.vic41148.somn.core.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.vic41148.somn.core.data.database.entity.SessionTagEntity
import dev.vic41148.somn.core.data.database.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: TagEntity): Long

    @Update
    suspend fun update(tag: TagEntity)

    @Delete
    suspend fun delete(tag: TagEntity)

    @Query("SELECT * FROM tags WHERE isArchived = 0 ORDER BY category ASC, name ASC")
    fun observeAllActive(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY category ASC, name ASC")
    fun observeAll(): Flow<List<TagEntity>>

    /** R4: one-shot name list for idempotent default-taxonomy seeding. */
    @Query("SELECT name FROM tags")
    suspend fun getAllNames(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessionTag(sessionTag: SessionTagEntity)

    @Query("DELETE FROM session_tags WHERE sessionId = :sessionId AND tagId = :tagId")
    suspend fun removeSessionTag(sessionId: Long, tagId: Long)

    @Query("""
        SELECT t.* FROM tags t 
        INNER JOIN session_tags st ON t.id = st.tagId 
        WHERE st.sessionId = :sessionId
    """)
    fun observeTagsForSession(sessionId: Long): Flow<List<TagEntity>>

    @Query("""
        SELECT t.* FROM tags t 
        INNER JOIN session_tags st ON t.id = st.tagId 
        WHERE st.sessionId = :sessionId
    """)
    suspend fun getTagsForSession(sessionId: Long): List<TagEntity>
}
