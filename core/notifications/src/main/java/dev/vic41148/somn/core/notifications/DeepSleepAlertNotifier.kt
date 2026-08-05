package dev.vic41148.somn.core.notifications

import dev.vic41148.somn.core.domain.model.UserProfile
import javax.inject.Inject

class DeepSleepAlertNotifier @Inject constructor(
    private val notificationEngine: NotificationEngine
) {

    fun checkAndNotify(deepSleepPercentage: Double) {
        if (deepSleepPercentage < 10.0) {
            notificationEngine.showNotification(
                // 1004, NOT 1001: the sleep-tracking FGS uses id 1001 for its ongoing
                // notification, and stopForeground(STOP_FOREGROUND_REMOVE) at session end
                // removes it. When this alert posted as 1001, the FGS teardown raced the
                // ViewModel's notifyMorningAlerts and silently deleted the deep-sleep alert
                // ~half the time (the Luteal alert, id 1003, always survived — that's how the
                // session e2e caught it). Alerts must never share ids with FGS notifications.
                id = 1004,
                channelId = NotificationEngine.CHANNEL_ALERTS,
                title = "Brain Detox Interrupted",
                content = "Your deep sleep was below 10% last night. Deep sleep is critical for glymphatic clearance (brain detox)."
            )
        }
    }
}
