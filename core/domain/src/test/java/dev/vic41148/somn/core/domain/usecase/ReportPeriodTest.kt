package dev.vic41148.somn.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import dev.vic41148.somn.core.domain.model.SleepSession
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReportPeriodTest {

    private val zone = ZoneId.systemDefault()

    private fun sessionDaysAgo(
        daysAgo: Long,
        score: Int = 70,
        completed: Boolean = true
    ): SleepSession {
        val date = LocalDate.now().minusDays(daysAgo)
        return SleepSession(
            startTimeMillis = date.atTime(23, 0).atZone(zone).toInstant().toEpochMilli(),
            endTimeMillis = date.plusDays(1).atTime(7, 0).atZone(zone).toInstant().toEpochMilli(),
            sleepDurationMinutes = 420,
            timeInBedMinutes = 450,
            sleepEfficiency = 90f,
            deepSleepPercent = 18f,
            remSleepPercent = 22f,
            sleepScore = score,
            isCompleted = completed
        )
    }

    @Test
    fun `empty window returns null summary`() {
        val report = buildPeriodReport(emptyList(), ReportWindow.WEEK)
        assertThat(report.summary).isNull()
        assertThat(report.nightsTracked).isEqualTo(0)
        assertThat(report.calibration).isEqualTo("0/7 nights")
    }

    @Test
    fun `week window excludes older nights`() {
        val sessions = listOf(sessionDaysAgo(2), sessionDaysAgo(30))
        val report = buildPeriodReport(sessions, ReportWindow.WEEK)
        assertThat(report.nightsTracked).isEqualTo(1)
        assertThat(report.calibration).isEqualTo("1/7 nights")
    }

    @Test
    fun `month window includes week-old nights`() {
        val sessions = listOf(sessionDaysAgo(2), sessionDaysAgo(20), sessionDaysAgo(60))
        val report = buildPeriodReport(sessions, ReportWindow.MONTH)
        assertThat(report.nightsTracked).isEqualTo(2)
        assertThat(report.calibration).isEqualTo("2/30 nights")
    }

    @Test
    fun `year window covers twelve months`() {
        val sessions = listOf(sessionDaysAgo(2), sessionDaysAgo(300), sessionDaysAgo(400))
        val report = buildPeriodReport(sessions, ReportWindow.YEAR)
        assertThat(report.nightsTracked).isEqualTo(2)
        assertThat(report.calibration).isEqualTo("2/365 nights")
    }

    @Test
    fun `incomplete sessions never count`() {
        val sessions = listOf(sessionDaysAgo(1, completed = false))
        val report = buildPeriodReport(sessions, ReportWindow.WEEK)
        assertThat(report.summary).isNull()
    }

    @Test
    fun `rest mode cut removes sick nights`() {
        val sick = sessionDaysAgo(1, score = 90)
        val healthy = sessionDaysAgo(3, score = 60)
        val report = buildPeriodReport(
            listOf(sick, healthy),
            ReportWindow.WEEK,
            excludeSinceMillis = sick.startTimeMillis
        )
        assertThat(report.nightsTracked).isEqualTo(1)
        assertThat(report.summary!!.avgScore).isEqualTo(60)
        assertThat(report.scoreTrend).containsExactly(60)
    }

    @Test
    fun `score trend is oldest to newest`() {
        val sessions = listOf(
            sessionDaysAgo(5, score = 50),
            sessionDaysAgo(1, score = 80)
        )
        val report = buildPeriodReport(sessions, ReportWindow.WEEK)
        assertThat(report.scoreTrend).containsExactly(50, 80).inOrder()
    }

    @Test
    fun `pdf model carries stats tags and disclaimer`() {
        val sessions = listOf(sessionDaysAgo(1, score = 80), sessionDaysAgo(2, score = 60))
        val report = buildPeriodReport(sessions, ReportWindow.WEEK)
        val model = toReportPdfModel(report, listOf("caffeine", "alcohol"))
        assertThat(model.title).contains("Weekly")
        assertThat(model.subtitle).contains("2/7 nights")
        val stats = model.sections.first { it.heading == "Sleep summary" }.rows.toMap()
        assertThat(stats["Average score"]).isEqualTo("70")
        assertThat(stats["Trend"]).isEqualTo("Improving (+20)")
        val tags = model.sections.first { it.heading == "Tags" }.rows.toMap()
        assertThat(tags["Logged tags"]).isEqualTo("alcohol, caffeine")
        val note = model.sections.first { it.heading == "About this report" }.rows.toMap()
        assertThat(note["Note"]).contains("mentioning to a doctor")
    }

    @Test
    fun `pdf model handles empty window honestly`() {
        val report = buildPeriodReport(emptyList(), ReportWindow.MONTH)
        val model = toReportPdfModel(report, emptyList())
        val stats = model.sections.first { it.heading == "Sleep summary" }.rows.toMap()
        assertThat(stats["Nights tracked"]).isEqualTo("0")
        val tags = model.sections.first { it.heading == "Tags" }.rows.toMap()
        assertThat(tags["Logged tags"]).isEqualTo("None this period")
    }

    @Test
    fun `pdf declining trend copy`() {
        val sessions = listOf(sessionDaysAgo(1, score = 50), sessionDaysAgo(2, score = 70))
        val report = buildPeriodReport(sessions, ReportWindow.WEEK)
        val model = toReportPdfModel(report, emptyList())
        val stats = model.sections.first { it.heading == "Sleep summary" }.rows.toMap()
        assertThat(stats["Trend"]).isEqualTo("Declining (-20)")
    }
}
