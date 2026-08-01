package dev.vic41148.somn.core.domain.usecase

import dev.vic41148.somn.core.domain.model.JetLagRisk
import dev.vic41148.somn.core.domain.model.SleepSession
import dev.vic41148.somn.core.domain.model.SocialJetLag
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

/**
 * Calculates social jet lag — the discrepancy between natural sleep timing on free days
 * (weekends) and schedule-constrained timing on work days (weekdays).
 *
 * Method (standard Munich ChronoType Questionnaire / MCTQ approach):
 *   - Partition completed sessions by weekday (Mon–Fri) vs weekend (Sat–Sun)
 *   - Compute sleep midpoint per session: (startTime + endTime) / 2
 *   - Circular-average midpoints for each group
 *   - Social jet lag = |MSF − MSW| in minutes
 *
 * Minimum data requirements:
 *   - [SocialJetLag.MIN_WEEKDAY_SESSIONS] weekday sessions
 *   - [SocialJetLag.MIN_WEEKEND_SESSIONS] weekend sessions
 *
 * Research basis §2.11 (Frontiers in Sleep 2025):
 * Social jet lag >2 hours is an independent cardiovascular risk factor.
 */
class SocialJetLagUseCase {

    /**
     * @param sessions Completed sleep sessions (typical window: last 30–90 days).
     * @return [SocialJetLag] result, or null if insufficient weekday/weekend data.
     */
    fun calculate(sessions: List<SleepSession>): SocialJetLag? {
        val completed = sessions.filter { it.isCompleted && it.endTimeMillis > 0 }

        val weekdaySessions = completed.filter { isWeekday(it) }
        val weekendSessions  = completed.filter { !isWeekday(it) }

        if (weekdaySessions.size < SocialJetLag.MIN_WEEKDAY_SESSIONS ||
            weekendSessions.size  < SocialJetLag.MIN_WEEKEND_SESSIONS) {
            return null
        }

        val weekdayMidpointMinutes = circularAverageMinutes(weekdaySessions.mapNotNull { midpointMinutes(it) })
        val weekendMidpointMinutes  = circularAverageMinutes(weekendSessions.mapNotNull  { midpointMinutes(it) })

        if (weekdayMidpointMinutes == null || weekendMidpointMinutes == null) return null

        val jetLagMinutes = circularDifferenceMinutes(weekendMidpointMinutes, weekdayMidpointMinutes)
        val riskLevel     = JetLagRisk.from(jetLagMinutes)
        val weekendIsLater = isWeekendLater(weekdayMidpointMinutes, weekendMidpointMinutes)

        val weekdayTime = toLocalTime(weekdayMidpointMinutes)
        val weekendTime  = toLocalTime(weekendMidpointMinutes)

        return SocialJetLag(
            weekdayMidpoint  = weekdayTime,
            weekendMidpoint   = weekendTime,
            jetLagMinutes     = jetLagMinutes,
            riskLevel         = riskLevel,
            weekdaySessions   = weekdaySessions.size,
            weekendSessions    = weekendSessions.size,
            insight           = buildInsight(jetLagMinutes, riskLevel, weekdayTime, weekendTime, weekendIsLater)
        )
    }

    // ---- Classification helpers ----

    /**
     * Returns true if the session started on a weekday (Mon–Fri).
     * Uses the session's stored timezone for correct local-time day-of-week.
     */
    private fun isWeekday(session: SleepSession): Boolean {
        val zone = runCatching { ZoneId.of(session.timezoneId) }.getOrDefault(ZoneId.systemDefault())
        val dow  = Instant.ofEpochMilli(session.startTimeMillis).atZone(zone).dayOfWeek
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY
    }

    /** Sleep midpoint as minutes-of-day [0, 1439], or null if session is incomplete. */
    private fun midpointMinutes(session: SleepSession): Int? {
        if (session.endTimeMillis == 0L) return null
        val midMillis = (session.startTimeMillis + session.endTimeMillis) / 2
        val zone = runCatching { ZoneId.of(session.timezoneId) }.getOrDefault(ZoneId.systemDefault())
        val t    = Instant.ofEpochMilli(midMillis).atZone(zone).toLocalTime()
        return t.hour * 60 + t.minute
    }

    private fun toLocalTime(minutes: Int): LocalTime =
        LocalTime.ofSecondOfDay((minutes * 60L).coerceIn(0, 86399))

    // ---- Circular time math ----

    private fun circularAverageMinutes(minutesList: List<Int>): Int? {
        if (minutesList.isEmpty()) return null
        if (minutesList.size == 1) return minutesList.first()
        val totalMinutesInDay = 24 * 60
        val twoPi = 2.0 * Math.PI
        var sinSum = 0.0; var cosSum = 0.0
        for (m in minutesList) {
            val angle = twoPi * m / totalMinutesInDay
            sinSum += Math.sin(angle); cosSum += Math.cos(angle)
        }
        val avgAngle   = Math.atan2(sinSum, cosSum)
        val normalized = ((avgAngle / twoPi) * totalMinutesInDay).toInt()
        return ((normalized % totalMinutesInDay) + totalMinutesInDay) % totalMinutesInDay
    }

    /**
     * Circular difference (unsigned) between two times-of-day in minutes.
     * Returns the shorter arc, so maximum is 720 (12 hours).
     */
    private fun circularDifferenceMinutes(a: Int, b: Int): Int {
        val totalMinutesInDay = 24 * 60
        val diff = abs(a - b)
        return minOf(diff, totalMinutesInDay - diff)
    }

    /**
     * True if, on the shorter circular arc, the weekend midpoint falls after the weekday one —
     * the typical direction (relaxed weekend schedule sleeps/wakes later). Shift workers and
     * other atypical schedules can reverse this, so the insight text must check rather than
     * assume it: [circularDifferenceMinutes] alone only gives an unsigned magnitude.
     */
    private fun isWeekendLater(weekdayMinutes: Int, weekendMinutes: Int): Boolean {
        val totalMinutesInDay = 24 * 60
        val forward = ((weekendMinutes - weekdayMinutes) % totalMinutesInDay + totalMinutesInDay) % totalMinutesInDay
        return forward <= totalMinutesInDay / 2
    }

    // ---- Insight builder ----

    private fun buildInsight(
        jetLagMinutes: Int,
        risk: JetLagRisk,
        weekdayTime: LocalTime,
        weekendTime: LocalTime,
        weekendIsLater: Boolean
    ): String {
        val h = jetLagMinutes / 60
        val m = jetLagMinutes % 60
        val lagStr = when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0          -> "${h}h"
            else           -> "${m}m"
        }

        val wdStr = weekdayTime.toString().substring(0, 5)
        val weStr = weekendTime.toString().substring(0, 5)
        val direction = if (weekendIsLater) "later on weekends ($weStr) than weekdays ($wdStr)"
                        else "earlier on weekends ($weStr) than weekdays ($wdStr)"

        return when (risk) {
            JetLagRisk.NONE ->
                "Your weekday ($wdStr) and weekend ($weStr) sleep midpoints are closely aligned. " +
                "Great schedule consistency."
            JetLagRisk.MILD ->
                "Your sleep midpoint is $lagStr $direction. " +
                "Mild social jet lag — worth watching."
            JetLagRisk.MODERATE ->
                "Your sleep midpoint shifts $lagStr $direction. " +
                "This level of social jet lag is associated with increased metabolic and mood risk. " +
                (if (weekendIsLater) "Try keeping your weekend morning no more than 1 hour later than usual."
                 else "Try keeping your weekday morning no more than 1 hour earlier than your weekend pattern.")
            JetLagRisk.HIGH ->
                "Your sleep midpoint shifts $lagStr $direction. " +
                "Social jet lag of this magnitude is an independent cardiovascular risk factor " +
                "(Frontiers in Sleep, 2025). This is equivalent to flying across multiple time zones " +
                "every week. Gradually aligning your schedule could significantly improve your health."
        }
    }
}
