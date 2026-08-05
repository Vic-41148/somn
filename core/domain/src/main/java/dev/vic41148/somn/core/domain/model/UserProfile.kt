package dev.vic41148.somn.core.domain.model

import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

/**
 * Domain model representing the user's biological profile.
 * Singleton — one profile per device installation.
 *
 * This is the foundation for age-calibrated scoring, cycle-adjusted scoring,
 * neurodivergent modes, and every personalisation feature in the app.
 */
data class UserProfile(
    val id: Long = 1,
    val dateOfBirth: LocalDate? = null,
    val biologicalSex: BiologicalSex = BiologicalSex.NOT_SPECIFIED,
    val lifeStage: LifeStage = LifeStage.DEFAULT,
    val chronotype: Chronotype = Chronotype.UNKNOWN,
    val chronotypeMeqScore: Int? = null,
    val neurodivergentProfile: NeurodivergentProfile = NeurodivergentProfile(),
    val targetSleepHours: Float = 8.0f,
    val pregnancyTrimester: Int? = null,
    val pregnancyDueDate: LocalDate? = null,
    val cycleLength: Int = 28,
    val lastPeriodStartDate: LocalDate? = null,
    val timezoneId: String = ZoneId.systemDefault().id,
    val onboardingCompleted: Boolean = false
) {
    /** Calculate current age from date of birth. */
    val age: Int?
        get() = dateOfBirth?.let {
            Period.between(it, LocalDate.now()).years
        }

    /** Get age-appropriate target sleep hours if user hasn't customised. */
    val recommendedSleepHours: Float
        get() = when (val currentAge = age) {
            null -> 8.0f
            in 0..2 -> 14.0f
            in 3..5 -> 11.0f
            in 6..12 -> 10.0f
            in 13..18 -> 9.0f
            in 19..64 -> 8.0f
            else -> 7.5f
        }

    /** Get age-calibrated deep sleep target percentage. */
    val deepSleepTargetPercent: Float
        get() = when (val currentAge = age) {
            null -> 20.0f
            in 0..17 -> 27.5f   // SWS peaks in childhood/adolescence
            in 18..35 -> 22.5f
            in 36..55 -> 17.5f  // SWS begins declining mid-30s
            in 56..75 -> 12.5f  // Substantially reduced
            else -> 10.0f       // Late life
        }

    /** Whether cycle tracking features should be visible. */
    val showCycleFeatures: Boolean
        get() = biologicalSex == BiologicalSex.FEMALE &&
            lifeStage in listOf(LifeStage.CYCLING, LifeStage.DEFAULT)

    /** Whether pregnancy features should be visible. */
    val showPregnancyFeatures: Boolean
        get() = lifeStage == LifeStage.PREGNANT

    /** Whether menopause features should be visible. */
    val showMenopauseFeatures: Boolean
        get() = lifeStage in listOf(
            LifeStage.PERIMENOPAUSE,
            LifeStage.MENOPAUSE,
            LifeStage.POST_MENOPAUSE
        )
}

enum class BiologicalSex(val displayName: String) {
    MALE("Male"),
    FEMALE("Female"),
    NOT_SPECIFIED("Prefer not to say")
}

/**
 * Life stage determines which biological adjustments apply to scoring.
 * Research doc §2.1–2.5.
 *
 * The age-band stages (Adolescent / Young Adult / Older Adult) were pruned: nothing consumed
 * them, and their intent is already covered by the age-derived `recommendedSleepHours`,
 * `deepSleepTargetPercent`, and the age-based score adjustments in CalculateSleepScoreUseCase
 * (adolescent duration bonus, 55+ deep-sleep band, wake-expectation by age). Add a consumer
 * before ever re-adding an age stage — a selectable option with no downstream effect is worse
 * than no option.
 */
enum class LifeStage(val displayName: String, val description: String) {
    DEFAULT("Default", "Standard sleep tracking"),
    CYCLING("Menstrual Cycling", "Active menstrual cycle tracking with phase-aware scoring"),
    PREGNANT("Pregnant", "Trimester-aware scoring and safe sleep guidance"),
    POSTPARTUM("Postpartum", "Fragmentation-focused tracking with PPD risk monitoring"),
    PERIMENOPAUSE("Perimenopause", "Vasomotor event detection and REM disruption alerts"),
    MENOPAUSE("Menopause", "Temperature spike flagging and adjusted scoring"),
    POST_MENOPAUSE("Post-Menopause", "Age-calibrated scoring with hormonal context")
}

/**
 * Chronotype determined by the rMEQ questionnaire (5-item, score 4-25) or detected from
 * sleep data. Research doc §2.11: chronotype is neurologically determined, encoded in
 * circadian genes.
 */
enum class Chronotype(val displayName: String, val meqRange: IntRange?) {
    UNKNOWN("Not assessed", null),
    DEFINITE_MORNING("Definite Morning", 21..25),
    MODERATE_MORNING("Moderate Morning", 17..20),
    INTERMEDIATE("Intermediate", 13..16),
    MODERATE_EVENING("Moderate Evening", 9..12),
    DEFINITE_EVENING("Definite Evening", 4..8);

    companion object {
        /**
         * rMEQ (Adan & Almirall, 1991) is a 5-item questionnaire scoring 4-25, NOT the 19-item
         * Horne & Östberg MEQ's 16-86 range this used to use. The old bands (59-86/42-58/...)
         * were structurally unreachable from a 5-item quiz sum, which silently pushed most
         * users toward evening-type. Rescaled onto the real rMEQ range (4-25), keeping the
         * existing 5-way typology since it's load-bearing elsewhere (ADHD adjustment, circadian
         * UI). No published rMEQ study defines 5 bands specifically — the validated cutoffs
         * are the 3-band 4-11/12-17/18-25 split — so this is an even-width approximation over
         * that range, not a literature value.
         */
        fun fromMeqScore(score: Int): Chronotype = when (score) {
            in 21..25 -> DEFINITE_MORNING
            in 17..20 -> MODERATE_MORNING
            in 13..16 -> INTERMEDIATE
            in 9..12  -> MODERATE_EVENING
            in 4..8   -> DEFINITE_EVENING
            else      -> UNKNOWN
        }
    }
}

/**
 * Neurodivergent profile flags.
 * Research doc §2.6–2.7: ADHD and ASD have distinct, measurable impacts on sleep architecture.
 */
data class NeurodivergentProfile(
    val adhdMode: Boolean = false,
    val asdMode: Boolean = false,
    val medicationTracking: Boolean = false
)
