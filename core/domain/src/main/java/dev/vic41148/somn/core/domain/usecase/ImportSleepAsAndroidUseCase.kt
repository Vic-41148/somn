package dev.vic41148.somn.core.domain.usecase

import dev.vic41148.somn.core.domain.model.ImportResult
import dev.vic41148.somn.core.domain.model.SessionType
import dev.vic41148.somn.core.domain.model.SleepSession
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone

/**
 * DATA-02: parses a Sleep as Android `sleep-export.csv` file into Somn [SleepSession]s.
 *
 * Sleep as Android's export format is **not officially documented by Urbandroid** — this parser
 * targets the column layout used by widely-circulated community reverse-engineering write-ups
 * (`Id`, `Tz`, `From`, `To`, `Sched`, `Hours`, `Rating`, `Comment`, `Framerate`, `Snore`, `Noise`,
 * `Cycles`, `DeepSleep`, `LenAdjust`, `Geo`) and is deliberately defensive: columns are matched
 * by header name (case-insensitive) rather than fixed position, unknown/extra columns are
 * ignored, and any row this parser can't confidently map to a real sleep session is skipped and
 * counted rather than guessed at. Only `From`/`To` are load-bearing — a row without both is
 * unimportable and is skipped. Everything else (rating, comment, deep-sleep%, timezone, hours) is
 * a best-effort enrichment. This is explicitly a lossy, best-effort import, not a full-fidelity
 * round-trip — Sleep as Android has no equivalent for cycle-phase/pregnancy/ADHD/ASD context,
 * per-epoch sleep stages, or audio events, so none of that carries over. Imported sessions are
 * marked in [SleepSession.notes] ("Imported from Sleep as Android: ...") rather than via
 * [SleepSession.isPartial] — that flag has a distinct, narrower meaning elsewhere (REL-02: a
 * tracking session cut short by the service dying mid-night), and reusing it here would make
 * imported nights trigger that "incomplete night" UI/logic for the wrong reason.
 */
class ImportSleepAsAndroidUseCase {

    operator fun invoke(csv: String): ImportResult {
        val lines = csv.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return ImportResult(emptyList(), 0, listOf("File is empty."))
        }

        val delimiter = if (lines.first().count { it == ';' } >= lines.first().count { it == ',' }) ';' else ','
        val header = splitRow(lines.first(), delimiter).map { it.trim().lowercase(Locale.US) }
        val headerFirstCell = header.firstOrNull()

        val fromIndex = header.indexOfFirst { it == "from" }
        val toIndex = header.indexOfFirst { it == "to" }
        val ratingIndex = header.indexOfFirst { it == "rating" }
        val commentIndex = header.indexOfFirst { it == "comment" }
        val deepSleepIndex = header.indexOfFirst { it == "deepsleep" }
        val tzIndex = header.indexOfFirst { it == "tz" || it == "timezone" }
        val hoursIndex = header.indexOfFirst { it == "hours" }

        if (fromIndex == -1 || toIndex == -1) {
            return ImportResult(
                emptyList(),
                lines.size - 1,
                listOf(
                    "Couldn't find 'From'/'To' columns in the header — this doesn't look like a " +
                        "Sleep as Android sleep-export.csv, or its format has changed since this " +
                        "importer was written."
                )
            )
        }

        val sessions = mutableListOf<SleepSession>()
        var skipped = 0
        var repeatedHeaderRows = 0
        val warnings = mutableListOf<String>()

        for ((rowNumber, line) in lines.drop(1).withIndex()) {
            val cols = splitRow(line, delimiter)

            // Real Sleep as Android exports interleave a repeated header row before each record
            // block, not just once at the top of the file. Skip those without counting them as
            // an import failure — they aren't malformed data, they're structural noise.
            val firstCell = cols.firstOrNull()?.trim()?.lowercase(Locale.US)
            if (headerFirstCell != null && firstCell == headerFirstCell) {
                repeatedHeaderRows++
                continue
            }

            // Resolved before timestamp parsing (not after, as in an earlier version of this
            // parser) — From/To need the row's own timezone to parse correctly, not just to be
            // labelled with one after the fact.
            val timezoneId = resolveTimezoneId(tzIndex, cols)
            val zone = runCatching { TimeZone.getTimeZone(ZoneId.of(timezoneId)) }.getOrDefault(TimeZone.getDefault())

            val from = cols.getOrNull(fromIndex)?.let { parseTimestamp(it, zone) }
            val to = cols.getOrNull(toIndex)?.let { parseTimestamp(it, zone) }

            if (from == null || to == null || to <= from) {
                skipped++
                if (warnings.size < MAX_WARNINGS) {
                    warnings.add("Row ${rowNumber + 2}: unparseable or invalid From/To timestamps — skipped.")
                }
                continue
            }

            val timeInBedMinutes = ((to - from) / 60_000L).toInt()

            // `Hours` is Sleep as Android's actual *time asleep* — From/To only bounds time in
            // bed. Without it, duration/efficiency are unknown rather than assumed-perfect: a
            // prior version of this parser set sleepDurationMinutes = timeInBedMinutes and
            // sleepEfficiency = 100f unconditionally, which fabricated a perfect night for every
            // imported row and fed a real, persisted, inflated sleep score into the scoring
            // engine for every import.
            val hoursAsleep = hoursIndex.takeIf { it >= 0 }?.let { cols.getOrNull(it)?.toFloatOrNull() }
                ?.takeIf { it >= 0f }
            val sleepDurationMinutes: Int
            val sleepEfficiency: Float
            if (hoursAsleep != null) {
                sleepDurationMinutes = (hoursAsleep * 60).toInt().coerceIn(0, timeInBedMinutes)
                sleepEfficiency = if (timeInBedMinutes > 0) {
                    (sleepDurationMinutes.toFloat() / timeInBedMinutes * 100).coerceIn(0f, 100f)
                } else 0f
            } else {
                // Unknown, not assumed-perfect. Falling back to time-in-bed for duration (better
                // than 0, since the app displays this number directly) while being explicit that
                // efficiency is not known.
                sleepDurationMinutes = timeInBedMinutes
                sleepEfficiency = 0f
            }

            val rating = ratingIndex.takeIf { it >= 0 }?.let { cols.getOrNull(it)?.toFloatOrNull() }
            // Sleep as Android's rating is a 0.0-5.0 float; Somn's moodRating is a 1-5 int scale.
            val moodRating = rating?.let { Math.round(it).coerceIn(0, 5) } ?: 0
            val comment = commentIndex.takeIf { it >= 0 }?.let { cols.getOrNull(it) }?.trim().orEmpty()
            val deepSleepPercent = deepSleepIndex.takeIf { it >= 0 }
                ?.let { cols.getOrNull(it)?.toFloatOrNull() }
                ?.takeIf { it in 0f..100f } ?: 0f

            sessions.add(
                SleepSession(
                    startTimeMillis = from,
                    endTimeMillis = to,
                    sleepDurationMinutes = sleepDurationMinutes,
                    timeInBedMinutes = timeInBedMinutes,
                    sleepEfficiency = sleepEfficiency,
                    moodRating = moodRating,
                    notes = if (comment.isNotBlank()) "Imported from Sleep as Android: $comment" else "Imported from Sleep as Android",
                    isCompleted = true,
                    timezoneId = timezoneId,
                    deepSleepPercent = deepSleepPercent,
                    sessionType = SessionType.MAIN_SLEEP
                )
            )
        }

        if (repeatedHeaderRows > 0) {
            warnings.add("Ignored $repeatedHeaderRows repeated header row(s) embedded in the file (normal for Sleep as Android exports).")
        }
        if (skipped > 0) {
            warnings.add(0, "$skipped row(s) could not be imported (see below).")
        }

        return ImportResult(sessions, skipped, warnings)
    }

    /**
     * ZoneId.of (not TimeZone.getTimeZone, which silently falls back to GMT and never fails) —
     * every existing consumer of session.timezoneId validates it this same way
     * (ChronotypeAssessmentUseCase, SocialJetLagUseCase, SeasonalAnalysisUseCase), so matching
     * that convention here keeps a garbage timezoneId from ever reaching a consumer in the first
     * place, rather than relying on each consumer's own guard to catch it downstream.
     */
    private fun resolveTimezoneId(tzIndex: Int, cols: List<String>): String =
        tzIndex.takeIf { it >= 0 }
            ?.let { cols.getOrNull(it)?.trim() }
            ?.takeIf { it.isNotBlank() && runCatching { ZoneId.of(it) }.isSuccess }
            ?: TimeZone.getDefault().id

    /**
     * Minimal RFC4180-style CSV row splitter — quote-aware, so a delimiter character inside a
     * quoted field doesn't split it. Verified against a real Urbandroid-published sample export
     * (urbandroid-team/sleep-csv-to-json): every field is double-quoted, and long diary-style
     * `Comment` fields routinely contain literal commas ("...v dobe komunismu, jsme s nejakyma
     * kamaradkama na ostrove, kolem nehoz...") — the earlier naive `line.split(delimiter)` (this
     * parser's own doc comment claimed "Sleep as Android doesn't quote fields," which was wrong)
     * would have sheared every such row into misaligned columns. Handles doubled `""` inside a
     * quoted field as an escaped literal quote, per RFC4180.
     */
    private fun splitRow(line: String, delimiter: Char): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++ // consume both quote characters of the escaped pair
                }
                c == '"' -> inQuotes = !inQuotes
                c == delimiter && !inQuotes -> {
                    fields.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        fields.add(current.toString())
        return fields
    }

    private val dateFormats = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "dd. MM. yyyy HH:mm"
    )

    /** [zone] is the row's own resolved timezone — parsing in the importing device's zone instead would silently shift every imported timestamp whenever the two differ. */
    private fun parseTimestamp(raw: String, zone: TimeZone): Long? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        for (pattern in dateFormats) {
            try {
                val format = SimpleDateFormat(pattern, Locale.US)
                format.isLenient = false
                format.timeZone = zone
                return format.parse(trimmed)?.time
            } catch (_: ParseException) {
                // try next pattern
            } catch (_: IllegalArgumentException) {
                // malformed pattern for this input shape — try next
            }
        }
        return null
    }

    private companion object {
        const val MAX_WARNINGS = 20
    }
}
