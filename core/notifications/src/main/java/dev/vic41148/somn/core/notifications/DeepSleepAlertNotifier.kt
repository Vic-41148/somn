package dev.vic41148.somn.core.notifications

import dev.vic41148.somn.core.domain.model.UserProfile
import javax.inject.Inject

class DeepSleepAlertNotifier @Inject constructor(
    private val notificationEngine: NotificationEngine
) {

    fun checkAndNotify(deepSleepPercentage: Double) {
        if (deepSleepPercentage < 10.0) {
            notificationEngine.showNotification(
                id = 1001,
                channelId = NotificationEngine.CHANNEL_ALERTS,
                title = "Brain Detox Interrupted",
                content = "Your deep sleep was below 10% last night. Deep sleep is critical for glymphatic clearance (brain detox)."
            )
        }
    }
}
