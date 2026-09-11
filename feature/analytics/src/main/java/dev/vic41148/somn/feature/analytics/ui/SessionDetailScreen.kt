package dev.vic41148.somn.feature.analytics.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.vic41148.somn.core.ui.components.Hypnogram
import dev.vic41148.somn.core.ui.components.MetricChip
import dev.vic41148.somn.core.ui.components.PillRow
import dev.vic41148.somn.core.ui.components.SleepCard
import dev.vic41148.somn.core.ui.components.SleepScoreRing
import dev.vic41148.somn.core.domain.model.AudioEvent
import dev.vic41148.somn.core.domain.model.AudioEventType
import dev.vic41148.somn.feature.analytics.AnalyticsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Context
import android.content.pm.PackageManager
import dev.vic41148.somn.core.ui.components.AudioTimeline
import dev.vic41148.somn.core.ui.components.ExpandablePickerCard
import dev.vic41148.somn.core.ui.components.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    sessionId: Long,
    onBack: () -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val sessions by viewModel.sessions.collectAsState()
    val session = sessions.find { it.id == sessionId } ?: return
    
    val audioEvents by viewModel.observeAudioEvents(sessionId).collectAsState(initial = emptyList())
    val externalVitals by produceState<dev.vic41148.somn.core.domain.model.ExternalVitalsSnapshot?>(
        initialValue = null,
        key1 = sessionId
    ) {
        value = viewModel.getExternalVitals(sessionId)
    }

    val dateFormat = SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val context = LocalContext.current

    // Sleep-talk clips live here: no screenshots, no recordings, no switcher thumbnail.
    dev.vic41148.somn.core.ui.components.SecureScreen()

    var selectedAudioEventId by remember { mutableStateOf<Long?>(null) }
    var eventFilter by remember { mutableStateOf<AudioEventType?>(null) }
    var timelineExpanded by remember { mutableStateOf(false) }
    var pendingClipExport by remember { mutableStateOf<AudioEvent?>(null) }
    val clipExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/wav")
    ) { uri ->
        val event = pendingClipExport
        pendingClipExport = null
        if (uri != null && event != null) viewModel.exportClipTo(context, event, uri)
    }
    val visibleEvents = remember(audioEvents, eventFilter) {
        if (eventFilter == null) audioEvents
        else audioEvents.filter { it.type == eventFilter }
    }
    val clipPlayer = rememberAudioClipPlayer { path ->
        withContext(Dispatchers.IO) { viewModel.playableClip(path) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(dateFormat.format(Date(session.startTimeMillis))) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.deleteSession(session)
                        onBack()
                    }) {
                        Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Score
            SleepScoreRing(score = session.sleepScore, size = 120.dp, strokeWidth = 10.dp)

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${timeFormat.format(Date(session.startTimeMillis))} → ${timeFormat.format(Date(session.endTimeMillis))}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Metrics, uniform 3-column grid so every stat reads at the same weight.
            SleepCard(title = "Sleep Metrics") {
                val hours = session.sleepDurationMinutes / 60
                val mins = session.sleepDurationMinutes % 60
                PillRow {
                    MetricChip(label = "Duration", value = "${hours}h ${mins}m", modifier = Modifier.weight(1f).fillMaxHeight())
                    MetricChip(label = "In Bed", value = "${session.timeInBedMinutes / 60}h ${session.timeInBedMinutes % 60}m", modifier = Modifier.weight(1f).fillMaxHeight())
                    MetricChip(label = "Efficiency", value = "${session.sleepEfficiency.toInt()}%", modifier = Modifier.weight(1f).fillMaxHeight())
                }
                Spacer(modifier = Modifier.height(8.dp))
                PillRow {
                    MetricChip(label = "Onset", value = "${session.sleepOnsetMinutes}min", modifier = Modifier.weight(1f).fillMaxHeight())
                    MetricChip(label = "Deep", value = "${session.deepSleepPercent.toInt()}%", modifier = Modifier.weight(1f).fillMaxHeight())
                    MetricChip(label = "REM", value = "${session.remSleepPercent.toInt()}%", modifier = Modifier.weight(1f).fillMaxHeight())
                }
                Spacer(modifier = Modifier.height(8.dp))
                PillRow {
                    MetricChip(label = "Light", value = "${session.lightSleepPercent.toInt()}%", modifier = Modifier.weight(1f).fillMaxHeight())
                    MetricChip(label = "Wakes", value = "${session.wakeEvents}", modifier = Modifier.weight(1f).fillMaxHeight())
                    MetricChip(label = "Sounds", value = "${audioEvents.size}", modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Notes
            if (session.notes.isNotBlank()) {
                SleepCard(title = "Notes") {
                    Text(
                        text = session.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Morning Mood sits with the night's headline facts, not buried
            // below the fold past the audio sections.
            if (session.moodRating > 0) {
                val moods = listOf("", "Exhausted", "Tired", "Okay", "Good", "Great")
                SleepCard(title = "Morning Mood") {
                    Text(
                        text = moods.getOrElse(session.moodRating) { "" },
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // R4: tag this night, tag presence feeds the Patterns binary predictors.
            SessionTagsCard(sessionId = sessionId, viewModel = viewModel)

            Spacer(modifier = Modifier.height(16.dp))

            // Event type filter for the timeline and recordings below. Totals
            // in Audio Events stay global.
            if (audioEvents.isNotEmpty()) {
                ExpandablePickerCard(
                    title = "Filter by event type",
                    icon = Icons.Default.FilterList,
                    iconColor = MaterialTheme.colorScheme.primary,
                    options = listOf("All") + AudioEventType.entries.map { it.label() },
                    selectedIndex = if (eventFilter == null) 0
                    else AudioEventType.entries.indexOf(eventFilter) + 1,
                    onSelect = {
                        eventFilter = if (it == 0) null else AudioEventType.entries[it - 1]
                        selectedAudioEventId = null
                        clipPlayer.stop()
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Audio timeline: tapping a marker selects it and plays its clip, if kept.
            if (visibleEvents.isNotEmpty()) {
                SleepCard(
                    title = "Audio timeline",
                    action = {
                        IconButton(onClick = { timelineExpanded = !timelineExpanded }) {
                            Icon(
                                if (timelineExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (timelineExpanded) "Collapse timeline" else "Expand timeline"
                            )
                        }
                    }
                ) {
                    AnimatedVisibility(
                        visible = timelineExpanded,
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
                        Column {
                            AudioTimeline(
                                events = visibleEvents,
                        sessionStartTime = session.startTimeMillis,
                        sessionDurationMillis = (session.endTimeMillis - session.startTimeMillis).coerceAtLeast(1000),
                        startLabel = timeFormat.format(Date(session.startTimeMillis)),
                        endLabel = timeFormat.format(Date(session.endTimeMillis)),
                        selectedEventId = selectedAudioEventId,
                        onEventSelected = { event ->
                            selectedAudioEventId = event.id
                            event.clipPath?.let { clipPlayer.play(event) }
                        }
                    )
                    val selected = visibleEvents.find { it.id == selectedAudioEventId }
                    if (selected != null && selected.clipPath == null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No recording kept for this event.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Audio Events Summary
            if (audioEvents.isNotEmpty()) {
                SleepCard(title = "Audio Events") {
                    PillRow {
                        val snoreCount = audioEvents.count { it.type == AudioEventType.SNORE }
                        val coughCount = audioEvents.count { it.type == AudioEventType.COUGH }
                        val talkCount = audioEvents.count { it.type == AudioEventType.TALK }
                        MetricChip(label = "Snoring", value = "$snoreCount", modifier = Modifier.weight(1f).fillMaxHeight())
                        MetricChip(label = "Coughs", value = "$coughCount", modifier = Modifier.weight(1f).fillMaxHeight())
                        MetricChip(label = "Talking", value = "$talkCount", modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Recordings: every event that kept a clip, with one shared player.
            val clipEvents = visibleEvents.filter { it.clipPath != null }
            if (clipEvents.isNotEmpty()) {
                AudioRecordingsCard(
                    events = clipEvents,
                    player = clipPlayer,
                    formatTime = { millis -> timeFormat.format(Date(millis)) },
                    selectedEventId = selectedAudioEventId,
                    onShareClick = { event ->
                        pendingClipExport = event
                        clipExportLauncher.launch(
                            "somn_${event.type.name.lowercase()}_${event.id}.wav"
                        )
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // External Vitals (HEALTH-01), HR/HRV/SpO2/skin temp a paired wearable wrote to Health Connect
            externalVitals?.let { vitals ->
                if (vitals.hasAnyData) {
                    // sourceApp is stored as a package name (e.g. "com.fitbit.FitbitMobile"), not
                    // a display name. Resolve it here at the UI layer rather than in the data
                    // layer, so the stable package name stays what is actually persisted.
                    val sourceLabel = remember(vitals.sourceApp) {
                        vitals.sourceApp?.let { resolveAppLabel(context, it) }
                    }
                    SleepCard(title = "Vitals" + (sourceLabel?.let { " · $it" } ?: "")) {
                        PillRow {
                            vitals.avgHeartRateBpm?.let {
                                MetricChip(label = "Average HR", value = "${it.toInt()} bpm", modifier = Modifier.weight(1f).fillMaxHeight())
                            }
                            vitals.restingHeartRateBpm?.let {
                                MetricChip(label = "Resting HR", value = "${it.toInt()} bpm", modifier = Modifier.weight(1f).fillMaxHeight())
                            }
                            vitals.avgHeartRateVariabilityMs?.let {
                                MetricChip(label = "HRV", value = "${it.toInt()} ms", modifier = Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                        if (vitals.avgSpo2Percent != null || vitals.avgSkinTemperatureCelsius != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            PillRow {
                                vitals.avgSpo2Percent?.let {
                                    MetricChip(label = "SpO2", value = "${it.toInt()}%", modifier = Modifier.weight(1f).fillMaxHeight())
                                }
                                vitals.minSpo2Percent?.let {
                                    MetricChip(label = "Min SpO2", value = "${it.toInt()}%", modifier = Modifier.weight(1f).fillMaxHeight())
                                }
                                vitals.avgSkinTemperatureCelsius?.let {
                                    MetricChip(label = "Skin temperature", value = "${"%.1f".format(it)}°C", modifier = Modifier.weight(1f).fillMaxHeight())
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // The floating dock overlays content (no Scaffold slot), trailing
            // clearance so the last card scrolls clear of the pill.
            Spacer(modifier = Modifier.height(72.dp))

        }
    }
}

/**
 * R4 Tags card: chips grouped under their category headers (the MorningReview
 * moods pattern). Tagging a night feeds the Patterns screen's binary
 * predictors, five tagged nights minimum before a tag earns its own read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionTagsCard(sessionId: Long, viewModel: AnalyticsViewModel) {
    val allTags by viewModel.allTags.collectAsState()
    val attached by viewModel.observeSessionTags(sessionId).collectAsState(initial = emptyList())
    val attachedIds = attached.map { it.id }.toSet()
    var tagsExpanded by remember { mutableStateOf(false) }
    SleepCard(
        title = "Tags",
        action = {
            IconButton(onClick = { tagsExpanded = !tagsExpanded }) {
                Icon(
                    if (tagsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (tagsExpanded) "Collapse tags" else "Expand tags"
                )
            }
        }
    ) {
        AnimatedVisibility(
            visible = tagsExpanded,
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
            Column {
                if (allTags.isEmpty()) {
                    Text(
                        "No tags yet. They appear here after your first tagged night.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val grouped = remember(allTags) {
                        allTags.groupBy { it.category.ifBlank { "General" } }
                    }
                    grouped.entries.forEachIndexed { groupIndex, (category, tags) ->
                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tags.forEach { tag ->
                                val isAttached = attachedIds.contains(tag.id)
                                androidx.compose.material3.FilterChip(
                                    selected = isAttached,
                                    onClick = { viewModel.toggleSessionTag(sessionId, tag.id, isAttached) },
                                    label = { Text(tag.name) },
                                    leadingIcon = if (isAttached) {
                                        { Icon(Icons.Default.Check, contentDescription = null) }
                                    } else null
                                )
                            }
                        }
                        if (groupIndex < grouped.size - 1) {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Resolves a Health Connect data-origin package name (e.g. "com.fitbit.FitbitMobile") to the
 * app's display label (e.g. "Fitbit"). It falls back to the raw package name if it is not
 * installed/resolvable, it never crashes on an unresolvable package.
 *
 * Uses the plain `getApplicationInfo(String, Int)` overload rather than the API 33+
 * `ApplicationInfoFlags` variant, this module's minSdk is 26.
 */
@Suppress("DEPRECATION")
private fun resolveAppLabel(context: Context, packageName: String): String {
    val packageManager = context.packageManager
    return try {
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(appInfo).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }
}
