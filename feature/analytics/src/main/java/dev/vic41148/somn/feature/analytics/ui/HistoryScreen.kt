package dev.vic41148.somn.feature.analytics.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.hilt.navigation.compose.hiltViewModel
import dev.vic41148.somn.core.domain.model.SessionType
import dev.vic41148.somn.core.ui.components.ColorLegendItem
import dev.vic41148.somn.core.ui.theme.ScoreTier
import dev.vic41148.somn.core.ui.theme.scoreColor
import dev.vic41148.somn.core.domain.model.SleepSession
import dev.vic41148.somn.feature.analytics.AnalyticsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Shared across all SessionRow instances (LazyColumn runs single-threaded on the main thread) —
// these used to be constructed fresh on every row recomposition, allocating a locale-symbol
// table lookup per row per scroll frame.
private val historyDateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
private val historyTimeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onSessionClick: (Long) -> Unit,
    onNavigateToCircadian: () -> Unit,
    onNavigateToTrends: () -> Unit,
    onAddManualSession: () -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val allSessions by viewModel.sessions.collectAsState()
    var selectedTypeFilter by remember { mutableStateOf<SessionType?>(null) }
    val sessions = remember(allSessions, selectedTypeFilter) {
        if (selectedTypeFilter == null) allSessions
        else allSessions.filter { it.sessionType == selectedTypeFilter }
    }
    val selectedIds by viewModel.selectedSessionIds.collectAsState()
    val exportProgress by viewModel.exportProgress.collectAsState()
    val exportStatus by viewModel.exportStatus.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current

    // SAF File Picker Launcher
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            viewModel.exportSelectedSessions(context, it)
        }
    }

    // exportStatus was collected here but never rendered anywhere in this screen — export
    // success/failure messages from exportSelectedSessions() reached nobody. Wired to a Snackbar
    // rather than inline Text, consistent with the same fix in SettingsScreen.
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(exportStatus) {
        exportStatus?.let { snackbarHostState.showSnackbar(it) }
    }

    androidx.compose.material3.Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = {
                    Text(if (selectedIds.isNotEmpty()) "${selectedIds.size} Selected" else "History")
                },
                navigationIcon = {
                    if (selectedIds.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearBulkSelection() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear"
                            )
                        }
                    }
                },
                actions = {
                    if (selectedIds.isNotEmpty()) {
                        IconButton(onClick = { viewModel.deleteSelectedSessions() }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete"
                            )
                        }
                        IconButton(onClick = { launcher.launch(null) }) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = "Export"
                            )
                        }
                    } else {
                        IconButton(onClick = onAddManualSession) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add manual entry"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Session type filter dropdown
            if (allSessions.isNotEmpty()) {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    OutlinedTextField(
                        value = selectedTypeFilter?.displayName ?: "All Sessions",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Filter by Session Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Sessions") },
                            onClick = {
                                selectedTypeFilter = null
                                expanded = false
                            }
                        )
                        SessionType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName) },
                                onClick = {
                                    selectedTypeFilter = type
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            if (sessions.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (allSessions.isEmpty()) "No sleep data yet" else "No ${selectedTypeFilter?.displayName} sessions yet",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                if (exportProgress != null) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { exportProgress!! },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        // Score tier colors — the same ramp each row's score digit renders,
                        // spelled out once so the list reads as one system.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ScoreTier.entries.forEach { tier ->
                                ColorLegendItem(color = scoreColor(tier.minScore), label = tier.label)
                            }
                        }
                    }
                    items(sessions, key = { it.id }) { session ->
                        val isSelected = selectedIds.contains(session.id)
                        SessionRow(
                            session = session,
                            isSelected = isSelected,
                            onLongClick = { viewModel.toggleSelection(session.id) },
                            onClick = {
                                if (selectedIds.isNotEmpty()) {
                                    viewModel.toggleSelection(session.id)
                                } else {
                                    onSessionClick(session.id)
                                }
                            }
                        )
                    }
                    item {
                        androidx.compose.material3.Button(
                            onClick = onNavigateToTrends,
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                        ) {
                            Text("View Trends")
                        }
                    }
                    item {
                        androidx.compose.material3.Button(
                            onClick = onNavigateToCircadian,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp)
                        ) {
                            Text("View Circadian Insights")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    session: SleepSession,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = historyDateFormat.format(Date(session.startTimeMillis)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (session.sessionType != SessionType.MAIN_SLEEP) {
                        Text(
                            text = session.sessionType.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = "${historyTimeFormat.format(Date(session.startTimeMillis))} → ${historyTimeFormat.format(Date(session.endTimeMillis))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val hours = session.sleepDurationMinutes / 60
                val mins = session.sleepDurationMinutes % 60
                Text(
                    text = "${hours}h ${mins}m • ${session.sleepEfficiency.toInt()}% eff",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${session.sleepScore}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor(session.sleepScore)
                )
                if (session.moodRating > 0) {
                    val moods = listOf("", "Exhausted", "Tired", "Okay", "Good", "Great")
                    Text(
                        text = moods.getOrElse(session.moodRating) { "" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
