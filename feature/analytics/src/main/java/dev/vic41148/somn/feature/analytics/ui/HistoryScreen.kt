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
import androidx.compose.runtime.key
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
import dev.vic41148.somn.core.ui.components.SleepCard
import dev.vic41148.somn.core.ui.components.StatRing
import dev.vic41148.somn.core.ui.theme.ScoreTier
import dev.vic41148.somn.core.ui.theme.scoreColor
import dev.vic41148.somn.core.domain.model.SleepSession
import dev.vic41148.somn.core.domain.usecase.ReportSummary
import dev.vic41148.somn.core.domain.usecase.formatDurationShort
import dev.vic41148.somn.feature.analytics.AnalyticsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Shared across all SessionRow instances (LazyColumn runs single-threaded on the main thread).
// The previous code constructed these fresh on every row recomposition, which allocated a locale-symbol
// table lookup per row per scroll frame.
private val historyDateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
private val historyTimeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onSessionClick: (Long) -> Unit,
    onNavigateToCircadian: () -> Unit,
    onNavigateToTrends: () -> Unit,
    onNavigateToVitals: () -> Unit = {},
    onNavigateToReports: () -> Unit = {},
    onAddManualSession: () -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val allSessions by viewModel.sessions.collectAsState()
    val rangedSessions by viewModel.rangedSessions.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val rangeDays by viewModel.rangeDays.collectAsState()
    var selectedTypeFilter by remember { mutableStateOf<SessionType?>(null) }
    val sessions = remember(rangedSessions, selectedTypeFilter) {
        if (selectedTypeFilter == null) rangedSessions
        else rangedSessions.filter { it.sessionType == selectedTypeFilter }
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

    // Previous code collected exportStatus here but never rendered it anywhere in this screen — export
    // success/failure messages from exportSelectedSessions() reached nobody. The fix wires it to a Snackbar
    // rather than inline Text, consistent with the same fix in SettingsScreen.
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(exportStatus) {
        exportStatus?.let { snackbarHostState.showSnackbar(it) }
    }

    androidx.compose.material3.Scaffold(
        // Lifted clear of the floating dock, which overlays content.
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState, modifier = Modifier.padding(bottom = 88.dp)) },
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
                                contentDescription = "Add a manual entry"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        // One LazyColumn for the whole screen. The header (range, summary, filter) used to
        // sit in a fixed Column above a nested list. The top half of the screen never
        // scrolled and the list fought for the remaining space. Everything scrolls as one
        // now, the way a report should read.
        var filterExpanded by remember { mutableStateOf(false) }
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Range selector — stats header and list both follow this.
            item {
                ReportRangeRow(
                    selectedDays = rangeDays,
                    onSelect = { viewModel.selectRange(it) }
                )
            }

            // Summary header: the actual "report" — averages, streak and best over the range.
            // Each ring opens Trends, where Trends breaks the same numbers down per metric.
            summary?.let { report ->
                item {
                    SummaryCard(
                        summary = report,
                        rangeLabel = rangeLabel(rangeDays),
                        onRingClick = onNavigateToTrends
                    )
                }
            }

            // Session type filter dropdown
            if (allSessions.isNotEmpty()) {
                item {
                    ExposedDropdownMenuBox(
                        expanded = filterExpanded,
                        onExpandedChange = { filterExpanded = !filterExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedTypeFilter?.displayName ?: "All Sessions",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Filter by the session type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = filterExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = filterExpanded,
                            onDismissRequest = { filterExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Sessions") },
                                onClick = {
                                    selectedTypeFilter = null
                                    filterExpanded = false
                                }
                            )
                            SessionType.entries.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.displayName) },
                                    onClick = {
                                        selectedTypeFilter = type
                                        filterExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (sessions.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = when {
                                allSessions.isEmpty() -> "No sleep data yet"
                                rangedSessions.isEmpty() -> "No sessions are in this range. Use a wider range"
                                else -> "No ${selectedTypeFilter?.displayName} sessions yet"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                if (exportProgress != null) {
                    item {
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { exportProgress!! },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

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
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text("View Circadian Insights")
                    }
                }
                item {
                    androidx.compose.material3.Button(
                        onClick = onNavigateToVitals,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text("View Vitals")
                    }
                }
                item {
                    androidx.compose.material3.Button(
                        onClick = onNavigateToReports,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp)
                    ) {
                        Text("View Reports")
                    }
                }
                // The floating dock overlays content (no Scaffold slot) — trailing
                // clearance so the last rows scroll clear of the pill.
                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
    }
}

private fun rangeLabel(days: Int?): String = when (days) {
    7 -> "Last 7 days"
    30 -> "Last 30 days"
    90 -> "Last 90 days"
    else -> "All time"
}

@Composable
private fun ReportRangeRow(
    selectedDays: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(7 to "Week", 30 to "Month", 90 to "3 mo", null to "All")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (days, label) ->
            FilterChip(
                selected = selectedDays == days,
                onClick = { onSelect(days) },
                label = { Text(label) }
            )
        }
    }
}

/** Header "report": average rings plus streak/best/total so the screen answers the question. */
@Composable
private fun SummaryCard(
    summary: ReportSummary,
    rangeLabel: String,
    onRingClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SleepCard(title = rangeLabel, modifier = modifier) {
        // New ring instances per range so the fill animation replays from zero on every switch.
        key(rangeLabel) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatRing(
                label = "Score",
                value = "${summary.avgScore}",
                fraction = summary.avgScore / 100f,
                color = scoreColor(summary.avgScore),
                size = 72.dp,
                strokeWidth = 8.dp,
                onClick = onRingClick
            )
            StatRing(
                label = "Sleep",
                value = formatDurationShort(summary.avgDurationMinutes),
                fraction = (summary.avgDurationMinutes / 480f).coerceIn(0f, 1f),
                color = MaterialTheme.colorScheme.primary,
                size = 72.dp,
                strokeWidth = 8.dp,
                onClick = onRingClick
            )
            StatRing(
                label = "Efficiency",
                value = "${summary.avgEfficiencyPercent}%",
                fraction = summary.avgEfficiencyPercent / 100f,
                color = MaterialTheme.colorScheme.tertiary,
                size = 72.dp,
                strokeWidth = 8.dp,
                onClick = onRingClick
            )
        }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SummaryMiniStat(value = "${summary.nights}", label = "Nights")
            SummaryMiniStat(value = "${summary.streakNights}", label = "Night streak")
            SummaryMiniStat(value = "${summary.bestScore}", label = "Best score")
            SummaryMiniStat(
                value = (if (summary.scoreDelta >= 0) "+" else "") + "${summary.scoreDelta}",
                label = "Trend"
            )
        }
    }
}

@Composable
private fun SummaryMiniStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    text = "${hours}h ${mins}m • ${session.sleepEfficiency.toInt()}% efficiency",
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
