package dev.vic41148.somn.core.domain.model

import java.time.LocalTime

/**
 * Result of chronotype analysis, combining the onboarding rMEQ questionnaire result with
 * data-driven detection from alarm-free sleep sessions.
 *
 * Research basis §2.11: Chronotype is neurologically determined and encoded in circadian genes
 * (Washington University / PNAS 2025). Social jet lag — the discrepancy between biological
 * and schedule-forced sleep timing — is an independent cardiovascular risk factor.
 */
data class ChronotypeAssessment(
    /** Chronotype classified from onboarding rMEQ questionnaire. Always present. */
    val questionnaireBased: Chronotype,
    /** Raw MEQ score from onboarding quiz, or null if not completed. */
    val questionnaireScore: Int?,
    /**
     * Chronotype derived from actual alarm-free sleep data.
     * Null if fewer than [MIN_ALARM_FREE_NIGHTS] qualifying nights exist.
     */
    val dataDriven: Chronotype?,
    /**
     * The calculated natural sleep midpoint from alarm-free sessions.
     * Null if insufficient data.
     */
    val dataDrivenMidpoint: LocalTime?,
    /** Number of alarm-free nights used in the data-driven calculation. */
    val alarmFreeNightsUsed: Int,
    /** How confident the data-driven result is. LOW if few nights. */
    val confidence: AssessmentConfidence,
    /** Whether the questionnaire and data-driven results agree. */
    val agreementStatus: AgreementStatus
) {
    companion object {
        const val MIN_ALARM_FREE_NIGHTS = 14
    }
}

/**
 * Confidence tier for data-driven chronotype assessment.
 * Based on number of qualifying alarm-free sessions.
 */
enum class AssessmentConfidence(val displayName: String, val minNights: Int) {
    /** Fewer than 14 alarm-free nights — data-driven result unavailable. */
    INSUFFICIENT("Insufficient data", 0),
    /** 14–29 alarm-free nights. */
    LOW("Low", 14),
    /** 30–59 alarm-free nights. */
    MODERATE("Moderate", 30),
    /** 60+ alarm-free nights. */
    HIGH("High", 60);

    companion object {
        fun from(nights: Int): AssessmentConfidence = when {
            nights < LOW.minNights      -> INSUFFICIENT
            nights < MODERATE.minNights -> LOW
            nights < HIGH.minNights     -> MODERATE
            else                        -> HIGH
        }
    }
}

/**
 * Whether the rMEQ questionnaire result and data-driven detection agree.
 * Disagreement may indicate social jet lag or a need to retake the questionnaire.
 */
enum class AgreementStatus(val displayName: String) {
    /** Both methods classify into the same chronotype category. */
    AGREE("Results agree"),
    /**
     * Methods disagree. Possible social jet lag — natural timing may differ from lived schedule.
     */
    DISAGREE("Results differ — possible social jet lag"),
    /** Not enough data to compute data-driven result. */
    INSUFFICIENT_DATA("Not enough data yet")
}
