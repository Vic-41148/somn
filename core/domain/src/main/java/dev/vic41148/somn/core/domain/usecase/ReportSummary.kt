package dev.vic41148.somn.core.domain.usecase

import dev.vic41148.somn.core.domain.model.SleepSession
import java.time.Instant
import java.time.ZoneId

/**
 * Aggregate stats over a set of completed sessions, shared by History, Trends and Home.
 * Pure functions so the math stays unit-tested independently of any ViewModel.
 */
data class ReportSummary(
    val nights: Int,
    val avgScore: Int,
    val avgDurationMinutes: Int,
    val avgEfficiencyPercent: Int,
    val avgDeepPercent: Int,
    val avgRemPercent: Int,
    val bestScore: Int,
    /** Last (most recent) score minus first (oldest) score — positive means improving. */
    val scoreDelta: Int,
    /** Consecutive calendar days with at least one session, counting back from the newest. */
    val streakNights: Int,
    val totalSleepMinutes: Int
)

/** Null when there is nothing to summarize — callers render their empty state instead. */
fun summarizeSessions(
    sessions: List<SleepSession>,
    /** Rest Mode boundary: nights on/after this are sick nights, not signal. */
    excludeSinceMillis: Long? = null
): ReportSummary? {
    val completed = sessions.filter {
        it.isCompleted && (excludeSinceMillis == null || it.startTimeMillis < excludeSinceMillis)
    }
    if (completed.isEmpty()) return null
    val byTime = completed.sortedBy { it.startTimeMillis }
    return ReportSummary(
        nights = completed.size,
        avgScore = completed.map { it.sleepScore }.average().toInt(),
        avgDurationMinutes = completed.map { it.sleepDurationMinutes }.average().toInt(),
        avgEfficiencyPercent = (completed.map { it.sleepEfficiency }.average()).toInt(),
        avgDeepPercent = completed.map { it.deepSleepPercent }.average().toInt(),
        avgRemPercent = completed.map { it.remSleepPercent }.average().toInt(),
        bestScore = completed.maxOf { it.sleepScore },
        scoreDelta = byTime.last().sleepScore - byTime.first().sleepScore,
        streakNights = currentStreak(completed, excludeSinceMillis),
        totalSleepMinutes = completed.sumOf { it.sleepDurationMinutes }
    )
}

fun currentStreak(
    sessions: List<SleepSession>,
    /** Rest Mode boundary: nights on/after this neither extend nor break the streak. */
    excludeSinceMillis: Long? = null
): Int {
    val days = sessions.filter {
        it.isCompleted && (excludeSinceMillis == null || it.startTimeMillis < excludeSinceMillis)
    }
        .map {
            Instant.ofEpochMilli(it.startTimeMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        }
        .toSortedSet()
    if (days.isEmpty()) return 0
    var streak = 1
    var cursor = days.last()
    while (days.contains(cursor.minusDays(1))) {
        cursor = cursor.minusDays(1)
        streak++
    }
    return streak
}

/** "7h 12m", "45m", or "0m" — never blank, so tiles always have something to show. */
fun formatDurationShort(totalMinutes: Int): String {
    if (totalMinutes <= 0) return "0m"
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
