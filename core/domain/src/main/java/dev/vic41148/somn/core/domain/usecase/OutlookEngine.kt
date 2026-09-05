package dev.vic41148.somn.core.domain.usecase

import dev.vic41148.somn.core.domain.model.SleepDebt

/**
 * Daily Outlook sentence engine — WHOOP-Daily-Outlook voice, zero LLM. One short
 * template-built paragraph, different morning vs evening, from numbers already in the
 * DB. Pure function so the copy variants stay unit-tested.
 *
 * Copy guardrails (orthosomnia row): trend framing over nightly judgment, never a
 * verdict on the person, "perfection is not the goal" tone. Never blank — every
 * null combination falls back to a generic line rather than crashing or emptying
 * the widget.
 */
fun buildOutlook(
    readiness: ReadinessResult?,
    debt: SleepDebt?,
    /** Strongest settled correlation insight, or null when the engine has none yet. */
    correlationInsight: String?,
    /** Extra sleep minutes/night from the debt recovery plan, when one exists. */
    recoveryMinutesHint: Int? = null,
    isMorning: Boolean = true,
    /** Rest Mode: the math stays honest, the voice switches to recovery framing. */
    restMode: Boolean = false,
    /** R5 luteal coaching sentence, appended when the cycle is in its luteal window. */
    cycleCoaching: String? = null
): String {
    if (readiness == null) {
        return if (isMorning) "Track tonight and tomorrow starts with a plan."
        else "Wind down early tonight — your future morning self says thanks."
    }
    if (restMode && isMorning) {
        return "Rest Mode is on — only rest counts today. " +
            "Nights logged now won't move your streak or baselines."
    }
    val debtWord = when {
        debt == null -> null
        debt.totalDebtMinutes < 30 -> "no sleep debt"
        else -> "${formatDurationShort(debt.totalDebtMinutes)} of sleep debt (${debt.trend.displayName.lowercase()})"
    }
    return if (isMorning) {
        val lead = when (readiness.zone) {
            ReadinessZone.READY -> "You're primed today"
            ReadinessZone.STEADY -> "A steady day ahead"
            ReadinessZone.REST -> "Recovery day"
        }
        val advice = when (readiness.zone) {
            ReadinessZone.READY -> "a good day to push."
            ReadinessZone.STEADY -> "normal load is fine."
            ReadinessZone.REST -> "keep it light."
        }
        val first = buildString {
            append(lead)
            if (debtWord != null) append(" with $debtWord")
            append(" — $advice")
        }
        val second = if (correlationInsight != null) "$first $correlationInsight" else first
        return if (cycleCoaching != null) "$second $cycleCoaching" else second
    } else {
        val parts = mutableListOf<String>()
        when (readiness.zone) {
            ReadinessZone.REST -> parts.add("Today took its toll — protect tonight.")
            else -> parts.add("Close out the day well.")
        }
        if (recoveryMinutesHint != null && recoveryMinutesHint > 0) {
            parts.add("About $recoveryMinutesHint extra minutes would keep the debt plan on track.")
        } else {
            parts.add("A consistent bedtime matters more than a perfect one.")
        }
        parts.joinToString(" ")
    }
}
