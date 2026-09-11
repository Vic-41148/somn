package dev.vic41148.somn.app.debug

import dev.vic41148.somn.core.data.audio.AudioClipStore
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
        tagRepo: TagRepository,
        clipStore: AudioClipStore,
        filesDir: java.io.File
    ) {
        val rnd = Random(424242L)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()

        // Weekend / commute tag for a bit of cross-referencing colour.
        val weekendTag = tagRepo.createTag("Weekend", "Lifestyle", 0xFF6200EE, "weekend")

        // One night per morning for the last 30 days (day 0 = today's wake-up,
        // newest first), scores riding a slow wave so ranges and trends read.
        for (dayAgo in 29 downTo 0) {
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

            // Score reflects a varied month: slow wave plus noise, one dip.
            val durationScore = ((sleepDuration - 300) / 2f).coerceIn(40f, 100f).toInt()
            val wave = (8 * kotlin.math.sin(dayAgo / 4.0)).toInt()
            val score = (72 + wave + rnd.nextInt(13) - 6 - (if (dayAgo == 10) 12 else 0))
                .coerceIn(50, 94)

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

            // Audio events on odd nights, playable clips only for the recent
            // week, older nights keep bare events the way retention leaves them.
            if (dayAgo % 2 == 1) {
                val writeClips = dayAgo < 7
                audioEventOf(sleepRepo, clipStore, filesDir, sessionId, startMillis, sleepDuration, AudioEventType.SNORE, rnd, writeClips)
                if (rnd.nextBoolean()) {
                    audioEventOf(sleepRepo, clipStore, filesDir, sessionId, startMillis, sleepDuration, AudioEventType.TALK, rnd, writeClips)
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
        clipStore: AudioClipStore,
        filesDir: java.io.File,
        sessionId: Long,
        startMillis: Long,
        sleepDuration: Int,
        type: AudioEventType,
        rnd: Random,
        writeClips: Boolean
    ) {
        var ts = startMillis + rnd.nextInt(sleepDuration) * 60_000L
        val dirName = when (type) {
            AudioEventType.TALK -> "sleep_talk"
            AudioEventType.SNORE -> "sleep_snore"
            AudioEventType.COUGH -> "sleep_cough"
            else -> "sleep_events"
        }
        val dir = java.io.File(filesDir, dirName)
        while (ts < startMillis + sleepDuration * 60_000L) {
            val durationSeconds = 3 + rnd.nextInt(8)
            // Real playable clip through the production path (sealed .enc on
            // disk, decrypted at play time): loud synthesized stand-ins, one
            // voice per event type, so playback is verifiable by ear.
            val clipPath = if (writeClips) {
                clipStore.writeClip(
                    dir,
                    "${type.name.lowercase()}_${sessionId}_${ts}.wav",
                    encodeWav(synthClip(type, durationSeconds, rnd))
                ).absolutePath
            } else null
            sleepRepo.insertAudioEvent(
                AudioEvent(
                    sessionId = sessionId,
                    timestampMillis = ts,
                    durationSeconds = durationSeconds,
                    type = type,
                    intensityDecibels = 40 + rnd.nextInt(25),
                    clipPath = clipPath
                )
            )
            ts += 60_000L * (25 + rnd.nextInt(45))
        }
    }

    private const val SEED_SAMPLE_RATE = 16000

    /** Loud, obviously-audible stand-in per event type at 16 kHz mono. */
    private fun synthClip(type: AudioEventType, durationSeconds: Int, rnd: Random): ShortArray {
        val n = durationSeconds * SEED_SAMPLE_RATE
        val out = ShortArray(n)
        when (type) {
            // Chattering syllables: 180 Hz wobble with gaps.
            AudioEventType.TALK -> {
                var i = 0
                while (i < n) {
                    val sylLen = (0.22 * SEED_SAMPLE_RATE).toInt()
                    for (j in 0 until sylLen) {
                        if (i + j >= n) break
                        val t = (i + j).toDouble() / SEED_SAMPLE_RATE
                        val env = kotlin.math.sin(kotlin.math.PI * j / sylLen)
                        val s = kotlin.math.sin(2 * kotlin.math.PI * 180 * t) * 0.7 +
                            kotlin.math.sin(2 * kotlin.math.PI * 360 * t) * 0.3
                        out[i + j] = (s * env * 22000).toInt().toShort()
                    }
                    i += sylLen + (0.15 * SEED_SAMPLE_RATE).toInt()
                }
            }
            // Low rumble swells.
            AudioEventType.SNORE -> {
                for (i in 0 until n) {
                    val t = i.toDouble() / SEED_SAMPLE_RATE
                    val swell = 0.5 + 0.5 * kotlin.math.sin(2 * kotlin.math.PI * 0.4 * t)
                    val s = kotlin.math.sin(2 * kotlin.math.PI * 70 * t) * 0.8 +
                        (rnd.nextFloat() - 0.5f) * 0.4
                    out[i] = (s * swell * 22000).toInt()
                        .coerceIn(-32768, 32767).toShort()
                }
            }
            // Sharp noise bursts.
            else -> {
                var i = 0
                while (i < n) {
                    val burstLen = (0.3 * SEED_SAMPLE_RATE).toInt()
                    for (j in 0 until burstLen) {
                        if (i + j >= n) break
                        val env = 1.0 - j.toDouble() / burstLen
                        out[i + j] = ((rnd.nextFloat() - 0.5f) * 2 * env * 24000).toInt()
                            .coerceIn(-32768, 32767).toShort()
                    }
                    i += burstLen + (0.25 * SEED_SAMPLE_RATE).toInt()
                }
            }
        }
        return out
    }

    /** 44-byte PCM header + 16-bit mono samples, same layout as the tracker. */
    private fun encodeWav(data: ShortArray): ByteArray {
        val totalDataLen = data.size * 2
        val totalAudioLen = totalDataLen + 36
        val out = java.io.ByteArrayOutputStream(44 + totalDataLen)
        val h = ByteArray(44)
        h[0] = 'R'.code.toByte(); h[1] = 'I'.code.toByte()
        h[2] = 'F'.code.toByte(); h[3] = 'F'.code.toByte()
        h[4] = (totalAudioLen and 0xff).toByte()
        h[5] = ((totalAudioLen shr 8) and 0xff).toByte()
        h[6] = ((totalAudioLen shr 16) and 0xff).toByte()
        h[7] = ((totalAudioLen shr 24) and 0xff).toByte()
        h[8] = 'W'.code.toByte(); h[9] = 'A'.code.toByte()
        h[10] = 'V'.code.toByte(); h[11] = 'E'.code.toByte()
        h[12] = 'f'.code.toByte(); h[13] = 'm'.code.toByte()
        h[14] = 't'.code.toByte(); h[15] = ' '.code.toByte()
        h[16] = 16; h[17] = 0; h[18] = 0; h[19] = 0; h[20] = 1; h[21] = 0
        h[22] = 1; h[23] = 0
        h[24] = (SEED_SAMPLE_RATE and 0xff).toByte()
        h[25] = ((SEED_SAMPLE_RATE shr 8) and 0xff).toByte()
        h[26] = ((SEED_SAMPLE_RATE shr 16) and 0xff).toByte()
        h[27] = ((SEED_SAMPLE_RATE shr 24) and 0xff).toByte()
        val byteRate = 16 * SEED_SAMPLE_RATE / 8
        h[28] = (byteRate and 0xff).toByte()
        h[29] = ((byteRate shr 8) and 0xff).toByte()
        h[30] = ((byteRate shr 16) and 0xff).toByte()
        h[31] = ((byteRate shr 24) and 0xff).toByte()
        h[32] = 2; h[33] = 0; h[34] = 16; h[35] = 0
        h[36] = 'd'.code.toByte(); h[37] = 'a'.code.toByte()
        h[38] = 't'.code.toByte(); h[39] = 'a'.code.toByte()
        h[40] = (totalDataLen and 0xff).toByte()
        h[41] = ((totalDataLen shr 8) and 0xff).toByte()
        h[42] = ((totalDataLen shr 16) and 0xff).toByte()
        h[43] = ((totalDataLen shr 24) and 0xff).toByte()
        out.write(h)
        val bb = java.nio.ByteBuffer.allocate(data.size * 2)
        bb.order(java.nio.ByteOrder.LITTLE_ENDIAN)
        bb.asShortBuffer().put(data)
        out.write(bb.array())
        return out.toByteArray()
    }
}
