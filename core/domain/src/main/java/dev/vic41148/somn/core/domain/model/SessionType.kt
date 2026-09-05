package dev.vic41148.somn.core.domain.model

/**
 * What kind of sleep/rest session was tracked.
 *
 * Only [MAIN_SLEEP] sessions feed nightly consistency/streak scoring and circadian
 * analysis (chronotype, social jet lag, weekly reports) — naps, commute sleep, and
 * shift-work sessions are tracked and scored individually but excluded from those
 * aggregates so they do not dilute or distort them.
 */
enum class SessionType(val displayName: String) {
    MAIN_SLEEP("Main Sleep"),
    NAP("Nap"),
    COMMUTE("Commute"),
    SHIFT("Shift Work")
}
