package dev.vic41148.somn.core.domain.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * A single habit event logged by the user on a given date.
 * Wraps one of the sealed [HabitEntry] subtypes.
 */
data class HabitLog(
    val id: Long = 0,
    val date: LocalDate,
    val entry: HabitEntry,
    val notes: String = ""
)

/**
 * Sealed hierarchy of trackable lifestyle habits.
 * Each subtype maps to a distinct log entry in the database.
 *
 * Research basis:
 * §3.1  Caffeine: half-life ~5-6 h; cut-off recommendations ≥6 h before bed.
 * §3.2  Alcohol: fragments REM sleep; reduces efficiency even at low doses.
 * §3.3  Exercise: moderate aerobic → +8 min deep sleep; timing matters.
 * §3.4  Stress: cortisol elevation → prolonged sleep onset + wakes.
 * §3.5  Stimulant medication: ADHD medication timing critical for sleep onset.
 */
sealed class HabitEntry {

    /** Caffeine consumption event. */
    data class Caffeine(
        val mg: Int,                         // caffeine content in milligrams
        val timeOfDay: LocalTime,
        val source: CaffeineSource = CaffeineSource.COFFEE
    ) : HabitEntry()

    /** Alcohol consumption event. */
    data class Alcohol(
        val units: Float,                    // 1 unit ≈ 10 ml pure ethanol (UK standard)
        val timeOfDay: LocalTime
    ) : HabitEntry()

    /** Exercise session. */
    data class Exercise(
        val type: ExerciseType,
        val durationMinutes: Int,
        val intensity: ExerciseIntensity = ExerciseIntensity.MODERATE,
        val timeOfDay: LocalTime
    ) : HabitEntry()

    /** Subjective stress level at end of day. */
    data class Stress(
        val level: Int   // 1 (calm) → 5 (very stressed)
    ) : HabitEntry() {
        init {
            require(level in 1..5) { "Stress level must be 1-5, got $level" }
        }
    }

    /** Medication log — primarily for ADHD stimulant timing. */
    data class Medication(
        val name: String,
        val dose: String,
        val timeOfDay: LocalTime,
        val isStimulant: Boolean
    ) : HabitEntry()
}

/** Canonical caffeine source with default mg values. */
enum class CaffeineSource(val displayName: String, val defaultMg: Int) {
    COFFEE("Coffee", 95),
    ESPRESSO("Espresso", 65),
    TEA("Tea", 47),
    ENERGY_DRINK("Energy drink", 160),
    SODA("Cola/soda", 35),
    PRE_WORKOUT("Pre-workout", 200),
    OTHER("Other", 80)
}

/** Exercise type for correlation analysis. */
enum class ExerciseType(val displayName: String) {
    WALKING("Walking"),
    RUNNING("Running"),
    CYCLING("Cycling"),
    STRENGTH("Strength training"),
    YOGA("Yoga / stretching"),
    SWIMMING("Swimming"),
    HIIT("HIIT"),
    OTHER("Other")
}

/** Exercise intensity level. */
enum class ExerciseIntensity(val displayName: String) {
    LIGHT("Light"),
    MODERATE("Moderate"),
    VIGOROUS("Vigorous")
}
