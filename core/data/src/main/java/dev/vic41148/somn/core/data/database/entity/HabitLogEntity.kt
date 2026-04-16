package dev.vic41148.somn.core.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for a single habit log entry.
 *
 * Uses a flat table with a [entryType] discriminator column.
 * Each subtype occupies its own nullable columns; unrelated columns are null.
 * This avoids the complexity of polymorphic relationships while keeping a single DAO.
 *
 * [date] is stored as ISO-8601 string (yyyy-MM-dd) for simple range queries.
 * [timeOfDayHour] + [timeOfDayMinute] reconstruct a LocalTime on the domain side.
 */
@Entity(tableName = "habit_logs")
data class HabitLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** ISO-8601 date string (yyyy-MM-dd). */
    val date: String,

    /** Discriminator: "CAFFEINE" | "ALCOHOL" | "EXERCISE" | "STRESS" | "MEDICATION" */
    val entryType: String,

    // ---- Caffeine ----
    val caffeineMg: Int? = null,
    val caffeineSource: String? = null,          // CaffeineSource.name

    // ---- Alcohol ----
    val alcoholUnits: Float? = null,

    // ---- Exercise ----
    val exerciseType: String? = null,            // ExerciseType.name
    val exerciseDurationMinutes: Int? = null,
    val exerciseIntensity: String? = null,       // ExerciseIntensity.name

    // ---- Stress ----
    val stressLevel: Int? = null,                // 1-5

    // ---- Medication ----
    val medicationName: String? = null,
    val medicationDose: String? = null,
    val medicationIsStimulant: Boolean? = null,

    // ---- Shared (time + notes) ----
    /** Hour component of the event time (null for Stress which has no time). */
    val timeOfDayHour: Int? = null,
    /** Minute component of the event time. */
    val timeOfDayMinute: Int? = null,

    val notes: String = ""
)
