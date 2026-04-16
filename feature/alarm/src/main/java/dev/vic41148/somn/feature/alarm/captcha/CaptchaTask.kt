package dev.vic41148.somn.feature.alarm.captcha

import androidx.compose.runtime.Composable

/**
 * Interface for CAPTCHA tasks that must be completed to dismiss an alarm.
 */
interface CaptchaTask {
    val id: String
    val displayName: String
    
    /**
     * Returns true if the task has been successfully completed.
     */
    fun isComplete(): Boolean

    /**
     * Resets the task state for a new alarm event.
     */
    fun reset()

    /**
     * Optional UI component for the task.
     */
    @Composable
    fun TaskUI(onComplete: () -> Unit)
}
