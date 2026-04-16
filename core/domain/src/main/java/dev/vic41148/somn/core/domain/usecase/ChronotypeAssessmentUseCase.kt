package dev.vic41148.somn.core.domain.usecase

import dev.vic41148.somn.core.domain.model.AssessmentConfidence
import dev.vic41148.somn.core.domain.model.ChronotypeAssessment
import dev.vic41148.somn.core.domain.model.Chronotype
import dev.vic41148.somn.core.domain.model.AgreementStatus
import dev.vic41148.somn.core.domain.model.SleepSession
import dev.vic41148.somn.core.domain.model.UserProfile
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Assesses the user's chronotype by combining:
 *   1. The rMEQ questionnaire result stored in [UserProfile] (from onboarding)
 *   2. Data-driven detection from alarm-free sleep sessions
 *
 * Data-driven method (MCTQ approach):
 *   - Only sessions where [SleepSession.alarmUsed] == false qualify
 *   - Natural sleep midpoint = average of ((startTime + endTime) / 2) across qualifying nights
 *   - Chronotype classified from midpoint time
 *
 * Requires [ChronotypeAssessment.MIN_ALARM_FREE_NIGHTS] alarm-free nights for a
 * data-driven result. Fewer nights returns null for [ChronotypeAssessment.dataDriven].
 *
 * Research basis §2.11: Chronotype is neurologically determined (Washington University / PNAS 2025).
 * The questionnaire provides an immediate baseline; data-driven detection adds longitudinal validation.
 */
class ChronotypeAssessmentUseCase {

    /**
     * @param profile     The user's stored profile (contains rMEQ chronotype from onboarding).
     * @param sessions    All completed sleep sessions.
     * @return A [ChronotypeAssessment] with both questionnaire and data-driven results.
     */
    fun assess(profile: UserProfile, sessions: List<SleepSession>): ChronotypeAssessment {
        val questionnaireBased = profile.chronotype
        val questionnaireScore = profile.chronotypeMeqScore

        // Filter to alarm-free completed sessions only
        val alarmFreeSessions = sessions.filter { it.isCompleted && !it.alarmUsed }
        val nightCount = alarmFreeSessions.size

        // Need at least MIN_ALARM_FREE_NIGHTS for a data-driven result
        if (nightCount < ChronotypeAssessment.MIN_ALARM_FREE_NIGHTS) {
            return ChronotypeAssessment(
                questionnaireBased   = questionnaireBased,
                questionnaireScore   = questionnaireScore,
                dataDriven           = null,
                dataDrivenMidpoint   = null,
                alarmFreeNightsUsed  = nightCount,
                confidence           = AssessmentConfidence.INSUFFICIENT,
                agreementStatus      = AgreementStatus.INSUFFICIENT_DATA
            )
        }

        // Compute natural sleep midpoints for each qualifying session
        val midpointMinutes: List<Int> = alarmFreeSessions.mapNotNull { session ->
            if (session.endTimeMillis == 0L) return@mapNotNull null
            val midMillis = (session.startTimeMillis + session.endTimeMillis) / 2
            instantToLocalMinutesOfDay(midMillis, session.timezoneId)
        }

        if (midpointMinutes.isEmpty()) {
            return ChronotypeAssessment(
                questionnaireBased  = questionnaireBased,
                questionnaireScore  = questionnaireScore,
                dataDriven          = null,
                dataDrivenMidpoint  = null,
                alarmFreeNightsUsed = 0,
                confidence          = AssessmentConfidence.INSUFFICIENT,
                agreementStatus     = AgreementStatus.INSUFFICIENT_DATA
            )
        }

        // Circular average to handle midnight wrap-around correctly
        val avgMidpointMinutes = circularAverageMinutes(midpointMinutes)
        val midpointTime = LocalTime.ofSecondOfDay((avgMidpointMinutes * 60L).coerceIn(0, 86399))

        val dataDrivenChronotype = chronotypeFromMidpoint(midpointTime)
        val confidence = AssessmentConfidence.from(nightCount)
        val agreement = computeAgreement(questionnaireBased, dataDrivenChronotype)

        return ChronotypeAssessment(
            questionnaireBased  = questionnaireBased,
            questionnaireScore  = questionnaireScore,
            dataDriven          = dataDrivenChronotype,
            dataDrivenMidpoint  = midpointTime,
            alarmFreeNightsUsed = nightCount,
            confidence          = confidence,
            agreementStatus     = agreement
        )
    }

    // ---- Chronotype from midpoint ----

    /**
     * Classify chronotype from natural sleep midpoint.
     * Thresholds derived from Munich ChronoType Questionnaire (MCTQ) population data.
     *
     * Note: sleep midpoint is typically in the middle of the night — hence times like
     * 3:00–4:00 AM represent average chronotypes.
     */
    private fun chronotypeFromMidpoint(midpoint: LocalTime): Chronotype {
        val hour = midpoint.hour
        val minute = midpoint.minute
        val totalMinutes = hour * 60 + minute

        // Adjusted to minutes from midnight
        return when {
            totalMinutes < 2 * 60              -> Chronotype.DEFINITE_MORNING   // before 2:00 AM
            totalMinutes < 2 * 60 + 45         -> Chronotype.MODERATE_MORNING   // 2:00–2:45 AM
            totalMinutes < 3 * 60 + 30         -> Chronotype.INTERMEDIATE        // 2:45–3:30 AM
            totalMinutes < 4 * 60 + 30         -> Chronotype.MODERATE_EVENING   // 3:30–4:30 AM
            else                               -> Chronotype.DEFINITE_EVENING   // after 4:30 AM
        }
    }

    // ---- Agreement ----

    private fun computeAgreement(questionnaire: Chronotype, dataDriven: Chronotype): AgreementStatus {
        // Treat UNKNOWN as agreement (no questionnaire completed)
        if (questionnaire == Chronotype.UNKNOWN) return AgreementStatus.INSUFFICIENT_DATA

        // Group into morning / neutral / evening buckets for fuzzy agreement
        val morningTypes  = setOf(Chronotype.DEFINITE_MORNING, Chronotype.MODERATE_MORNING)
        val eveningTypes  = setOf(Chronotype.DEFINITE_EVENING, Chronotype.MODERATE_EVENING)

        fun bucket(c: Chronotype) = when (c) {
            in morningTypes  -> 0
            Chronotype.INTERMEDIATE -> 1
            in eveningTypes  -> 2
            else             -> -1
        }

        return if (bucket(questionnaire) == bucket(dataDriven)) AgreementStatus.AGREE
        else AgreementStatus.DISAGREE
    }

    // ---- Time math helpers ----

    /**
     * Convert an epoch-millis timestamp to minutes-of-day in the given timezone.
     * Returns minutes in range [0, 1439].
     */
    private fun instantToLocalMinutesOfDay(epochMillis: Long, timezoneId: String): Int {
        val zone = runCatching { ZoneId.of(timezoneId) }.getOrDefault(ZoneId.systemDefault())
        val localTime = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime()
        return localTime.hour * 60 + localTime.minute
    }

    /**
     * Circular average for time-of-day values (minutes) to handle midnight wrap-around.
     * Uses the unit-circle projection method: convert to radians, average sin/cos, convert back.
     */
    private fun circularAverageMinutes(minutesList: List<Int>): Int {
        if (minutesList.size == 1) return minutesList.first()
        val totalMinutesInDay = 24 * 60
        val twoPi = 2.0 * Math.PI

        var sinSum = 0.0
        var cosSum = 0.0
        for (m in minutesList) {
            val angle = twoPi * m / totalMinutesInDay
            sinSum += Math.sin(angle)
            cosSum += Math.cos(angle)
        }

        val avgAngle = Math.atan2(sinSum, cosSum)
        val normalized = ((avgAngle / twoPi) * totalMinutesInDay).toInt()
        return ((normalized % totalMinutesInDay) + totalMinutesInDay) % totalMinutesInDay
    }
}
