package dev.vic41148.somn.feature.habits.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.SentimentVeryDissatisfied
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.vic41148.somn.core.domain.model.CaffeineSource
import dev.vic41148.somn.core.domain.model.ExerciseIntensity
import dev.vic41148.somn.core.domain.model.ExerciseType
import dev.vic41148.somn.core.domain.model.HabitEntry
import dev.vic41148.somn.core.domain.model.HabitLog
import dev.vic41148.somn.feature.habits.HabitViewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun DailyLogScreen(
    onNavigateToMedication: () -> Unit = {},
    onNavigateToCorrelations: () -> Unit = {},
    viewModel: HabitViewModel = hiltViewModel()
) {
    val todayLogs by viewModel.todayLogs.collectAsState()
    val profile by viewModel.userProfile.collectAsState()
    val showMedication = profile?.neurodivergentProfile?.adhdMode == true ||
        profile?.neurodivergentProfile?.medicationTracking == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Today's Log",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Track what affects your sleep",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ---- Caffeine ----
        HabitSection(
            title = "Caffeine",
            icon = Icons.Default.Coffee,
            iconColor = MaterialTheme.colorScheme.tertiary
        ) {
            CaffeineLogForm { entry -> viewModel.logEntry(entry) }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ---- Alcohol ----
        HabitSection(
            title = "Alcohol",
            icon = Icons.Default.LocalBar,
            iconColor = MaterialTheme.colorScheme.secondary
        ) {
            AlcoholLogForm { entry -> viewModel.logEntry(entry) }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ---- Exercise ----
        HabitSection(
            title = "Exercise",
            icon = Icons.Default.DirectionsRun,
            iconColor = MaterialTheme.colorScheme.primary
        ) {
            ExerciseLogForm { entry -> viewModel.logEntry(entry) }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ---- Stress ----
        HabitSection(
            title = "Stress",
            icon = Icons.Default.SentimentVeryDissatisfied,
            iconColor = MaterialTheme.colorScheme.error
        ) {
            StressLogForm { entry -> viewModel.logEntry(entry) }
        }

        // ---- Medication (ADHD-gated) ----
        if (showMedication) {
            Spacer(modifier = Modifier.height(12.dp))
            HabitSection(
                title = "Medication",
                icon = Icons.Default.Medication,
                iconColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                TextButton(onClick = onNavigateToMedication) {
                    Text("Open medication log →")
                }
            }
        }

        // ---- Correlation insights ----
        // CorrelationInsightsScreen was fully built (its own ViewModel data, UI, and nav-graph
        // route already existed) but nothing anywhere ever navigated to it — this was the only
        // missing piece keeping the whole feature unreachable.
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onNavigateToCorrelations,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("View how habits affect your sleep →")
        }

        // ---- Today's entries ----
        if (todayLogs.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Logged today",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            todayLogs.forEach { log ->
                LoggedEntryRow(log = log, onDelete = { viewModel.deleteLog(log) })
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ---- Section card ----

@Composable
private fun HabitSection(
    title: String,
    icon: ImageVector,
    iconColor: androidx.compose.ui.graphics.Color,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    // One animator owns the height change. This Card used to also carry animateContentSize(),
    // which ran its own tween over the same expand/collapse that AnimatedVisibility below was
    // already animating on a different spec — the two chased each other, so the card visibly
    // rubber-banded and every section under it kept sliding long after the content had settled.
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column {
            Row(
                // Padding goes inside the click target, not around it. It used to sit on the
                // parent Column, which left a 16dp dead border where a tap on the card's own
                // edge — visually part of the header — hit nothing at all.
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClickLabel = if (expanded) "Collapse $title" else "Expand $title",
                        role = Role.Button
                    ) { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(iconColor.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = iconColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                // Material's motion split: size is spatial, so it springs; opacity is an effect,
                // so it uses a short linear-ish fade. The spring is deliberately non-bouncy —
                // these sections are stacked, and overshoot on one shoves every section below it
                // past its resting position and back, which is what made taps land on the wrong
                // card while the list was still settling.
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(animationSpec = tween(durationMillis = 150)),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeOut(animationSpec = tween(durationMillis = 100))
            ) {
                // Header owns its own padding now, so the body supplies the sides and bottom.
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    content()
                }
            }
        }
    }
}

// ---- Caffeine form ----

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun CaffeineLogForm(onLog: (HabitEntry.Caffeine) -> Unit) {
    var selectedSource by remember { mutableStateOf(CaffeineSource.COFFEE) }
    var customMg by remember { mutableIntStateOf(selectedSource.defaultMg) }
    var hour by remember { mutableIntStateOf(8) }
    var minute by remember { mutableIntStateOf(0) }

    Text("Drink", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(8.dp))

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CaffeineSource.entries.forEach { source ->
            FilterChip(
                selected = selectedSource == source,
                onClick = {
                    selectedSource = source
                    customMg = source.defaultMg
                },
                label = { Text("${source.displayName} (${source.defaultMg}mg)") }
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))
    Text("Time", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    TimeSliders(hour = hour, minute = minute, onHourChange = { hour = it }, onMinuteChange = { minute = it })

    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = {
            onLog(
                HabitEntry.Caffeine(
                    mg = customMg,
                    timeOfDay = LocalTime.of(hour, minute),
                    source = selectedSource
                )
            )
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Log ${selectedSource.displayName} (${customMg}mg)")
    }
}

// ---- Alcohol form ----

@Composable
private fun AlcoholLogForm(onLog: (HabitEntry.Alcohol) -> Unit) {
    var units by remember { mutableFloatStateOf(1f) }
    var hour by remember { mutableIntStateOf(19) }
    var minute by remember { mutableIntStateOf(0) }

    Text("Units: ${"%.1f".format(units)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(
        text = "1 unit = 10 ml pure alcohol (e.g. small wine, single spirit)",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Slider(
        value = units,
        onValueChange = { units = it },
        valueRange = 0.5f..10f,
        steps = 18
    )

    Spacer(modifier = Modifier.height(8.dp))
    Text("Time", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    TimeSliders(hour = hour, minute = minute, onHourChange = { hour = it }, onMinuteChange = { minute = it })

    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = {
            onLog(HabitEntry.Alcohol(units = units, timeOfDay = LocalTime.of(hour, minute)))
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Log ${"%.1f".format(units)} unit${if (units != 1f) "s" else ""}")
    }
}

// ---- Exercise form ----

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseLogForm(onLog: (HabitEntry.Exercise) -> Unit) {
    var selectedType by remember { mutableStateOf(ExerciseType.WALKING) }
    var selectedIntensity by remember { mutableStateOf(ExerciseIntensity.MODERATE) }
    var duration by remember { mutableFloatStateOf(30f) }
    var hour by remember { mutableIntStateOf(7) }
    var minute by remember { mutableIntStateOf(0) }

    Text("Activity", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExerciseType.entries.forEach { type ->
            FilterChip(selected = selectedType == type, onClick = { selectedType = type }, label = { Text(type.displayName) })
        }
    }

    Spacer(modifier = Modifier.height(12.dp))
    Text("Intensity", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ExerciseIntensity.entries.forEach { intensity ->
            FilterChip(
                selected = selectedIntensity == intensity,
                onClick = { selectedIntensity = intensity },
                label = { Text(intensity.displayName) }
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))
    Text("Duration: ${duration.toInt()} min", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Slider(value = duration, onValueChange = { duration = it }, valueRange = 5f..180f, steps = 34)

    Spacer(modifier = Modifier.height(8.dp))
    Text("Time", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    TimeSliders(hour = hour, minute = minute, onHourChange = { hour = it }, onMinuteChange = { minute = it })

    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = {
            onLog(
                HabitEntry.Exercise(
                    type = selectedType,
                    durationMinutes = duration.toInt(),
                    intensity = selectedIntensity,
                    timeOfDay = LocalTime.of(hour, minute)
                )
            )
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Log ${duration.toInt()} min ${selectedType.displayName}")
    }
}

// ---- Stress form ----

@Composable
private fun StressLogForm(onLog: (HabitEntry.Stress) -> Unit) {
    var stressLevel by remember { mutableIntStateOf(3) }
    val labels = listOf("Very calm", "Calm", "Neutral", "Stressed", "Very stressed")

    Text(
        text = labels[stressLevel - 1],
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        labels.forEachIndexed { index, _ ->
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (stressLevel == index + 1)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            androidx.compose.ui.graphics.Color.Transparent
                    )
                    .clickable { stressLevel = index + 1 }
                    .padding(8.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = { onLog(HabitEntry.Stress(level = stressLevel)) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Log stress level $stressLevel")
    }
}

// ---- Shared time sliders ----

@Composable
private fun TimeSliders(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit
) {
    val time = LocalTime.of(hour, (minute / 15) * 15)
    Text(
        text = time.format(timeFormatter),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
    Slider(value = hour.toFloat(), onValueChange = { onHourChange(it.toInt()) }, valueRange = 0f..23f, steps = 22)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("00:00", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("23:00", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- Logged entry row ----

@Composable
private fun LoggedEntryRow(log: HabitLog, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = log.entry.summary(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun HabitEntry.summary(): String = when (this) {
    is HabitEntry.Caffeine -> "${source.displayName} — ${mg}mg at ${timeOfDay.format(timeFormatter)}"
    is HabitEntry.Alcohol -> "${units} unit${if (units != 1f) "s" else ""} at ${timeOfDay.format(timeFormatter)}"
    is HabitEntry.Exercise -> "${type.displayName} ${durationMinutes}min (${intensity.displayName}) at ${timeOfDay.format(timeFormatter)}"
    is HabitEntry.Stress -> when (level) {
        1 -> "Very calm"
        2 -> "Calm"
        3 -> "Neutral stress"
        4 -> "Stressed"
        5 -> "Very stressed"
        else -> "Stress level $level"
    }
    is HabitEntry.Medication -> "$name $dose at ${timeOfDay.format(timeFormatter)}"
}
