package dev.vic41148.somn.app.debug

import dev.vic41148.somn.core.data.repository.HabitLogRepository
import dev.vic41148.somn.core.data.repository.SleepRepository
import dev.vic41148.somn.core.data.repository.TagRepository
import dev.vic41148.somn.core.domain.model.AudioEvent
import dev.vic41148.somn.core.domain.model.AudioEventType
import dev.vic41148.somn.core.domain.model.CaffeineSource
import dev.vic41148.somn.core.domain.model.ExerciseIntensity
import dev.vic41148.somn.core.domain.model.ExerciseType
import dev.vic41148.somn.core.domain.model.ExternalVitalsSnapshot
import dev.vic41148.somn.core.domain.model.HabitEntry
import dev.vic41148.somn.core.domain.model.SessionType
import dev.vic41148.somn.core.domain.model.SleepEpoch
import dev.vic41148.somn.core.domain.model.SleepSession
import dev.vic41148.somn.core.domain.model.SleepStage
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

/**
 * Debug-only data seeder (DEBUG source set - never shipped in release builds). Inserts a week of
 * realistic, internally-consistent sleep data so the Home / Trends / History / Debt / Circadian
 * screens have something believable to render. Triggered via adb broadcast, see seed.sh.
 */
object DebugSeeder {

    suspend fun seed(
        sleepRepo: SleepRepository,
        habitRepo: HabitLogRepository,
        tagRepo: TagRepository
    ) {
        val rnd = Random(424242L)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()

        // Weekend / commute tag for a bit of cross-referencing colour.
        val weekendTag = tagRepo.createTag("Weekend", "Lifestyle", 0xFF6200EE, "weekend")

        // One night per morning for the last 7 days (day 0 = today's wake-up, newest first).
        for (dayAgo in 6 downTo 0) {
            val wakeDate = today.minusDays(dayAgo.toLong())
            val hour = 7 - (dayAgo % 3)          // later/earlier variation across the week
            val start = wakeDate.minusDays(1).atTime(22 + rnd.nextInt(2), rnd.nextInt(50))
            val end = wakeDate.atTime(hour, rnd.nextInt(50))
            val startMillis = start.atZone(zone).toInstant().toEpochMilli()
            val endMillis = end.atZone(zone).toInstant().toEpochMilli()

            val timeInBedMinutes = ((endMillis - startMillis) / 60000L).toInt()
            val onset = 8 + rnd.nextInt(18)
            val wakeEvents = rnd.nextInt(3)
            val wakeMinutes = wakeEvents * 3
            val sleepDuration = (timeInBedMinutes - onset - wakeMinutes).coerceAtLeast(300)

            val deepPct = (14 + rnd.nextInt(9)).toFloat()      // 14-22
            val remPct = (18 + rnd.nextInt(9)).toFloat()       // 18-26
            val lightPct = (100f - deepPct - remPct).coerceIn(40f, 65f)
            val efficiency = (sleepDuration.toFloat() / timeInBedMinutes * 100f)

            // Score reflects a good-but-varied week (GREAT/GOOD, occasionally FAIR).
            val durationScore = ((sleepDuration - 300) / 2f).coerceIn(40f, 100f).toInt()
            val score = (70 + rnd.nextInt(20) - (if (dayAgo == 3) 15 else 0)).coerceIn(52, 92)

            val session = SleepSession(
                startTimeMillis = startMillis,
                endTimeMillis = endMillis,
                sleepDurationMinutes = sleepDuration,
                timeInBedMinutes = timeInBedMinutes,
                sleepEfficiency = efficiency,
                sleepOnsetMinutes = onset,
                wakeEvents = wakeEvents,
                deepSleepPercent = deepPct,
                lightSleepPercent = lightPct,
                remSleepPercent = remPct,
                sleepScore = score,
                moodRating = 3 + rnd.nextInt(3),
                notes = notesFor(dayAgo),
                isCompleted = true,
                timezoneId = zone.id,
                isHomeSleep = true,
                alarmUsed = dayAgo % 2 == 0,
                avgBreathingRateBrpm = 12f + rnd.nextInt(4).toFloat(),
                coughEventCount = if (dayAgo == 2) 4 else rnd.nextInt(2),
                sessionType = SessionType.MAIN_SLEEP,
                isOversleep = false
            )

            val sessionId = sleepRepo.createSession(startMillis, zone.id, SessionType.MAIN_SLEEP)
            sleepRepo.completeSession(session.copy(id = sessionId))
            sleepRepo.insertEpochs(epochsFor(sessionId, startMillis, sleepDuration, deepPct, remPct, rnd))
            sleepRepo.upsertExternalVitals(vitalsFor(sessionId, rnd))

            // A few audio events on some nights; tag the weekend sessions.
            if (dayAgo % 2 == 1) {
                audioEventOf(sleepRepo, sessionId, startMillis, sleepDuration, AudioEventType.SNORE, rnd)
                if (rnd.nextBoolean()) {
                    audioEventOf(sleepRepo, sessionId, startMillis, sleepDuration, AudioEventType.TALK, rnd)
                }
            }
            if (wakeDate.dayOfWeek.value >= 6) {
                tagRepo.addTagToSession(sessionId, weekendTag)
            }
        }

        habitRepo.log(
            HabitEntry.Caffeine(120, LocalTime.of(8, 30), CaffeineSource.COFFEE),
            today.minusDays(0),
            "Morning coffee"
        )
        habitRepo.log(
            HabitEntry.Caffeine(47, LocalTime.of(15, 0), CaffeineSource.TEA),
            today.minusDays(2)
        )
        habitRepo.log(HabitEntry.Alcohol(2f, LocalTime.of(20, 30)), today.minusDays(1))
        habitRepo.log(
            HabitEntry.Exercise(ExerciseType.RUNNING, 30, ExerciseIntensity.MODERATE, LocalTime.of(18, 0)),
            today.minusDays(3)
        )
        habitRepo.log(HabitEntry.Stress(2), today.minusDays(3))
        habitRepo.log(HabitEntry.Stress(4), today.minusDays(1))
        habitRepo.log(HabitEntry.Stress(1), today.minusDays(0))
    }

    private fun notesFor(dayAgo: Int): String = when (dayAgo) {
        1 -> "Windy night, woke up briefly"
        3 -> "Fell asleep late after watching a film"
        5 -> "Unusually restless"
        else -> ""
    }

    /** 10-minute epochs covering the full sleep window, distributed across the given stage split. */
    private fun epochsFor(
        sessionId: Long,
        startMillis: Long,
        sleepDuration: Int,
        deepPct: Float,
        remPct: Float,
        rnd: Random
    ): List<SleepEpoch> {
        val epochs = mutableListOf<SleepEpoch>()
        val total = (sleepDuration / 10)
        val deepCount = (total * deepPct / 100f).toInt()
        val remCount = (total * remPct / 100f).toInt()
        var deepSeen = 0
        var remSeen = 0
        for (i in 0 until total) {
            val stage: SleepStage = when {
                deepSeen < deepCount && rnd.nextInt(3) == 0 -> { deepSeen++; SleepStage.DEEP }
                remSeen < remCount && rnd.nextInt(4) == 0 -> { remSeen++; SleepStage.REM }
                else -> SleepStage.LIGHT
            }
            epochs.add(
                SleepEpoch(
                    sessionId = sessionId,
                    timestampMillis = startMillis + i * 600_000L,
                    stage = stage,
                    movementMagnitude = if (stage == SleepStage.DEEP) rnd.nextFloat() * 0.2f
                    else 0.5f + rnd.nextFloat() * 1.5f,
                    movementVariability = rnd.nextFloat() * 0.5f
                )
            )
        }
        return epochs
    }

    private fun vitalsFor(sessionId: Long, rnd: Random) = ExternalVitalsSnapshot(
        sessionId = sessionId,
        avgHeartRateBpm = 48f + rnd.nextInt(12).toFloat(),
        restingHeartRateBpm = 44f + rnd.nextInt(10).toFloat(),
        avgHeartRateVariabilityMs = 40f + rnd.nextInt(28).toFloat(),
        avgSpo2Percent = 96f + rnd.nextInt(3).toFloat(),
        minSpo2Percent = 90f + rnd.nextInt(5).toFloat(),
        avgSkinTemperatureCelsius = 35.6f + rnd.nextFloat() * 0.9f,
        sourceApp = "com.samsung.android.honeyboard"
    )

    private suspend fun audioEventOf(
        sleepRepo: SleepRepository,
        sessionId: Long,
        startMillis: Long,
        sleepDuration: Int,
        type: AudioEventType,
        rnd: Random
    ) {
        var ts = startMillis + rnd.nextInt(sleepDuration) * 60_000L
        while (ts < startMillis + sleepDuration * 60_000L) {
            sleepRepo.insertAudioEvent(
                AudioEvent(
                    sessionId = sessionId,
                    timestampMillis = ts,
                    durationSeconds = 3 + rnd.nextInt(8),
                    type = type,
                    intensityDecibels = 40 + rnd.nextInt(25)
                )
            )
            ts += 60_000L * (25 + rnd.nextInt(45))
        }
    }
}
