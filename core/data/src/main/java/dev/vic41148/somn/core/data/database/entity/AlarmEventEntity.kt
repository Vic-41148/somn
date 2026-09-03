package dev.vic41148.somn.core.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted alarm lifecycle event (see domain [dev.vic41148.somn.core.domain.model.AlarmEvent]).
 *
 * No FK to `alarms` on purpose: deleting an alarm must not erase its history, and an event is a
 * point-in-time snapshot (label/time captured at firing) that stays accurate through later edits.
 */
@Entity(
    tableName = "alarm_events",
    indices = [
        Index("alarmId"),
        Index("timestampMillis"),
        Index("type")
    ]
)
data class AlarmEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val alarmId: Long,
    val type: String,
    val timestampMillis: Long,
    val label: String = "",
    val hour: Int = 0,
    val minute: Int = 0,
    val snoozeCount: Int = 0,
    val detail: String = ""
)