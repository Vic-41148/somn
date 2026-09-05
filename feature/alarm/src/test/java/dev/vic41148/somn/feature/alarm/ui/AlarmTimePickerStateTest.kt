package dev.vic41148.somn.feature.alarm.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePickerSelectionMode
import androidx.compose.material3.TimePickerState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Locks in the AM/PM-toggle rebuild policy that keeps the dial hand drawing.
 *
 * Regression source: toggling AM/PM then dragging the hand could make the hand stop
 * drawing until the screen reopened. The fix rebuilds the picker state whenever noon
 * flips while no finger is on the dial — these tests pin both the decision and the
 * state-preservation of the rebuild.
 */
@OptIn(ExperimentalMaterial3Api::class)
class AlarmTimePickerStateTest {

    @Test
    fun rebuilds_whenNoonFlips_andNoFingerIsDown() {
        assertThat(alarmPickerShouldRebuild(pointerDown = false, nowPm = true, lastPm = false)).isTrue()
        assertThat(alarmPickerShouldRebuild(pointerDown = false, nowPm = false, lastPm = true)).isTrue()
    }

    @Test
    fun doesNotRebuild_whenNoonFlips_duringADrag() {
        assertThat(alarmPickerShouldRebuild(pointerDown = true, nowPm = true, lastPm = false)).isFalse()
        assertThat(alarmPickerShouldRebuild(pointerDown = true, nowPm = false, lastPm = true)).isFalse()
    }

    @Test
    fun doesNotRebuild_whenNoonIsUnchanged() {
        assertThat(alarmPickerShouldRebuild(pointerDown = false, nowPm = true, lastPm = true)).isFalse()
        assertThat(alarmPickerShouldRebuild(pointerDown = false, nowPm = false, lastPm = false)).isFalse()
    }

    @Test
    fun rebuiltState_keepsPmHour_minute_and12hFormat() {
        val previous = TimePickerState(initialHour = 19, initialMinute = 34, is24Hour = false)

        val rebuilt = rebuiltAlarmPickerState(previous)

        assertThat(rebuilt.hour).isEqualTo(19)
        assertThat(rebuilt.minute).isEqualTo(34)
        assertThat(rebuilt.is24hour).isFalse()
    }

    @Test
    fun rebuiltState_keepsAmHour_andMinuteSelection() {
        val previous = TimePickerState(initialHour = 6, initialMinute = 15, is24Hour = false)
        previous.selection = TimePickerSelectionMode.Minute

        val rebuilt = rebuiltAlarmPickerState(previous)

        assertThat(rebuilt.hour).isEqualTo(6)
        assertThat(rebuilt.minute).isEqualTo(15)
        assertThat(rebuilt.selection).isEqualTo(TimePickerSelectionMode.Minute)
    }
}