package dev.vic41148.somn.core.domain.usecase

import dev.vic41148.somn.core.domain.model.HabitEntry
import dev.vic41148.somn.core.domain.model.HabitLog
import dev.vic41148.somn.core.domain.model.SleepSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * R4 Behavior science: settled reads, proactive shift flags, tags as predictors.
 *
 * WHOOP-Recovery-Impacts mechanics, Somn-flavored: the Pearson core stays, but every
 * read now says whether it is an early read or settled, material moves get flagged
 * without being asked, and tag presence joins the big-four habits as a binary predictor.
 * Pure functions — the screens only render what these return.
 */

/** Early read vs settled: the same r from 7 nights is tentative, from 90 nights is firm. */
val CorrelationConfidence.maturityLabel: String
    get() = when (this) {
        CorrelationConfidence.LOW -> "Early read"
        CorrelationConfidence.MEDIUM -> "Firming up"
        CorrelationConfidence.HIGH -> "Settled"
    }

/** One tagged-vs-untagged comparison, n-of-1 voiced. Null when either side is too thin. */
data class TagImpact(
    val tagName: String,
    val taggedAvgScore: Int,
    val untaggedAvgScore: Int,
    val taggedNights: Int,
    val untaggedNights: Int,
    val insight: String
)

data class TaggedNight(val tagged: Boolean, val score: Int)

/** WHOOP's 5-yes/5-no rule per 90 days: both sides need this many nights to count. */
const val MIN_TAG_SIDE_NIGHTS = 5

fun tagImpact(tagName: String, nights: List<TaggedNight>): TagImpact? {
    val tagged = nights.filter { it.tagged }
    val untagged = nights.filterNot { it.tagged }
    if (tagged.size < MIN_TAG_SIDE_NIGHTS || untagged.size < MIN_TAG_SIDE_NIGHTS) return null
    val taggedAvg = tagged.map { it.score }.average()
    val untaggedAvg = untagged.map { it.score }.average()
    val delta = (taggedAvg - untaggedAvg).toInt()
    val direction = when {
        delta > 0 -> "higher"
        delta < 0 -> "lower"
        else -> "about the same"
    }
    return TagImpact(
        tagName = tagName,
        taggedAvgScore = taggedAvg.toInt(),
        untaggedAvgScore = untaggedAvg.toInt(),
        taggedNights = tagged.size,
        untaggedNights = untagged.size,
        insight = if (delta == 0) {
            "Nights tagged '$tagName' score about the same " +
                "(${taggedAvg.toInt()} vs ${untaggedAvg.toInt()}, " +
                "${tagged.size} vs ${untagged.size} nights)."
        } else {
            "Nights tagged '$tagName' average ${abs(delta)} pts $direction " +
                "(${taggedAvg.toInt()} vs ${untaggedAvg.toInt()}, " +
                "${tagged.size} vs ${untagged.size} nights)."
        }
    )
}

/** A material move the user didn't ask about — frequency or metric slide. */
data class ShiftFlag(val title: String, val detail: String)

/** 30d-vs-prior-30d mean comparison; fires on a real slide, silent on noise or gains. */
fun efficiencySlide(
    recentEfficiencies: List<Float>,
    priorEfficiencies: List<Float>,
    dropThresholdPoints: Float = 5f,
    minNightsEachSide: Int = 5
): ShiftFlag? {
    if (recentEfficiencies.size < minNightsEachSide || priorEfficiencies.size < minNightsEachSide) {
        return null
    }
    val drop = priorEfficiencies.average() - recentEfficiencies.average()
    if (drop < dropThresholdPoints) return null
    return ShiftFlag(
        title = "Efficiency slid ${drop.toInt()} points this month",
        detail = "From ${priorEfficiencies.average().toInt()}% to " +
            "${recentEfficiencies.average().toInt()}% over the last 30 nights. " +
            "Worth a look at what changed."
    )
}

/** Behavior-frequency move: doubled (or newly appeared) month-over-month. */
fun frequencyShift(label: String, recentDays: Int, pastDays: Int): ShiftFlag? {
    if (recentDays < 4) return null
    if (pastDays == 0) {
        return ShiftFlag(
            title = "$label appeared this month",
            detail = "$label on $recentDays nights in the last 30 days, " +
                "none in the 30 before that."
        )
    }
    if (recentDays < 2 * pastDays) return null
    return ShiftFlag(
        title = "$label frequency doubled this month",
        detail = "$pastDays → $recentDays nights over the last 30 days."
    )
}

/**
 * Orchestrator: efficiency slide + alcohol/caffeine frequency shifts over trailing
 * 30d vs prior 30d windows. Empty list is the common case — no news is no cards.
 */
fun detectShifts(
    sessions: List<SleepSession>,
    habitLogs: List<HabitLog>,
    nowMillis: Long = System.currentTimeMillis()
): List<ShiftFlag> {
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val recentStart = today.minusDays(30)
    val pastStart = today.minusDays(60)

    val flags = mutableListOf<ShiftFlag>()

    val completed = sessions.filter { it.isCompleted }
    val effByDate = completed.associate { s ->
        Instant.ofEpochMilli(s.startTimeMillis).atZone(zone).toLocalDate() to s.sleepEfficiency
    }
    val recentEff = effByDate.filterKeys { !it.isBefore(recentStart) }.values.toList()
    val pastEff = effByDate.filterKeys { it.isBefore(recentStart) && !it.isBefore(pastStart) }
        .values.toList()
    efficiencySlide(recentEff, pastEff)?.let { flags.add(it) }

    val logsByDate = habitLogs.groupBy { it.date }
    fun behaviorDays(pred: (HabitEntry) -> Boolean, from: LocalDate, to: LocalDate): Int =
        logsByDate.filterKeys { !it.isBefore(from) && it.isBefore(to) }.count { (_, logs) ->
            logs.any { pred(it.entry) }
        }

    flags.addAll(
        listOfNotNull(
            frequencyShift(
                "Alcohol",
                behaviorDays({ it is HabitEntry.Alcohol }, recentStart, today),
                behaviorDays({ it is HabitEntry.Alcohol }, pastStart, recentStart)
            ),
            frequencyShift(
                "Late caffeine",
                behaviorDays(
                    { it is HabitEntry.Caffeine && it.timeOfDay.hour >= 14 },
                    recentStart, today
                ),
                behaviorDays(
                    { it is HabitEntry.Caffeine && it.timeOfDay.hour >= 14 },
                    pastStart, recentStart
                )
            )
        )
    )
    return flags
}
