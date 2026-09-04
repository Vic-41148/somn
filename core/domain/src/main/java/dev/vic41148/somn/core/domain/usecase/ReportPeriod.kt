package dev.vic41148.somn.core.domain.usecase

import dev.vic41148.somn.core.domain.model.SleepSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * R3 Reports: fixed reporting windows over the local session history.
 * YEAR doubles as the 12-month anniversary report — same engine, all-time data.
 */
enum class ReportWindow(val days: Int, val title: String) {
    WEEK(7, "Weekly Report"),
    MONTH(30, "Monthly Report"),
    YEAR(365, "Year in Review")
}

data class PeriodReport(
    val window: ReportWindow,
    /** Completed main-sleep nights inside the window, after the Rest Mode cut. */
    val nightsTracked: Int,
    /** "1 Sep – 5 Sep 2026" style label for headers and the PDF. */
    val windowLabel: String,
    /** "5/30 nights" calibration — every baseline-derived number shows its sample. */
    val calibration: String,
    /** Null when the window holds no usable nights — callers render the empty state. */
    val summary: ReportSummary?,
    /** Oldest→newest scores inside the window (cap applied by caller need), for PDF bars. */
    val scoreTrend: List<Int>
)

/**
 * Pure builder: filters to completed sessions inside [window] ending at [nowMillis],
 * then reuses [summarizeSessions] so report math can never drift from History math.
 */
fun buildPeriodReport(
    sessions: List<SleepSession>,
    window: ReportWindow,
    excludeSinceMillis: Long? = null,
    nowMillis: Long = System.currentTimeMillis()
): PeriodReport {
    val cutoff = nowMillis - window.days * 24 * 60 * 60 * 1000L
    val inWindow = sessions.filter { it.isCompleted && it.startTimeMillis >= cutoff }
    val summary = summarizeSessions(inWindow, excludeSinceMillis)
    val nights = summary?.nights ?: 0
    return PeriodReport(
        window = window,
        nightsTracked = nights,
        windowLabel = formatPeriodLabel(cutoff, nowMillis),
        calibration = "$nights/${window.days} nights",
        summary = summary,
        scoreTrend = inWindow
            .filter { excludeSinceMillis == null || it.startTimeMillis < excludeSinceMillis }
            .sortedBy { it.startTimeMillis }
            .map { it.sleepScore }
    )
}

private val dayMonth = DateTimeFormatter.ofPattern("d MMM")
private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy")

fun formatPeriodLabel(startMillis: Long, endMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val start: LocalDate = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
    val end: LocalDate = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate()
    return "${start.format(dayMonth)} – ${end.format(dayMonthYear)}"
}

/** Pure PDF content model — the framework renderer draws this, so the words stay unit-tested. */
data class ReportPdfSection(
    val heading: String,
    val rows: List<Pair<String, String>>
)

data class ReportPdfModel(
    val title: String,
    val subtitle: String,
    val sections: List<ReportPdfSection>
)

/** Wellness framing only: the strongest claim the PDF ever makes is "mention to a doctor". */
const val REPORT_PDF_DISCLAIMER =
    "Wellness information only — not medical advice. " +
        "If anything here concerns you, it is worth mentioning to a doctor."

fun toReportPdfModel(report: PeriodReport, tagNames: List<String>): ReportPdfModel {
    val s = report.summary
    val statsRows = if (s == null) {
        listOf("Nights tracked" to "0")
    } else {
        listOf(
            "Nights tracked" to "${s.nights}",
            "Average score" to "${s.avgScore}",
            "Average sleep" to formatDurationShort(s.avgDurationMinutes),
            "Average efficiency" to "${s.avgEfficiencyPercent}%",
            "Total sleep" to formatDurationShort(s.totalSleepMinutes),
            "Best night" to "${s.bestScore}",
            "Trend" to when {
                s.scoreDelta > 0 -> "Improving (+${s.scoreDelta})"
                s.scoreDelta < 0 -> "Declining (${s.scoreDelta})"
                else -> "Steady"
            },
            "Streak" to "${
                s.streakNights} night" + if (s.streakNights == 1) "" else "s"
        )
    }
    return ReportPdfModel(
        title = "Somn ${report.window.title}",
        subtitle = "${report.windowLabel} · ${report.calibration}",
        sections = listOf(
            ReportPdfSection("Sleep summary", statsRows),
            ReportPdfSection(
                "Tags",
                if (tagNames.isEmpty()) listOf("Logged tags" to "None this period")
                else listOf("Logged tags" to tagNames.sorted().joinToString(", "))
            ),
            ReportPdfSection("About this report", listOf("Note" to REPORT_PDF_DISCLAIMER))
        )
    )
}
