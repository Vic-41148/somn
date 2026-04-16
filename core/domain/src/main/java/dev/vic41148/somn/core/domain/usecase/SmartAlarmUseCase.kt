package dev.vic41148.somn.core.domain.usecase

import dev.vic41148.somn.core.domain.model.SleepStage

/**
 * Smart Alarm logic. Determines if an alarm should trigger early
 * based on the user's current sleep stage, trying to avoid waking
 * them up from deep sleep (to minimize sleep inertia).
 */
class SmartAlarmUseCase {

    /**
     * @param currentTimeMillis Current epoch time in ms.
     * @param alarmTimeMillis Scheduled alarm time in ms.
     * @param wakeWindowMinutes The window (in minutes) before the alarm where early waking is allowed.
     * @param currentStage The latest classified sleep stage.
     * @return True if the alarm should ring now, False otherwise.
     */
    fun shouldWakeEarly(
        currentTimeMillis: Long,
        alarmTimeMillis: Long,
        wakeWindowMinutes: Int,
        currentStage: SleepStage
    ): Boolean {
        // If the exact alarm time is reached or passed, wake up immediately
        if (currentTimeMillis >= alarmTimeMillis) {
            return true
        }

        val windowStartMillis = alarmTimeMillis - (wakeWindowMinutes * 60 * 1000L)
        
        // If we are outside the wake window, do not wake
        if (currentTimeMillis < windowStartMillis) {
            return false
        }

        // Inside the wake window: only wake if in LIGHT sleep or AWAKE
        return currentStage == SleepStage.LIGHT || currentStage == SleepStage.AWAKE
    }
}
