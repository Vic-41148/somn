package dev.vic41148.somn.feature.alarm.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessAlarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.vic41148.somn.core.domain.model.AlarmEvent
import dev.vic41148.somn.core.domain.model.AlarmEventType
import dev.vic41148.somn.feature.alarm.AlarmHistoryViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * History of alarm lifecycle events (rang / snoozed / dismissed / confirmed-awake / missed).
 * Shows a "missed this week" summary plus a date-grouped event list, all driven by
 * [AlarmHistoryViewModel]. Events are point-in-time snapshots, so rows survive alarm edits/deletes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmHistoryScreen(
    onBack: () -> Unit = {},
    viewModel: AlarmHistoryViewModel = hiltViewModel()
) {
    val events by viewModel.events.collectAsState()
    val missedThisWeek by viewModel.missedThisWeek.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("Alarm History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (events.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.clearHistory() }
                        ) {
                            Text("Clear")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SummaryCard(missedThisWeek)

            if (events.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No alarm events yet",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Alarms that ring, get snoozed, dismissed, or missed will show up here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        events.groupBy { it.timestampMillis.toLocalDate() }.entries.toList(),
                        key = { it.key.toString() }
                    ) { (date, dayEvents) ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = date.headerLabel(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            dayEvents.forEach { event ->
                                AlarmEventRow(event)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(missedThisWeek: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (missedThisWeek > 0)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = if (missedThisWeek > 0)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (missedThisWeek == 1)
                        "1 missed alarm this week"
                    else if (missedThisWeek > 0)
                        "$missedThisWeek missed alarms this week"
                    else
                        "No missed alarms this week",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (missedThisWeek > 0)
                        MaterialTheme.colorScheme.onErrorContainer
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "A missed alarm never got a wake confirmation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AlarmEventRow(event: AlarmEvent) {
    val icon: ImageVector
    val tint: Color
    when (event.type) {
        AlarmEventType.FIRED -> {
            icon = Icons.Default.AccessAlarm
            tint = MaterialTheme.colorScheme.primary
        }
        AlarmEventType.SNOOZED -> {
            icon = Icons.Default.Snooze
            tint = MaterialTheme.colorScheme.tertiary
        }
        AlarmEventType.DISMISSED -> {
            icon = Icons.Default.Close
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        }
        AlarmEventType.CONFIRMED_AWAKE -> {
            icon = Icons.Default.Check
            tint = MaterialTheme.colorScheme.primary
        }
        AlarmEventType.MISSED -> {
            icon = Icons.Default.Warning
            tint = MaterialTheme.colorScheme.error
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = event.type.displayName,
                tint = tint,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (event.label.isNotBlank()) event.label else event.timeFormatted,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = event.type.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (event.detail.isNotBlank()) {
                    Text(
                        text = event.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = event.timestampMillis.toTimeLabel(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

private fun Long.toTimeLabel(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("h:mm a"))

private fun LocalDate.headerLabel(): String {
    val today = LocalDate.now()
    return when (this) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    }
}