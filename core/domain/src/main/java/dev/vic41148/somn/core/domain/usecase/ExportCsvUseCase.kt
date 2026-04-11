package dev.vic41148.somn.core.domain.usecase

import dev.vic41148.somn.core.domain.model.SleepSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exports sleep sessions to CSV format.
 */
class ExportCsvUseCase {

    companion object {
        private const val HEADER = "Date,Bedtime,Wake Time,Duration (min),Efficiency (%),Deep Sleep (%),Light Sleep (%),Wake Events,Mood,Notes,Score"
    }

    operator fun invoke(sessions: List<SleepSession>): String {
        val sb = StringBuilder()
        sb.appendLine(HEADER)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.US)

        for (session in sessions) {
            if (!session.isCompleted) continue

            val date = dateFormat.format(Date(session.startTimeMillis))
            val bedtime = timeFormat.format(Date(session.startTimeMillis))
            val wakeTime = timeFormat.format(Date(session.endTimeMillis))
            val notes = session.notes.replace(",", ";").replace("\n", " ")

            sb.appendLine(
                "$date,$bedtime,$wakeTime," +
                "${session.sleepDurationMinutes},${String.format("%.1f", session.sleepEfficiency)}," +
                "${String.format("%.1f", session.deepSleepPercent)}," +
                "${String.format("%.1f", session.lightSleepPercent)}," +
                "${session.wakeEvents},${session.moodRating}," +
                "\"$notes\",${session.sleepScore}"
            )
        }

        return sb.toString()
    }
}
