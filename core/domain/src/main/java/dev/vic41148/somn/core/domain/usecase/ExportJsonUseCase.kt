package dev.vic41148.somn.core.domain.usecase

import dev.vic41148.somn.core.domain.model.SleepSession
import org.json.JSONArray
import org.json.JSONObject

/**
 * DATA-01: exports sleep sessions as a JSON array — Somn's own schema, full-fidelity (unlike
 * [ExportCsvUseCase], every domain field round-trips). Nullable fields are simply omitted from
 * the object rather than written as JSON null, keeping the output compact.
 */
class ExportJsonUseCase {

    operator fun invoke(sessions: List<SleepSession>): String {
        val array = JSONArray()

        for (session in sessions) {
            val obj = JSONObject()
            obj.put("id", session.id)
            obj.put("startTimeMillis", session.startTimeMillis)
            obj.put("endTimeMillis", session.endTimeMillis)
            obj.put("sleepDurationMinutes", session.sleepDurationMinutes)
            obj.put("timeInBedMinutes", session.timeInBedMinutes)
            obj.put("sleepEfficiency", session.sleepEfficiency.toDouble())
            obj.put("sleepOnsetMinutes", session.sleepOnsetMinutes)
            obj.put("wakeEvents", session.wakeEvents)
            obj.put("deepSleepPercent", session.deepSleepPercent.toDouble())
            obj.put("lightSleepPercent", session.lightSleepPercent.toDouble())
            obj.put("remSleepPercent", session.remSleepPercent.toDouble())
            obj.put("sleepScore", session.sleepScore)
            obj.put("moodRating", session.moodRating)
            obj.put("notes", session.notes)
            obj.put("isCompleted", session.isCompleted)
            obj.put("timezoneId", session.timezoneId)
            obj.put("isHomeSleep", session.isHomeSleep)
            obj.put("alarmUsed", session.alarmUsed)
            session.avgBreathingRateBrpm?.let { obj.put("avgBreathingRateBrpm", it.toDouble()) }
            obj.put("coughEventCount", session.coughEventCount)
            obj.put("isPartial", session.isPartial)
            obj.put("sessionType", session.sessionType.name)
            obj.put("isOversleep", session.isOversleep)
            session.healthConnectRecordId?.let { obj.put("healthConnectRecordId", it) }

            array.put(obj)
        }

        return array.toString(2)
    }
}
