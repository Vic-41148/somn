package dev.vic41148.somn.core.domain.usecase

import dev.vic41148.somn.core.domain.model.ExternalVitalsSnapshot

/**
 * Vitals dashboard flags — each wearable vital against the user's own recent range.
 * WHOOP-Health-Monitor pattern without the hardware: a check when the latest night
 * sits inside the personal typical range, a flag when it drifts out, and honesty
 * ("needs more nights") instead of a verdict when history is thin.
 *
 * Pure function, unit-tested; the screen only renders what this returns.
 */
data class VitalFlag(
    val label: String,
    /** Latest-night reading, e.g. "62 bpm". */
    val value: String,
    /** e.g. "Usual 58–64 bpm" or "Needs 3+ nights with vitals". */
    val detail: String,
    val inRange: Boolean,
    val hasData: Boolean
)

private const val MIN_HISTORY_NIGHTS = 3

fun flagVitals(
    latest: ExternalVitalsSnapshot?,
    history: List<ExternalVitalsSnapshot>
): List<VitalFlag> {
    if (latest == null) return emptyList()
    val past = history.filter { it.sessionId != latest.sessionId }
    return listOf(
        flagOne(
            label = "Resting HR",
            latest = latest.restingHeartRateBpm,
            past = past.mapNotNull { it.restingHeartRateBpm },
            format = { "${it.toInt()} bpm" }
        ),
        flagOne(
            label = "HRV",
            latest = latest.avgHeartRateVariabilityMs,
            past = past.mapNotNull { it.avgHeartRateVariabilityMs },
            format = { "${it.toInt()} ms" }
        ),
        flagOne(
            label = "SpO2",
            latest = latest.avgSpo2Percent,
            past = past.mapNotNull { it.avgSpo2Percent },
            format = { "${it.toInt()}%" }
        ),
        flagOne(
            label = "Skin temp",
            latest = latest.avgSkinTemperatureCelsius,
            past = past.mapNotNull { it.avgSkinTemperatureCelsius },
            format = { "${"%.1f".format(it)}°C" }
        )
    )
}

private fun flagOne(
    label: String,
    latest: Float?,
    past: List<Float>,
    format: (Float) -> String
): VitalFlag {
    if (latest == null || past.size < MIN_HISTORY_NIGHTS) {
        return VitalFlag(
            label = label,
            value = latest?.let(format) ?: "–",
            detail = "Needs $MIN_HISTORY_NIGHTS+ nights with vitals",
            inRange = true,
            hasData = false
        )
    }
    // Typical range = 10th–90th percentile of history: robust to one weird night,
    // tighter than min–max so real drift actually flags.
    val sorted = past.sorted()
    val lo = sorted[(sorted.size * 0.1).toInt().coerceIn(0, sorted.size - 1)]
    val hi = sorted[(sorted.size * 0.9).toInt().coerceIn(0, sorted.size - 1)]
    val span = (hi - lo).coerceAtLeast(0.001f)
    // 10% tolerance outside the band before flagging — vitals wobble night to night.
    val inRange = latest >= lo - span * 0.1f && latest <= hi + span * 0.1f
    return VitalFlag(
        label = label,
        value = format(latest),
        detail = "Usual ${format(lo)}–${format(hi)}",
        inRange = inRange,
        hasData = true
    )
}
