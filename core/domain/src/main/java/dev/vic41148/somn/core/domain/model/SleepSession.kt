package dev.vic41148.somn.core.domain.model

/**
 * Domain model for a complete sleep session.
 */
data class SleepSession(
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
    val moodRating: Int = 0,       // 1-5 emoji scale
    val notes: String = "",
    val isCompleted: Boolean = false,
    // Phase 3: Circadian intelligence fields
    /** Timezone ID at session start — used for correct local-time circadian calculations. */
    val timezoneId: String = "UTC",
    /** False when sleep occurred away from home. Used to tag non-baseline sessions. */
    val isHomeSleep: Boolean = true,
    /** True if an alarm was active — false = alarm-free night, used for chronotype detection. */
    val alarmUsed: Boolean = false,
    /** Average breathing rate in breaths per minute for the session, computed from audio analysis. Null if microphone not used. */
    val avgBreathingRateBrpm: Float? = null,
    /** Number of cough events detected by audio classifier during this session. */
    val coughEventCount: Int = 0,
    /** True when the session was auto-finalized from a stale/abandoned tracking session (service died mid-night) rather than a normal user-initiated stop. */
    val isPartial: Boolean = false,
    /** What kind of session this was — only MAIN_SLEEP feeds nightly consistency/streak/circadian aggregates. */
    val sessionType: SessionType = SessionType.MAIN_SLEEP,
    /** True when sleepDurationMinutes exceeded the user's healthy-duration threshold. */
    val isOversleep: Boolean = false,
    /** HEALTH-04: Health Connect record ID this session was written as, once synced — null until then. */
    val healthConnectRecordId: String? = null
) {
    val isTracking: Boolean get() = !isCompleted && endTimeMillis == 0L
}
