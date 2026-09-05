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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
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

        // Hoisted to screen level, outside AnimatedVisibility. rememberTimePickerState used
        // to live inside the AnimatedVisibility content, so the dial's state (including its
        // needle-animation clock) was tied to the enter/exit subcomposition — after tapping
        // AM/PM and dragging, the needle froze while the time still moved. It survives now.
        //
        // The AM/PM toggle inside the Material3 picker mutates `isAfternoon` on the *same*
        // state object, whose internal needle Animatable is then left pointing at a stale
        // angle; the first drag after the flip can orphan the draw layer so the hand stops
        // drawing until the screen reopens. The picker is therefore rebuilt with a fresh
        // state (and a fresh needle animation) whenever AM/PM flips and no finger is down.
        val initialPickerState = rememberTimePickerState(initialHour = 7, initialMinute = 0)
        var timePickerState by remember { mutableStateOf<TimePickerState>(initialPickerState) }
        var activePointers by remember { mutableIntStateOf(0) }
        var lastNoon by remember { mutableStateOf(if (timePickerState.hour >= 12) 1 else 0) }

        LaunchedEffect(timePickerState.hour >= 12, activePointers) {
            when {
                activePointers == 0 && (if (timePickerState.hour >= 12) 1 else 0) != lastNoon -> {
                    lastNoon = if (timePickerState.hour >= 12) 1 else 0
                    timePickerState = TimePickerState(
                        initialHour = timePickerState.hour,
                        initialMinute = timePickerState.minute,
                        is24Hour = timePickerState.is24hour
                    ).also { it.selection = timePickerState.selection }
                }
                else -> lastNoon = if (timePickerState.hour >= 12) 1 else 0
            }
        }

        LaunchedEffect(editingAlarm) {
            editingAlarm?.let {
                label = it.label
                wakeWindow = it.wakeWindowMinutes.toFloat()
                repeatDays = it.repeatDays
                // The picker used to snapshot editingAlarm?.hour at first composition (null →
                // 7:00) and never sync, so editing an alarm always opened showing 7:00 AM.
                timePickerState.hour = it.hour
                timePickerState.minute = it.minute
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
        // The save button used to be the last child of an unscrollable Column. A Material3
        // TimePicker dial alone is ~300dp, and with the day chips, label field and wake-window
        // slider above it the button sat past the bottom of the screen on a normal phone — laid
        // out, clipped, and completely unreachable, so an alarm could never actually be saved.
        // The dial now sits FIXED above the scroll region with the button pinned below it: a
        // verticalScroll parent used to steal the dial's circular drags (vertical components
        // scroll the form instead of moving the needle), which froze the needle animation.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (alarmId > 0) "Edit Alarm" else "New Alarm",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            when (event.type) {
                                PointerEventType.Press -> activePointers += 1
                                PointerEventType.Release ->
                                    activePointers = (activePointers - 1).coerceAtLeast(0)
                                PointerEventType.Move ->
                                    if (activePointers > 0 && event.changes.none { it.pressed }) {
                                        activePointers = 0
                                    }
                                else -> {}
                            }
                        }
                    }
                }
            ) {
                TimePicker(state = timePickerState)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

            // Repeat Days
            Text(
                text = "Repeat on",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))
            // FlowRow, not a squeezed SpaceEvenly row — seven chips at minimum touch width
            // overflow a 360dp screen once padding and font scale are accounted for.
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 7
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
