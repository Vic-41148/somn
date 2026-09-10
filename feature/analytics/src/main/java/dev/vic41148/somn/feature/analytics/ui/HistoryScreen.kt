package dev.vic41148.somn.feature.analytics.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
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
import kotlin.math.roundToInt

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

    // Previous code collected exportStatus here but never rendered it anywhere in this screen, export
    // success/failure messages from exportSelectedSessions() reached nobody. The fix wires it to a Snackbar
    // rather than inline Text, consistent with the same fix in SettingsScreen.
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(exportStatus) {
        exportStatus?.let { snackbarHostState.showSnackbar(it) }
    }

    androidx.compose.material3.Scaffold(
        // Same deal as SettingsScreen: this sits inside the app-level Scaffold's NavHost,
        // which already consumed the system-bar insets. Re-applying them here double-pads
        // the top and pushes the screen down next to the other tabs.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        // Lifted clear of the floating dock, which overlays content.
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState, modifier = Modifier.padding(bottom = 88.dp)) },
    ) { padding ->
        // One LazyColumn for the whole screen. The header (range, summary, filter) used to
        // sit in a fixed Column above a nested list. The top half of the screen never
        // scrolled and the list fought for the remaining space. Everything scrolls as one
        // now, the way a report should read.
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header matches the other tabs (plain headline + actions, no TopAppBar
            // surface band), so this lines up exactly with Home/Habits/Settings.
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedIds.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearBulkSelection() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear"
                            )
                        }
                    }
                    Text(
                        text = if (selectedIds.isNotEmpty()) "${selectedIds.size} Selected" else "History",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
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
            }

            // Range selector, stats header and list both follow this.
            item {
                ReportRangeRow(
                    selectedDays = rangeDays,
                    onSelect = { viewModel.selectRange(it) }
                )
            }

            // Summary header: the actual "report", averages, streak and best over the range.
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

            // Session type filter, same expandable-card language as the habit
            // sections, not a stock dropdown.
            if (allSessions.isNotEmpty()) {
                item {
                    dev.vic41148.somn.core.ui.components.ExpandablePickerCard(
                        title = "Filter by the session type",
                        icon = Icons.Default.FilterList,
                        iconColor = MaterialTheme.colorScheme.primary,
                        options = listOf("All Sessions") +
                            SessionType.entries.map { it.displayName },
                        selectedIndex = if (selectedTypeFilter == null) 0
                        else SessionType.entries.indexOf(selectedTypeFilter) + 1,
                        onSelect = {
                            selectedTypeFilter =
                                if (it == 0) null else SessionType.entries[it - 1]
                        }
                    )
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
                    // Score tier colors, the same ramp each row's score digit renders,
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
                // The floating dock overlays content (no Scaffold slot), trailing
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
    val selectedIndex = options.indexOfFirst { it.first == selectedDays }.coerceAtLeast(0)
    // One big pill with a sliding thumb, same motion language as the dock bubble:
    // spatial glide on a non-bouncy spring, so the thumb never overshoots past rest.
    // Taps select directly, horizontal drags slide the thumb under the finger and
    // snap to the nearest segment on release.
    val density = LocalDensity.current
    // NaN = not dragging, a pixel offset into the content while a drag is live.
    var dragOffsetPx by remember { mutableFloatStateOf(Float.NaN) }
    // Row height in px: the overlay thumb copies it explicitly, fillMaxHeight
    // collapses to zero inside this unbounded-height list item.
    var rowHeightPx by remember { mutableIntStateOf(0) }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp)
    ) {
        // Segments split the padded content width: maxWidth still spans the 4.dp
        // padding on both sides, so dividing it raw made the thumb a touch too
        // wide per segment and drift right, clipping flat against the pill edge
        // on the last option.
        val segmentWidth = (maxWidth - 8.dp) / options.size
        val segmentWidthPx = with(density) { segmentWidth.toPx() }
        val thumbX by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "rangeThumbX"
        )
        // Finger owns the thumb mid-drag, the spring owns it otherwise. Labels
        // preview the nearest segment under the finger and commit on release.
        val thumbOffset = if (dragOffsetPx.isNaN()) thumbX
        else with(density) { dragOffsetPx.toDp() }
        val previewIndex = if (dragOffsetPx.isNaN()) null
        else (dragOffsetPx / segmentWidthPx).roundToInt().coerceIn(0, options.size - 1)
        val effectiveIndex = previewIndex ?: selectedIndex
        // Drag lives on the content box (segment pixels are known here), taps
        // still land on the per-segment click targets below.
        Box(
            modifier = Modifier.pointerInput(segmentWidthPx) {
                detectHorizontalDragGestures(
                    onDragStart = { dragOffsetPx = segmentWidthPx * selectedIndex },
                    onHorizontalDrag = { _, dragAmount ->
                        dragOffsetPx = (dragOffsetPx + dragAmount)
                            .coerceIn(0f, segmentWidthPx * (options.size - 1))
                    },
                    onDragEnd = {
                        val index = (dragOffsetPx / segmentWidthPx).roundToInt()
                            .coerceIn(0, options.size - 1)
                        dragOffsetPx = Float.NaN
                        onSelect(options[index].first)
                    },
                    onDragCancel = { dragOffsetPx = Float.NaN }
                )
            }
        ) {
            // True segment-wide thumb: matchParentSize() sizes to the whole row
            // (Box forces it), so offset() merely slid a full-width slab whose
            // visible slice depended on the selection, correct-looking only on
            // the last option and flooding the bar everywhere else.
            Box(
                modifier = Modifier
                    .width(segmentWidth)
                    .height(with(density) { rowHeightPx.toDp() })
                    .offset(x = thumbOffset)
                    .clip(CircleShape)
                    // Selected-pill language matches the dock bubble and the filled
                    // buttons below (primary/onPrimary): primaryContainer sits too
                    // close to the track in dark dynamic themes and reads muddy.
                    .background(MaterialTheme.colorScheme.primary)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { rowHeightPx = it.height }
            ) {
                options.forEachIndexed { index, (days, label) ->
                    val selected = index == effectiveIndex
                    // Crossfade with the thumb glide instead of snapping, so the
                    // label never sits bright-on-grey (or grey-on-bright) mid-slide.
                    val labelColor by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(180),
                        label = "rangeLabelColor"
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .clickable(
                                onClickLabel = "Show $label",
                                role = Role.Tab
                            ) { onSelect(days) }
                            .semantics { this.selected = selected }
                            .padding(vertical = 10.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = labelColor
                        )
                    }
                }
            }
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
