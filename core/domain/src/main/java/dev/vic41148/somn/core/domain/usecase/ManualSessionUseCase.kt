package dev.vic41148.somn.core.domain.usecase

import dev.vic41148.somn.core.domain.model.SleepSession

/**
 * Use case for creating and editing sleep sessions manually.
 *
 * Research doc competitor gap: "Cannot manually edit a session retroactively"
 * — cited as a key failure for Sleep Cycle, and nobody does it well.
 */
class ManualSessionUseCase {

    /**
     * Validate and prepare a retroactive sleep session from manually entered times.
     *
     * @param startTimeMillis User-entered bed time
     * @param endTimeMillis User-entered wake time
     * @return A SleepSession ready to be persisted, or null if invalid
     */
    fun createManualSession(startTimeMillis: Long, endTimeMillis: Long): SleepSession? {
        if (endTimeMillis <= startTimeMillis) return null

        val durationMinutes = ((endTimeMillis - startTimeMillis) / 60_000).toInt()
        if (durationMinutes < 15) return null // Minimum 15 min for a valid session

        return SleepSession(
            startTimeMillis = startTimeMillis,
            endTimeMillis = endTimeMillis,
            sleepDurationMinutes = durationMinutes,
            timeInBedMinutes = durationMinutes,
            sleepEfficiency = 90f, // Assumed for manual entry (no sensor data)
            isCompleted = true
        )
    }

    /**
     * Adjust session times and recalculate metrics.
     * Used when user corrects bed time or wake time after tracking.
     *
     * @param session Original session
     * @param newStartMillis Adjusted bed time (or null to keep existing)
     * @param newEndMillis Adjusted wake time (or null to keep existing)
     * @return Updated session with recalculated duration and time-in-bed
     */
    fun adjustSessionTimes(
        session: SleepSession,
        newStartMillis: Long? = null,
        newEndMillis: Long? = null
    ): SleepSession {
        val start = newStartMillis ?: session.startTimeMillis
        val end = newEndMillis ?: session.endTimeMillis

        if (end <= start) return session // Invalid adjustment, return original

        val newTimeInBed = ((end - start) / 60_000).toInt()
        // Preserve the ratio of sleep to time-in-bed
        val ratio = if (session.timeInBedMinutes > 0)
            session.sleepDurationMinutes.toFloat() / session.timeInBedMinutes
        else 0.9f
        val newSleepDuration = (newTimeInBed * ratio).toInt()
        val newEfficiency = if (newTimeInBed > 0) (newSleepDuration.toFloat() / newTimeInBed * 100) else 0f

        return session.copy(
            startTimeMillis = start,
            endTimeMillis = end,
            timeInBedMinutes = newTimeInBed,
            sleepDurationMinutes = newSleepDuration,
            sleepEfficiency = newEfficiency
        )
    }

    /**
     * Extend a session (e.g., user fell back asleep after initial wake detection).
     *
     * @param session Original session
     * @param additionalMinutes Minutes to add to end time
     * @return Extended session
     */
    fun extendSession(session: SleepSession, additionalMinutes: Int): SleepSession {
        val newEnd = session.endTimeMillis + (additionalMinutes * 60_000L)
        return adjustSessionTimes(session, newEndMillis = newEnd)
    }
}
