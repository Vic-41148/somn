package dev.vic41148.somn.core.domain.model

object AlarmPreferences {
    /** 
     * Maximum number of times the user can hit "Snooze" 
     * before the snooze option is disabled and they are forced to dismiss. 
     */
    var maxSnoozeCount: Int = 3
    var selectedCaptchaTaskId: String = "math"
    var qrCodeValue: String? = null
}
