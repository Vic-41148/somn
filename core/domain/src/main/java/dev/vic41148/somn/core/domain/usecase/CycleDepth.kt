package dev.vic41148.somn.core.domain.usecase

import dev.vic41148.somn.core.domain.model.MenstrualCyclePhase

/**
 * R5 Cycle depth: temperature-informed phases, luteal coaching, menopause check-in.
 *
 * Oura-validated mechanics, phone-only and on-device: calendar phase math stays the
 * fallback everywhere, skin temperature (when Health Connect provides it) refines
 * boundaries, and the questionnaire is pure UI. No accuracy percentages anywhere —
 * every refined number names its source and sample.
 */

/** Where the current phase call came from — shown, never hidden. */
enum class PhaseSource { CALENDAR, CALENDAR_AND_TEMP }

data class PhaseRefinement(
    val phase: MenstrualCyclePhase,
    val source: PhaseSource,
    /** Nights of temperature history behind the call (0 = calendar only). */
    val tempNights: Int,
    val note: String
)

/** Post-ovulation temps run ~0.2–0.5°C hotter; this is the shift that moves a phase. */
const val TEMP_SHIFT_THRESHOLD_C = 0.3f
const val MIN_TEMP_NIGHTS = 6

/**
 * Refines [calendarPhase] with nightly skin temperatures (oldest→newest, nulls
 * allowed for missing nights). A sustained rise with a pre-ovulation calendar phase
 * means ovulation likely passed — the phase moves to luteal. Everything else stands.
 */
fun refinePhase(
    calendarPhase: MenstrualCyclePhase?,
    tempsCelsius: List<Float?>
): PhaseRefinement {
    if (calendarPhase == null) {
        return PhaseRefinement(
            phase = MenstrualCyclePhase.FOLLICULAR,
            source = PhaseSource.CALENDAR,
            tempNights = 0,
            note = "No cycle data — enable cycle tracking for phase-aware coaching."
        )
    }
    val temps = tempsCelsius.filterNotNull()
    if (temps.size < MIN_TEMP_NIGHTS) {
        return PhaseRefinement(
            phase = calendarPhase,
            source = PhaseSource.CALENDAR,
            tempNights = temps.size,
            note = "Calendar only — ${temps.size}/$MIN_TEMP_NIGHTS temperature nights " +
                "so far for refinement."
        )
    }
    val half = temps.size / 2
    val baseline = temps.take(half).average()
    val recent = temps.takeLast(half).average()
    val rise = recent - baseline
    if (rise >= TEMP_SHIFT_THRESHOLD_C &&
        (calendarPhase == MenstrualCyclePhase.FOLLICULAR ||
            calendarPhase == MenstrualCyclePhase.OVULATION)
    ) {
        return PhaseRefinement(
            phase = MenstrualCyclePhase.LUTEAL,
            source = PhaseSource.CALENDAR_AND_TEMP,
            tempNights = temps.size,
            note = "Temperature rose ${"%.1f".format(rise)}°C — ovulation likely " +
                "passed, phase moved to luteal (${temps.size} nights)."
        )
    }
    return PhaseRefinement(
        phase = calendarPhase,
        source = PhaseSource.CALENDAR_AND_TEMP,
        tempNights = temps.size,
        note = "Calendar plus temperature agree (${temps.size} nights)."
    )
}

/** Luteal-phase sleep coaching for the Outlook sentence; null outside luteal window. */
fun lutealCoaching(phase: MenstrualCyclePhase?): String? = when (phase) {
    MenstrualCyclePhase.LUTEAL ->
        "Luteal phase: efficiency typically dips while temperature runs high — " +
            "plan an extra 20 minutes in bed this week."
    MenstrualCyclePhase.PREMENSTRUAL ->
        "Pre-menstruation: the hardest sleep nights of the cycle are hormonal, " +
            "not a regression — plan an extra 20 minutes in bed."
    else -> null
}

/** Extra bedtime minutes the debt plan widens during the luteal window. */
const val LUTEAL_EXTRA_MINUTES = 20

// ---- Menopause check-in (Oura Menopause Impact Scale, Somn-shortened) ----

data class MenoQuestion(val id: String, val text: String)

/** 10 questions, 0–3 each (Not at all → A lot), sleep-weighted first. Pure UI. */
val MENOPAUSE_QUESTIONS = listOf(
    MenoQuestion("night_sweats", "Night sweats that wake you"),
    MenoQuestion("sleep_through", "Trouble staying asleep through the night"),
    MenoQuestion("hot_flashes", "Hot flashes during the day"),
    MenoQuestion("fatigue", "Daytime fatigue despite time in bed"),
    MenoQuestion("mood", "Mood swings or irritability"),
    MenoQuestion("anxiety", "Anxiety or racing thoughts at bedtime"),
    MenoQuestion("memory", "Forgetfulness or brain fog"),
    MenoQuestion("joints", "Joint aches or stiffness"),
    MenoQuestion("palpitations", "Heart palpitations"),
    MenoQuestion("headaches", "Headaches or migraines")
)

enum class MenoBand(val displayName: String, val summary: String) {
    MINIMAL(
        "Minimal impact",
        "Low symptom load right now. Sleep dips are more likely habit or " +
            "schedule than hormonal — the Patterns screen will say which."
    ),
    MODERATE(
        "Moderate impact",
        "Enough symptoms to move sleep. Vasomotor nights (sweats, flashes) " +
            "fragment sleep even when total hours look fine — worth tracking " +
            "against your efficiency trend."
    ),
    SIGNIFICANT(
        "Significant impact",
        "High symptom load. This questionnaire is wellness information, not a " +
            "diagnosis — but this level is worth mentioning to a doctor, " +
            "because effective treatments exist."
    );

    companion object {
        /** 0–30 scale; bands split the range in thirds. */
        fun from(total: Int): MenoBand = when {
            total < 10 -> MINIMAL
            total <= 20 -> MODERATE
            else -> SIGNIFICANT
        }
    }
}

fun scoreMenopause(answers: List<Int>): MenoBand {
    require(answers.size == MENOPAUSE_QUESTIONS.size) {
        "Need ${MENOPAUSE_QUESTIONS.size} answers, got ${answers.size}"
    }
    return MenoBand.from(answers.sum())
}

// ---- Pregnancy / postpartum trend context ----

/** Banner copy for Trends when the calendar overlay doesn't apply. Null otherwise. */
fun lifeStageBanner(
    lifeStageName: String,
    pregnancyTrimester: Int?
): String? = when (lifeStageName) {
    "PREGNANT" -> {
        val tri = if (pregnancyTrimester != null) " · trimester $pregnancyTrimester" else ""
        "Pregnancy$tri — fragmentation and vivid dreams are normal; " +
            "your score already adjusts for this stage."
    }
    "POSTPARTUM" ->
        "Newborn phase — consolidated sleep is rare and that is expected; " +
            "track what you can, ignore the streak."
    else -> null
}

/** Absolute point gap helper for tests and UI deltas. */
fun tempRise(tempsCelsius: List<Float>): Float {
    if (tempsCelsius.size < 2) return 0f
    val half = tempsCelsius.size / 2
    return (tempsCelsius.takeLast(half).average() - tempsCelsius.take(half).average()).toFloat()
}
