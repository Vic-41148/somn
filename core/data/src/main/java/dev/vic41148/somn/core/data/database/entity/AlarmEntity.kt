package dev.vic41148.somn.core.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val label: String = "",
    val isEnabled: Boolean = true,
    val repeatDays: String = "",        // Comma-separated day ints
    val wakeWindowMinutes: Int = 30,
    val snoozeDurationMinutes: Int = 9,
    val maxSnoozeCount: Int = 3,
    val soundUri: String = "",
    val vibrationEnabled: Boolean = true,
    val gradualVolumeSeconds: Int = 60,
    val captchaType: String = "NONE"
)
