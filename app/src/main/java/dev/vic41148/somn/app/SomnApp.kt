package dev.vic41148.somn.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import dev.vic41148.somn.app.integration.UpdateIntegration
import dev.vic41148.somn.core.data.backup.LocalBackupWorker
import dev.vic41148.somn.core.data.retention.ClipRetentionWorker
import dev.vic41148.somn.core.notifications.WeeklyReportGenerator
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltAndroidApp
class SomnApp : Application(), Configuration.Provider {
    @Inject
    lateinit var preferencesRepository: dev.vic41148.somn.core.data.repository.SomnPreferencesRepository

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var updateIntegrations: Set<@JvmSuppressWildcards UpdateIntegration>

    override fun onCreate() {
        super.onCreate()
        // Zero-telemetry crash capture first: nothing leaves the device, the log just waits
        // in app-private storage until the user copies it out of Settings → About.
        dev.vic41148.somn.core.data.diagnostics.CrashLogStore.install(this)
        scheduleWeeklyReport()
        scheduleClipRetention()
        scheduleLocalBackup()
        // One-time upgrade: seal any still-plaintext sensitive prefs (v0.1.2 installs).
        // Fire-and-forget on IO; reads tolerate both forms until it lands.
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { preferencesRepository.migrateSensitivePrefsToEncrypted() }
        }
        // Channel-scoped integrations (in-app updater scheduling on standalone builds; no-op on
        // store). Called after the base scheduling so we stay independent of app startup order.
        updateIntegrations.forEach { it.onAppCreated(this) }
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

    /**
     * Flavor-agnostic daily local backup. The worker itself no-ops until the user grants a backup
     * folder, so initial scheduling before onboarding is harmless. KEEP (not UPDATE) because a
     * daily cadence never needs re-derivation.
     */
    private fun scheduleLocalBackup() {
        val request = PeriodicWorkRequestBuilder<LocalBackupWorker>(
            LocalBackupWorker.INTERVAL_HOURS, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            LocalBackupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
