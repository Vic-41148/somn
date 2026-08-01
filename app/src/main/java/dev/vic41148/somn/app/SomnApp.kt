package dev.vic41148.somn.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import dev.vic41148.somn.core.data.retention.ClipRetentionWorker
import dev.vic41148.somn.core.notifications.WeeklyReportGenerator
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class SomnApp : Application(), Configuration.Provider {
    @Inject
    lateinit var preferencesRepository: dev.vic41148.somn.core.data.repository.SomnPreferencesRepository

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        scheduleWeeklyReport()
        scheduleClipRetention()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun scheduleWeeklyReport() {
        val now = LocalDateTime.now()
        var nextSunday = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            .with(LocalTime.of(8, 0))
        if (nextSunday.isBefore(now)) {
            nextSunday = nextSunday.plusWeeks(1)
        }
        val initialDelayMillis = java.time.Duration.between(now, nextSunday).toMillis()

        val request = PeriodicWorkRequestBuilder<WeeklyReportGenerator>(7, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "weekly_sleep_report",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Prunes expired sleep-talk recordings twice a day. The worker itself re-reads the retention
     * preference on every run, so changing the setting takes effect without rescheduling — hence
     * KEEP rather than UPDATE.
     */
    private fun scheduleClipRetention() {
        val request = PeriodicWorkRequestBuilder<ClipRetentionWorker>(
            ClipRetentionWorker.INTERVAL_HOURS, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ClipRetentionWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
