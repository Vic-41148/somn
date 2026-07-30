package dev.vic41148.somn.feature.alarm.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    var label by remember { mutableStateOf("") }
    var wakeWindow by remember { mutableStateOf(30f) }
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

        if (alarmId > 0 && !isInitialized) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        val timePickerState = rememberTimePickerState(
            initialHour = editingAlarm?.hour ?: 7,
            initialMinute = editingAlarm?.minute ?: 0
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
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

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
                                repeatDays = repeatDays
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
