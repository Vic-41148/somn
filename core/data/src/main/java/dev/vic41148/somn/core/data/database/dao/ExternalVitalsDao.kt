package dev.vic41148.somn.core.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.vic41148.somn.core.data.database.entity.ExternalVitalsEntity

@Dao
interface ExternalVitalsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vitals: ExternalVitalsEntity)

    @Query("SELECT * FROM external_vitals WHERE sessionId = :sessionId")
    suspend fun getForSession(sessionId: Long): ExternalVitalsEntity?
}
