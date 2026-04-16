package dev.vic41148.somn.core.notifications

import dev.vic41148.somn.core.domain.model.LifeStage
import dev.vic41148.somn.core.domain.model.UserProfile
import javax.inject.Inject

class PPDRiskNotifier @Inject constructor(
    private val notificationEngine: NotificationEngine
) {

    fun checkAndNotify(profile: UserProfile, weeksFragmented: Int) {
        if (profile.lifeStage == LifeStage.POSTPARTUM && weeksFragmented >= 3) {
            notificationEngine.showNotification(
                id = 1002,
                channelId = NotificationEngine.CHANNEL_ALERTS,
                title = "Postpartum Check-in",
                content = "You've experienced 3 weeks of severe sleep fragmentation. Please remember to prioritize your mental health, and reach out to support resources if you're struggling."
            )
        }
    }
}
