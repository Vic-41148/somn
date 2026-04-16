package dev.vic41148.somn.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val notificationManager = 
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Health Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical alerts like PPDRisk or Deep Sleep Deficit"
            }

            val reportsChannel = NotificationChannel(
                CHANNEL_REPORTS,
                "Weekly Reports",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Weekly sleep summaries and habit insights"
            }

            val remindersChannel = NotificationChannel(
                CHANNEL_REMINDERS,
                "Habit Reminders",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Gentle reminders for wind-down and habits"
            }

            notificationManager.createNotificationChannels(
                listOf(alertsChannel, reportsChannel, remindersChannel)
            )
        }
    }

    fun showNotification(
        id: Int,
        channelId: String,
        title: String,
        content: String,
        intent: Intent? = null
    ) {
        val builder = NotificationCompat.Builder(context, channelId)
            // .setSmallIcon(R.drawable.ic_somn_notification) // Optional, using a generic if undefined
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(
                when (channelId) {
                    CHANNEL_ALERTS -> NotificationCompat.PRIORITY_HIGH
                    CHANNEL_REMINDERS -> NotificationCompat.PRIORITY_LOW
                    else -> NotificationCompat.PRIORITY_DEFAULT
                }
            )
            .setAutoCancel(true)

        intent?.let {
            val pendingIntent = PendingIntent.getActivity(
                context,
                id,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentIntent(pendingIntent)
        }

        notificationManager.notify(id, builder.build())
    }

    companion object {
        const val CHANNEL_ALERTS = "somn_alerts_channel"
        const val CHANNEL_REPORTS = "somn_reports_channel"
        const val CHANNEL_REMINDERS = "somn_reminders_channel"
    }
}
