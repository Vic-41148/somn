package dev.vic41148.somn.core.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.vic41148.somn.core.domain.model.AudioEventType

@Entity(tableName = "audio_events")
data class AudioEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val timestampMillis: Long,
    val durationSeconds: Int,
    val type: String, // String representation of AudioEventType
    val intensityDecibels: Int,
    val clipPath: String? = null
)
