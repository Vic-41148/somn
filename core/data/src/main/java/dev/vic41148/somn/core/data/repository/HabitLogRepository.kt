package dev.vic41148.somn.core.data.repository

import dev.vic41148.somn.core.data.database.dao.HabitLogDao
import dev.vic41148.somn.core.data.database.entity.HabitLogEntity
import dev.vic41148.somn.core.domain.model.CaffeineSource
import dev.vic41148.somn.core.domain.model.ExerciseIntensity
import dev.vic41148.somn.core.domain.model.ExerciseType
import dev.vic41148.somn.core.domain.model.HabitEntry
import dev.vic41148.somn.core.domain.model.HabitLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HabitLogRepository @Inject constructor(
    private val dao: HabitLogDao
) {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    // ---- Queries ----

    fun getLogsForDate(date: LocalDate): Flow<List<HabitLog>> =
        dao.getLogsForDate(date.format(dateFormatter)).map { entities ->
            entities.mapNotNull { it.toDomain() }
        }

    fun getLogsInRange(from: LocalDate, to: LocalDate): Flow<List<HabitLog>> =
        dao.getLogsInRange(
            from.format(dateFormatter),
            to.format(dateFormatter)
        ).map { entities ->
            entities.mapNotNull { it.toDomain() }
        }

    fun getAllLogs(): Flow<List<HabitLog>> {
        val earliest = LocalDate.now().minusYears(2)
        return getLogsInRange(earliest, LocalDate.now())
    }

    fun getMedicationLogs(limit: Int = 30): Flow<List<HabitLog>> =
        dao.getRecentLogsByType("MEDICATION", limit).map { entities ->
            entities.mapNotNull { it.toDomain() }
        }

    // ---- Mutations ----

    suspend fun log(entry: HabitEntry, date: LocalDate, notes: String = ""): Long =
        dao.insert(entry.toEntity(date, notes))

    suspend fun delete(log: HabitLog) =
        dao.deleteById(log.id)

    // ---- Mappers ----

    private fun HabitEntry.toEntity(date: LocalDate, notes: String): HabitLogEntity {
        val dateStr = date.format(dateFormatter)
        return when (this) {
            is HabitEntry.Caffeine -> HabitLogEntity(
                date = dateStr,
                entryType = "CAFFEINE",
                caffeineMg = mg,
                caffeineSource = source.name,
                timeOfDayHour = timeOfDay.hour,
                timeOfDayMinute = timeOfDay.minute,
                notes = notes
            )
            is HabitEntry.Alcohol -> HabitLogEntity(
                date = dateStr,
                entryType = "ALCOHOL",
                alcoholUnits = units,
                timeOfDayHour = timeOfDay.hour,
                timeOfDayMinute = timeOfDay.minute,
                notes = notes
            )
            is HabitEntry.Exercise -> HabitLogEntity(
                date = dateStr,
                entryType = "EXERCISE",
                exerciseType = type.name,
                exerciseDurationMinutes = durationMinutes,
                exerciseIntensity = intensity.name,
                timeOfDayHour = timeOfDay.hour,
                timeOfDayMinute = timeOfDay.minute,
                notes = notes
            )
            is HabitEntry.Stress -> HabitLogEntity(
                date = dateStr,
                entryType = "STRESS",
                stressLevel = level,
                notes = notes
            )
            is HabitEntry.Medication -> HabitLogEntity(
                date = dateStr,
                entryType = "MEDICATION",
                medicationName = name,
                medicationDose = dose,
                medicationIsStimulant = isStimulant,
                timeOfDayHour = timeOfDay.hour,
                timeOfDayMinute = timeOfDay.minute,
                notes = notes
            )
        }
    }

    private fun HabitLogEntity.toDomain(): HabitLog? {
        val date = try {
            LocalDate.parse(date, dateFormatter)
        } catch (e: Exception) {
            return null
        }

        val entry: HabitEntry = when (entryType) {
            "CAFFEINE" -> HabitEntry.Caffeine(
                mg = caffeineMg ?: return null,
                timeOfDay = timeOf(timeOfDayHour, timeOfDayMinute),
                source = caffeineSource?.let {
                    runCatching { CaffeineSource.valueOf(it) }.getOrDefault(CaffeineSource.COFFEE)
                } ?: CaffeineSource.COFFEE
            )
            "ALCOHOL" -> HabitEntry.Alcohol(
                units = alcoholUnits ?: return null,
                timeOfDay = timeOf(timeOfDayHour, timeOfDayMinute)
            )
            "EXERCISE" -> HabitEntry.Exercise(
                type = exerciseType?.let {
                    runCatching { ExerciseType.valueOf(it) }.getOrDefault(ExerciseType.OTHER)
                } ?: ExerciseType.OTHER,
                durationMinutes = exerciseDurationMinutes ?: return null,
                intensity = exerciseIntensity?.let {
                    runCatching { ExerciseIntensity.valueOf(it) }.getOrDefault(ExerciseIntensity.MODERATE)
                } ?: ExerciseIntensity.MODERATE,
                timeOfDay = timeOf(timeOfDayHour, timeOfDayMinute)
            )
            "STRESS" -> HabitEntry.Stress(
                level = stressLevel ?: return null
            )
            "MEDICATION" -> HabitEntry.Medication(
                name = medicationName ?: return null,
                dose = medicationDose ?: "",
                timeOfDay = timeOf(timeOfDayHour, timeOfDayMinute),
                isStimulant = medicationIsStimulant ?: false
            )
            else -> return null
        }

        return HabitLog(id = id, date = date, entry = entry, notes = notes)
    }

    private fun timeOf(hour: Int?, minute: Int?): LocalTime =
        if (hour != null && minute != null) LocalTime.of(hour, minute)
        else LocalTime.NOON
}
