package dev.vic41148.somn.feature.alarm.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePickerState

/**
 * Rebuild policy for the alarm time dial.
 *
 * The Material3 picker keys its needle-animation state to a single [TimePickerState]
 * instance. The built-in AM/PM toggle flips noon on that same instance, leaving the
 * needle's internal Animatable at a stale angle; the first drag after the flip can
 * orphan the draw layer so the hand stops drawing until the screen reopens (numbers
 * stay, the time still moves). Replacing the instance with a fresh one re-keys every
 * per-state animation, which heals the stale state deterministically — but only when
 * no finger is on the dial, otherwise a mid-drag rebuild would fight the user's hand.
 */
internal fun alarmPickerShouldRebuild(pointerDown: Boolean, nowPm: Boolean, lastPm: Boolean): Boolean =
    !pointerDown && nowPm != lastPm

/** Builds a fresh [TimePickerState] that mirrors [previous] but re-keys the dial animations. */
@OptIn(ExperimentalMaterial3Api::class)
internal fun rebuiltAlarmPickerState(previous: TimePickerState): TimePickerState =
    TimePickerState(
        initialHour = previous.hour,
        initialMinute = previous.minute,
        is24Hour = previous.is24hour
    ).also { it.selection = previous.selection }