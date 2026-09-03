package dev.vic41148.somn.core.data.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns WorkManager enqueueing for update checks. A single named periodic job (rescued by the
 * boot- and version-update reschedulers on the one device that matters - this one) plus an
 * immediate one-shot for "Check for updates now". Both carry a network constraint so a dead radio
 * never costs us a manual check.
 */
@Singleton
class UpdateScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** Enqueues the daily periodic check; does nothing if it already exists. */
    fun ensureScheduled() {
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
            PERIOD_DAILY_DAYS, TimeUnit.DAYS
        )
            .setConstraints(updateConstraints())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UpdateCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** Replaces the periodic job with a new interval (1 = daily, 7 = weekly) after the user picks it. */
    fun rescheduleForInterval(days: Int) {
        val clamped = days.coerceIn(1, 7)
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
            clamped.toLong(), TimeUnit.DAYS
        )
            .setConstraints(updateConstraints())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UpdateCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            request
        )
    }

    /** Fires an immediate forced check, REPLACE-ing any queued manual check. */
    fun checkNow() {
        val request = OneTimeWorkRequestBuilder<UpdateCheckWorker>()
            .setConstraints(updateConstraints())
            .setInputData(workDataOf(UpdateCheckWorker.INPUT_FORCE to true))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UpdateCheckWorker.MANUAL_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun updateConstraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private companion object {
        const val PERIOD_DAILY_DAYS = 1L
    }
}