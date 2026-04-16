package dev.vic41148.somn.core.notifications

import dev.vic41148.somn.core.domain.model.MenstrualCyclePhase
import dev.vic41148.somn.core.domain.model.UserProfile
import javax.inject.Inject

class HormonalPhaseNotifier @Inject constructor(
    private val notificationEngine: NotificationEngine
) {

    fun checkAndNotify(profile: UserProfile, phase: MenstrualCyclePhase) {
        if (phase == MenstrualCyclePhase.LUTEAL) {
            notificationEngine.showNotification(
                id = 1003,
                channelId = NotificationEngine.CHANNEL_REPORTS,
                title = "Luteal Phase Alert",
                content = "You are entering the luteal phase. Progesterone levels may raise your core body temperature and fragment your sleep architecture. Keep your room cool."
            )
        }
    }
}
