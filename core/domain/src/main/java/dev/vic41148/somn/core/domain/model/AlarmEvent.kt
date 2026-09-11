package dev.vic41148.somn.core.domain.model

/**
 * A single observed alarm lifecycle event: the alarm rang, was snoozed, was dismissed, the user
 * confirmed they were awake, or it was quietly auto-dismissed after the wake-confirmation cap
 * without any acknowledgement (a "missed" alarm).
 *
 * The event stores snapshots of the alarm's label and hour/minute at firing time so history stays
 * meaningful even after the alarm is later edited or deleted - the row is intentionally decoupled
 * from the current `alarms` row.
 */
data class AlarmEvent(
    val id: Long = 0,
    val alarmId: Long,
    val type: AlarmEventType,
    val timestampMillis: Long,
    val label: String = "",
    val hour: Int = 0,
    val minute: Int = 0,
    val snoozeCount: Int = 0,
    val detail: String = ""
) {
    val timeFormatted: String
        get() {
            val h = if (hour % 12 == 0) 12 else hour % 12
            val amPm = if (hour < 12) "AM" else "PM"
            return String.format("%d:%02d %s", h, minute, amPm)
        }
}

/** The kinds of alarm lifecycle events [AlarmEvent] captures. */
enum class AlarmEventType(val displayName: String) {
    FIRED("Rang"),
    SNOOZED("Snoozed"),
    DISMISSED("Dismissed"),
    CONFIRMED_AWAKE("Confirmed awake"),
    MISSED("Missed");

    companion object {
        fun valueOfSafe(name: String): AlarmEventType =
            entries.firstOrNull { it.name == name } ?: MISSED
    }
}