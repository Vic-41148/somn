package dev.vic41148.somn.core.domain.usecase

import dev.vic41148.somn.core.domain.model.SleepSession
import java.util.concurrent.TimeUnit

/**
 * Counts consecutive weeks of severely fragmented sleep, most recent week first.
 *
 * Continuity, not duration, is what predicts postpartum depression risk — a week
 * counts as fragmented if either the average wake-event count or the average sleep
 * efficiency for that week crosses the threshold below.
 */
class PostpartumFragmentationUseCase {

    companion object {
        private const val WAKE_EVENTS_THRESHOLD = 4
        private const val EFFICIENCY_THRESHOLD = 70f
        private val WEEK_MILLIS = TimeUnit.DAYS.toMillis(7)
    }

    /**
     * @param sessionsByRecency completed sessions ordered newest-first, already
     *   scoped by the caller to a reasonable lookback window (e.g. last 6 weeks)
     * @param nowMillis the reference point from which weeks count back
     * @return number of consecutive fragmented weeks ending at [nowMillis], 0 if the
     *   most recent week is not fragmented or no sessions exist in it
     */
    operator fun invoke(sessionsByRecency: List<SleepSession>, nowMillis: Long): Int {
        var consecutiveWeeks = 0
        var weekEnd = nowMillis

        while (true) {
            val weekStart = weekEnd - WEEK_MILLIS
            val weekSessions = sessionsByRecency.filter {
                it.startTimeMillis in weekStart until weekEnd
            }
            if (weekSessions.isEmpty()) break

            val avgWakeEvents = weekSessions.map { it.wakeEvents }.average()
            val avgEfficiency = weekSessions.map { it.sleepEfficiency }.average()
            val isFragmented = avgWakeEvents >= WAKE_EVENTS_THRESHOLD || avgEfficiency < EFFICIENCY_THRESHOLD

            if (!isFragmented) break
            consecutiveWeeks++
            weekEnd = weekStart
        }

        return consecutiveWeeks
    }
}
