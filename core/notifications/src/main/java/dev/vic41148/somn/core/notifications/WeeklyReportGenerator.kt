package dev.vic41148.somn.core.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.vic41148.somn.core.data.repository.SleepRepository
import javax.inject.Inject

/**
 * WorkManager worker that generates a weekly sleep summary notification
 * every Sunday morning. Queries the last 7 days of sessions and computes
 * averages for score, duration, and efficiency.
 */
class WeeklyReportGenerator(
    private val context: Context,
    workerParams: WorkerParameters,
    private val sleepRepository: SleepRepository,
    private val notificationEngine: NotificationEngine
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val sevenDaysAgoMillis = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
            val sessions = sleepRepository.getSessionsSince(sevenDaysAgoMillis)

            if (sessions.isEmpty()) {
                // No sessions this week — send encouragement
                notificationEngine.showNotification(
                    id = 2001,
                    channelId = NotificationEngine.CHANNEL_REPORTS,
                    title = "📊 Weekly Sleep Report",
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
                avgScore >= 80 -> "Excellent week! 🎉"
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
                id = 2001,
                channelId = NotificationEngine.CHANNEL_REPORTS,
                title = "📊 Weekly Sleep Report",
                content = content
            )

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    /**
     * Factory for manual construction (WorkManager doesn't natively support Hilt
     * without the hilt-work artefact). In production, wire this through a
     * custom WorkerFactory provided via Hilt.
     */
    class Factory @Inject constructor(
        private val sleepRepository: SleepRepository,
        private val notificationEngine: NotificationEngine
    ) {
        fun create(context: Context, workerParams: WorkerParameters): WeeklyReportGenerator {
            return WeeklyReportGenerator(context, workerParams, sleepRepository, notificationEngine)
        }
    }
}
