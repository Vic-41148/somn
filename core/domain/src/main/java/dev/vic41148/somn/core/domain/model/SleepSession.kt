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
    val isCompleted: Boolean = false
) {
    val isTracking: Boolean get() = !isCompleted && endTimeMillis == 0L
}
