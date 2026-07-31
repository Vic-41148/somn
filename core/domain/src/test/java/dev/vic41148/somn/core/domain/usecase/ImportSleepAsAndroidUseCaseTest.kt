package dev.vic41148.somn.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import dev.vic41148.somn.core.domain.model.SessionType
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ImportSleepAsAndroidUseCaseTest {

    private val useCase = ImportSleepAsAndroidUseCase()

    /** Mirrors the use case's own (per-row-timezone, non-lenient) parsing so expectations aren't hardcoded epoch millis. */
    private fun epochMillisOf(raw: String, zoneId: String = TimeZone.getDefault().id): Long =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone(zoneId)
        }.parse(raw)!!.time

    @Test
    fun invoke_emptyFile_returnsEmptyResultWithWarning() {
        val result = useCase("")

        assertThat(result.sessions).isEmpty()
        assertThat(result.skippedRowCount).isEqualTo(0)
        assertThat(result.warnings).contains("File is empty.")
    }

    @Test
    fun invoke_missingFromToColumns_skipsEveryRowWithWarning() {
        val csv = """
            Id,Comment
            1,foo
            2,bar
        """.trimIndent()

        val result = useCase(csv)

        assertThat(result.sessions).isEmpty()
        assertThat(result.skippedRowCount).isEqualTo(2)
        assertThat(result.warnings.any { it.contains("From") }).isTrue()
    }

    @Test
    fun invoke_semicolonDelimited_parsesFullRowCorrectly() {
        val csv = "Id;Tz;From;To;Sched;Hours;Rating;Comment;DeepSleep\n" +
            "1;Europe/Prague;2026-01-01 22:30:00;2026-01-02 06:30:00;06:30;8.0;4.5;Slept well;18.5"

        val result = useCase(csv)

        assertThat(result.sessions).hasSize(1)
        assertThat(result.skippedRowCount).isEqualTo(0)

        val session = result.sessions.first()
        assertThat(session.startTimeMillis).isEqualTo(epochMillisOf("2026-01-01 22:30:00", "Europe/Prague"))
        assertThat(session.endTimeMillis).isEqualTo(epochMillisOf("2026-01-02 06:30:00", "Europe/Prague"))
        assertThat(session.timeInBedMinutes).isEqualTo(480)
        // Hours=8.0 matches the 480-minute time-in-bed window exactly here, so duration==timeInBed
        // and efficiency==100 — but both are now *computed* from Hours, not hardcoded (see the
        // separate imperfect-efficiency test below, which proves the computation actually runs).
        assertThat(session.sleepDurationMinutes).isEqualTo(480)
        assertThat(session.sleepEfficiency).isEqualTo(100f)
        // Sleep as Android's 0.0-5.0 rating rounds to Somn's 1-5 int scale: round(4.5) == 5.
        assertThat(session.moodRating).isEqualTo(5)
        assertThat(session.notes).isEqualTo("Imported from Sleep as Android: Slept well")
        assertThat(session.deepSleepPercent).isEqualTo(18.5f)
        assertThat(session.timezoneId).isEqualTo("Europe/Prague")
        assertThat(session.sessionType).isEqualTo(SessionType.MAIN_SLEEP)
        assertThat(session.isCompleted).isTrue()
    }

    @Test
    fun invoke_hoursLessThanTimeInBed_computesRealEfficiencyNotHundredPercent() {
        // 8h in bed (22:00-06:00), but Hours=6.5 asleep -> imperfect night, not the old hardcoded 100%.
        val csv = "From,To,Hours\n2026-01-01 22:00:00,2026-01-02 06:00:00,6.5"

        val result = useCase(csv)

        val session = result.sessions.first()
        assertThat(session.timeInBedMinutes).isEqualTo(480)
        assertThat(session.sleepDurationMinutes).isEqualTo(390) // 6.5h
        assertThat(session.sleepEfficiency).isEqualTo(390f / 480f * 100)
        assertThat(session.sleepEfficiency).isLessThan(100f)
    }

    @Test
    fun invoke_missingHoursColumn_marksEfficiencyUnknownNotPerfect() {
        val csv = "From,To\n2026-01-01 22:00:00,2026-01-02 06:00:00"

        val result = useCase(csv)

        val session = result.sessions.first()
        // No Hours column at all -> efficiency is unknown (0f, the codebase's "unknown" convention),
        // never fabricated as 100f. Duration falls back to time-in-bed as the least-wrong estimate.
        assertThat(session.sleepEfficiency).isEqualTo(0f)
        assertThat(session.sleepDurationMinutes).isEqualTo(480)
    }

    @Test
    fun invoke_hoursGreaterThanTimeInBed_clampsDurationRatherThanExceedingIt() {
        // Malformed/inconsistent row: Hours claims more sleep than the From/To window allows.
        val csv = "From,To,Hours\n2026-01-01 22:00:00,2026-01-02 06:00:00,9.0"

        val result = useCase(csv)

        val session = result.sessions.first()
        assertThat(session.sleepDurationMinutes).isEqualTo(480) // clamped to timeInBedMinutes, not 540
        assertThat(session.sleepEfficiency).isEqualTo(100f)
    }

    @Test
    fun invoke_commaDelimited_stillParses() {
        val csv = "From,To,Rating\n2026-01-01 22:00:00,2026-01-02 06:00:00,3.0"

        val result = useCase(csv)

        assertThat(result.sessions).hasSize(1)
        assertThat(result.sessions.first().timeInBedMinutes).isEqualTo(480)
        // round(3.0) == 3
        assertThat(result.sessions.first().moodRating).isEqualTo(3)
    }

    @Test
    fun invoke_unparseableTimestamp_skipsRowAndCountsIt() {
        val csv = "From,To\nnot-a-date,2026-01-02 06:00:00"

        val result = useCase(csv)

        assertThat(result.sessions).isEmpty()
        assertThat(result.skippedRowCount).isEqualTo(1)
        assertThat(result.warnings.any { it.contains("Row 2") }).isTrue()
    }

    @Test
    fun invoke_toBeforeOrEqualFrom_skipsRow() {
        val csv = "From,To\n2026-01-02 06:00:00,2026-01-02 06:00:00"

        val result = useCase(csv)

        assertThat(result.sessions).isEmpty()
        assertThat(result.skippedRowCount).isEqualTo(1)
    }

    @Test
    fun invoke_missingOrNonNumericRating_defaultsMoodRatingToZero() {
        val csv = "From,To,Rating\n2026-01-01 22:00:00,2026-01-02 06:00:00,n/a"

        val result = useCase(csv)

        assertThat(result.sessions.first().moodRating).isEqualTo(0)
    }

    @Test
    fun invoke_invalidTimezone_fallsBackToSystemDefaultNotGarbageString() {
        val csv = "From,To,Tz\n2026-01-01 22:00:00,2026-01-02 06:00:00,Not/A/RealZone"

        val result = useCase(csv)

        assertThat(result.sessions.first().timezoneId).isEqualTo(TimeZone.getDefault().id)
    }

    @Test
    fun invoke_sameWallClockDifferentTimezone_producesDifferentEpochMillis() {
        // Same literal date/time string, different Tz column -> must resolve to different instants.
        // Would fail under the old implementation, which parsed every row in the *importing
        // device's* timezone regardless of what the row's own Tz column said.
        val csv = "From,To,Tz\n" +
            "2026-06-01 22:00:00,2026-06-02 06:00:00,Pacific/Auckland\n" +
            "2026-06-01 22:00:00,2026-06-02 06:00:00,America/Los_Angeles"

        val result = useCase(csv)

        assertThat(result.sessions).hasSize(2)
        assertThat(result.sessions[0].startTimeMillis).isNotEqualTo(result.sessions[1].startTimeMillis)
        assertThat(result.sessions[0].startTimeMillis)
            .isEqualTo(epochMillisOf("2026-06-01 22:00:00", "Pacific/Auckland"))
        assertThat(result.sessions[1].startTimeMillis)
            .isEqualTo(epochMillisOf("2026-06-01 22:00:00", "America/Los_Angeles"))
    }

    @Test
    fun invoke_repeatedHeaderRowMidFile_skippedWithoutCountingAsError() {
        // Real Sleep as Android exports interleave a header row before each record block, not
        // just once at the top. This must not be counted as a parse failure.
        val csv = "From,To\n" +
            "2026-01-01 22:00:00,2026-01-02 06:00:00\n" +
            "From,To\n" +
            "2026-01-03 22:00:00,2026-01-04 06:00:00"

        val result = useCase(csv)

        assertThat(result.sessions).hasSize(2)
        assertThat(result.skippedRowCount).isEqualTo(0)
        assertThat(result.warnings.any { it.contains("repeated header") }).isTrue()
    }

    // ---- Real-world validation (Task 13) ----
    // Fetched from urbandroid-team/sleep-csv-to-json (sleep-test2.csv) — the OFFICIAL sample
    // published by Urbandroid, the makers of Sleep as Android, in their own GitHub org. This is
    // the actual confidence upgrade Task 13 asked for: not community-documented assumptions, a
    // real vendor-published file. Testing against it caught two real bugs the earlier
    // community-documentation-only version had: fields are double-quoted (this parser's own doc
    // comment claimed otherwise), and long Comment fields routinely contain literal embedded
    // commas that a naive line.split(',') would have sheared into misaligned columns.

    @Test
    fun invoke_realUrbandroidSample_quotedFieldsParseCleanly() {
        // Rows 1-4 of the real file verbatim: header, one data row, a repeated header, a second
        // data row. Short and clean (no attached movement-graph columns) — proves basic
        // quote-stripping and the real dd. MM. yyyy H:mm date format (note: single-digit hour,
        // "0:50" not "00:50") work against genuine vendor output, not just hand-written fixtures.
        val csv = "Id,Tz,From,To,Sched,Hours,Rating,Comment,Framerate,Snore,Noise,Cycles,DeepSleep,LenAdjust,Geo\n" +
            "\"1426377046792\",\"Europe/Prague\",\"15. 03. 2015 0:50\",\"15. 03. 2015 7:50\",\"15. 03. 2015 7:50\",\"7.000\",\"0.0\",\" zadny sen zadny sen\",\"10000\",\"-1\",\"-1.0\",\"-1\",\"-2.0\",\"0\",\"\"\n" +
            "Id,Tz,From,To,Sched,Hours,Rating,Comment,Framerate,Snore,Noise,Cycles,DeepSleep,LenAdjust,Geo\n" +
            "\"1426283734181\",\"Europe/Prague\",\"13. 03. 2015 22:55\",\"14. 03. 2015 5:55\",\"14. 03. 2015 5:55\",\"7.000\",\"0.0\",\"zggggggggggffggggggggg\",\"10000\",\"-1\",\"-1.0\",\"-1\",\"-2.0\",\"0\",\"\""

        val result = useCase(csv)

        assertThat(result.sessions).hasSize(2)
        assertThat(result.skippedRowCount).isEqualTo(0)
        assertThat(result.warnings.any { it.contains("repeated header") }).isTrue()

        val first = result.sessions.first()
        // No literal quote characters leaking into parsed values — the bug this fix targets.
        assertThat(first.notes).doesNotContain("\"")
        assertThat(first.timezoneId).isEqualTo("Europe/Prague")
        assertThat(first.timeInBedMinutes).isEqualTo(7 * 60) // 0:50 -> 7:50
        assertThat(first.sleepDurationMinutes).isEqualTo(7 * 60) // Hours=7.000
    }

    @Test
    fun invoke_realUrbandroidSample_embeddedCommaInQuotedCommentDoesNotShearColumns() {
        // Reproduces the exact structural pattern found in the real file's row 12: a quoted
        // Comment field containing literal commas. Without quote-aware splitting, this would
        // shear into extra phantom columns and misalign every field after Comment (Framerate,
        // Snore, Noise, DeepSleep, ...), corrupting deepSleepPercent/hours/etc for the row.
        val csv = "Id,Tz,From,To,Sched,Hours,Rating,Comment,Framerate,Snore,Noise,Cycles,DeepSleep,LenAdjust,Geo\n" +
            "\"1426029897173\",\"Europe/Prague\",\"11. 03. 2015 0:24\",\"11. 03. 2015 7:24\",\"11. 03. 2015 7:24\"," +
            "\"7.000\",\"3.75\",\"jsem z budoucnosti v dobe komunismu, jsme s nejakyma kamaradkama na ostrove, kolem nehoz\"," +
            "\"10000\",\"-1\",\"-1.0\",\"-1\",\"-2.0\",\"0\",\"\""

        val result = useCase(csv)

        assertThat(result.sessions).hasSize(1)
        assertThat(result.skippedRowCount).isEqualTo(0)

        val session = result.sessions.first()
        // The whole comma-containing sentence must survive as ONE field, not be truncated at the
        // first comma (which is what naive splitting would have done).
        assertThat(session.notes).contains("v dobe komunismu, jsme s nejakyma kamaradkama na ostrove, kolem nehoz")
        // Fields AFTER Comment must still be correctly aligned despite the embedded commas —
        // deepSleepPercent (column 13) is "-2.0", outside 0..100, so it should fall back to 0f
        // per the existing out-of-range guard, NOT be misread as some fragment of the comment.
        assertThat(session.deepSleepPercent).isEqualTo(0f)
        assertThat(session.timeInBedMinutes).isEqualTo(7 * 60)
        assertThat(session.sleepDurationMinutes).isEqualTo(7 * 60) // Hours=7.000, matches time in bed
    }

    @Test
    fun invoke_realUrbandroidSample_graphContinuationRowWithEmptyLeadingFieldsIsSkippedGracefully() {
        // Reproduces the real file's "graph continuation" row shape: mostly-empty leading fields
        // (no Id/Tz/From/To of its own — it's a second data array for the same night, e.g. a
        // parallel noise-sample channel) followed by many quoted numeric values. Must degrade to
        // a normal skipped-row, not crash on empty-string parsing.
        val csv = "Id,Tz,From,To,Sched,Hours,Rating,Comment,Framerate,Snore,Noise,Cycles,DeepSleep,LenAdjust,Geo\n" +
            ",,,,,,,,,,,,,\"27067.48\",\"27138.041\",\"9513.267\""

        val result = useCase(csv)

        assertThat(result.sessions).isEmpty()
        assertThat(result.skippedRowCount).isEqualTo(1)
    }

    @Test
    fun invoke_mixOfValidAndInvalidRows_reportsCountsSeparately() {
        val csv = "From,To\n" +
            "2026-01-01 22:00:00,2026-01-02 06:00:00\n" +
            "garbage,garbage\n" +
            "2026-01-03 23:00:00,2026-01-04 07:00:00"

        val result = useCase(csv)

        assertThat(result.sessions).hasSize(2)
        assertThat(result.skippedRowCount).isEqualTo(1)
        assertThat(result.importedCount).isEqualTo(2)
        assertThat(result.warnings.first()).contains("1 row(s)")
    }
}
