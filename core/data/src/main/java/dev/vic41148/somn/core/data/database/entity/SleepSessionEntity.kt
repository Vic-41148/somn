package dev.vic41148.somn.core.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sleep_sessions")
data class SleepSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTimeMillis: Long,
    val endTimeMillis: Long = 0,
    val sleepDurationMinutes: Int = 0,
    val timeInBedMinutes: Int = 0,
    val sleepEfficiency: Float = 0f,
    val sleepOnsetMinutes: Int = 0,
    val wakeEvents: Int = 0,
    val deepSleepPercent: Float = 0f,
    val lightSleepPercent: Float = 0f,
    val remSleepPercent: Float = 0f,
    val sleepScore: Int = 0,
    val moodRating: Int = 0,
    val notes: String = "",
    val isCompleted: Boolean = false,
    // Phase 3: Added for circadian intelligence
    /** Timezone ID at session start — used for correct local-time circadian calculations. */
    val timezoneId: String = "UTC",
    /** False when sleep occurred away from home (travel) — used to tag non-baseline sessions. */
    val isHomeSleep: Boolean = true,
    /** True if an alarm was active during this session — used to detect alarm-free nights for chronotype assessment. */
    val alarmUsed: Boolean = false,
    /** Average breathing rate in breaths per minute. Null if mic disabled/not available. */
    val avgBreathingRateBrpm: Float? = null,
    /** Number of cough events detected by audio classifier during this session. */
    val coughEventCount: Int = 0,
    /** True when the session was auto-finalized from a stale/abandoned tracking session (service died mid-night) rather than a normal user-initiated stop. */
    val isPartial: Boolean = false,
    /** SessionType enum name — only "MAIN_SLEEP" feeds nightly consistency/streak/circadian aggregates. */
    val sessionType: String = "MAIN_SLEEP",
    /** True when sleepDurationMinutes exceeded the user's healthy-duration threshold. */
    val isOversleep: Boolean = false,
    /** HEALTH-04: Health Connect record ID this session was written as, once synced — null until then. Re-sync checks this first to avoid writing a duplicate SleepSessionRecord. */
    val healthConnectRecordId: String? = null
)
