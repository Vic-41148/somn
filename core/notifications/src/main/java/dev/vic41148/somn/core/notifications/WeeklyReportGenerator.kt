package dev.vic41148.somn.core.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.vic41148.somn.core.data.repository.SleepRepository

/**
 * WorkManager worker that generates a weekly sleep summary notification
 * every Sunday morning. Queries the last 7 days of sessions and computes
 * averages for score, duration, and efficiency.
 */
@HiltWorker
class WeeklyReportGenerator @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val sleepRepository: SleepRepository,
    private val notificationEngine: NotificationEngine
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val sevenDaysAgoMillis = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
            // SESS-04: naps/commute/shift sessions would dilute the nightly score/duration/efficiency averages.
            val sessions = sleepRepository.getMainSleepSessionsSince(sevenDaysAgoMillis)

            if (sessions.isEmpty()) {
                // No sessions this week — send encouragement
                notificationEngine.showNotification(
                    // 2002, NOT 2001: the alarm FGS owns id 2001 for its ongoing notification
                // (startForeground), so a weekly report sharing it would silently replace a
                // ringing alarm's notification (alerts must never share ids, per the 1001 fix).
                id = 2002,
                    channelId = NotificationEngine.CHANNEL_REPORTS,
                    title = "Weekly Sleep Report",
                    content = "No sleep sessions were tracked this week. Try tracking tonight to start building insights!"
                )
                return Result.success()
            }

            val avgScore = sessions.map { it.sleepScore }.average()
            val avgDuration = sessions.map { it.sleepDurationMinutes }.average()
            val avgEfficiency = sessions.map { it.sleepEfficiency }.average()
            val totalNights = sessions.size

            val durationHours = (avgDuration / 60).toInt()
            val durationMins = (avgDuration % 60).toInt()

            val trend = when {
                avgScore >= 80 -> "Excellent week!"
                avgScore >= 60 -> "Solid week — room to improve."
                else -> "Tough week. Focus on consistency."
            }

            val content = buildString {
                append("$totalNights nights tracked | ")
                append("Avg Score: ${avgScore.toInt()} | ")
                append("Avg Duration: ${durationHours}h ${durationMins}m | ")
                append("Avg Efficiency: ${String.format("%.0f", avgEfficiency * 100)}%\n")
                append(trend)
            }

            notificationEngine.showNotification(
                // 2002, NOT 2001: same rationale as the sibling call above (alarm FGS owns 2001).
                id = 2002,
                channelId = NotificationEngine.CHANNEL_REPORTS,
                title = "Weekly Sleep Report",
                content = content
            )

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
