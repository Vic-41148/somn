package dev.vic41148.somn.core.domain.model

/**
 * Domain model for an alarm configuration.
 */
data class Alarm(
    val id: Long = 0,
    val hour: Int,                      // 0-23
    val minute: Int,                    // 0-59
    val label: String = "",
    val isEnabled: Boolean = true,
    val repeatDays: Set<Int> = emptySet(),  // Calendar.MONDAY, etc.
    val wakeWindowMinutes: Int = 30,
    val snoozeDurationMinutes: Int = 9,
    val maxSnoozeCount: Int = 3,
    val soundUri: String = "",
    val vibrationEnabled: Boolean = true,
    val gradualVolumeSeconds: Int = 60,
    val captchaType: CaptchaType = CaptchaType.NONE
) {
    val timeFormatted: String
        get() {
            val h = if (hour % 12 == 0) 12 else hour % 12
            val amPm = if (hour < 12) "AM" else "PM"
            return String.format("%d:%02d %s", h, minute, amPm)
        }
}

enum class CaptchaType(val displayName: String) {
    NONE("None"),
    MATH("Math Problem"),
    SHAKE("Shake Phone"),
    QR_SCAN("QR Code Scan")
}
