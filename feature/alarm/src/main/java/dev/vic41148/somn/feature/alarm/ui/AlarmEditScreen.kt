package dev.vic41148.somn.feature.alarm.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.vic41148.somn.feature.alarm.AlarmViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditScreen(
    alarmId: Long = 0L,
    onSaved: () -> Unit,
    viewModel: AlarmViewModel = hiltViewModel()
) {
    val editingAlarm by viewModel.editingAlarm.collectAsState()
    
    // UI state
    var label by rememberSaveable { mutableStateOf("") }
    var wakeWindow by rememberSaveable { mutableStateOf(30f) }
    var isInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(alarmId) {
        if (alarmId > 0) {
            viewModel.loadAlarmForEditing(alarmId)
        } else {
            viewModel.setEditingAlarm(null)
            isInitialized = true
        }
    }

        var repeatDays by remember { mutableStateOf<Set<Int>>(emptySet()) }
        
        LaunchedEffect(editingAlarm) {
            editingAlarm?.let {
                label = it.label
                wakeWindow = it.wakeWindowMinutes.toFloat()
                repeatDays = it.repeatDays
                isInitialized = true
            }
        }

        // The spinner fades out as the form fades in, so an edit screen opening from the
        // alarm list doesn't hard-cut between two surfaces (MOTION-04 entrance).
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = !isInitialized,
                modifier = Modifier.fillMaxSize(),
                exit = fadeOut(tween(200))
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            AnimatedVisibility(
                visible = isInitialized,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(tween(300)) +
                    scaleIn(initialScale = 0.98f, animationSpec = tween(300))
            ) {
                val timePickerState = rememberTimePickerState(
                    initialHour = editingAlarm?.hour ?: 7,
                    initialMinute = editingAlarm?.minute ?: 0
                )

        // The save button used to be the last child of an unscrollable Column. A Material3
        // TimePicker dial alone is ~300dp, and with the day chips, label field and wake-window
        // slider above it the button sat past the bottom of the screen on a normal phone — laid
        // out, clipped, and completely unreachable, so an alarm could never actually be saved.
        // The scrollable region now holds the form and the button is pinned outside it.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            Text(
                text = if (alarmId > 0) "Edit Alarm" else "New Alarm",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            TimePicker(state = timePickerState)

            Spacer(modifier = Modifier.height(24.dp))

            // Repeat Days
            Text(
                text = "Repeat on",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val days = listOf(
                    java.util.Calendar.MONDAY to "M",
                    java.util.Calendar.TUESDAY to "T",
                    java.util.Calendar.WEDNESDAY to "W",
                    java.util.Calendar.THURSDAY to "T",
                    java.util.Calendar.FRIDAY to "F",
                    java.util.Calendar.SATURDAY to "S",
                    java.util.Calendar.SUNDAY to "S"
                )
                days.forEach { (calendarDay, initial) ->
                    val isSelected = repeatDays.contains(calendarDay)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            repeatDays = if (isSelected) {
                                repeatDays - calendarDay
                            } else {
                                repeatDays + calendarDay
                            }
                        },
                        label = { Text(initial) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Label (optional)") },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Wake window slider
            Text(
                text = "Smart Wake Window: ${wakeWindow.toInt()} min",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = wakeWindow,
                onValueChange = { wakeWindow = it },
                valueRange = 10f..45f,
                steps = 6,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "The alarm will try to wake you during light sleep within this window before your set time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

                Spacer(modifier = Modifier.height(24.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (alarmId > 0 && editingAlarm != null) {
                            viewModel.updateAlarm(
                                editingAlarm!!.copy(
                                    hour = timePickerState.hour,
                                    minute = timePickerState.minute,
                                    label = label,
                                    repeatDays = repeatDays,
                                    wakeWindowMinutes = wakeWindow.toInt()
                                )
                            )
                        } else {
                            viewModel.createAlarm(
                                hour = timePickerState.hour,
                                minute = timePickerState.minute,
                                label = label,
                                repeatDays = repeatDays,
                                wakeWindowMinutes = wakeWindow.toInt()
                            )
                        }
                        onSaved()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (alarmId > 0) "Update Alarm" else "Save Alarm")
                }
            }
            }
        }
    }
}
