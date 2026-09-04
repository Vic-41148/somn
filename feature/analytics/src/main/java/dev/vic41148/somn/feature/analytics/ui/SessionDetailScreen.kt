package dev.vic41148.somn.feature.analytics.ui

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import dev.vic41148.somn.core.domain.model.AudioEventType
import dev.vic41148.somn.feature.analytics.AnalyticsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri

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

            // Metrics — uniform 3-column grid so every stat reads at the same weight.
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
                    Spacer(modifier = Modifier.weight(1f))
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

            // Audio Timeline (Phase 3)
            dev.vic41148.somn.core.ui.components.AudioTimeline(
                events = audioEvents,
                sessionStartTime = session.startTimeMillis,
                sessionDurationMillis = (session.endTimeMillis - session.startTimeMillis).coerceAtLeast(1000),
                modifier = Modifier.padding(vertical = 16.dp),
                onSeekTo = { timestamp ->
                    // Handle seeking/playback if implements
                }
            )

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

            // Sleep Talk Clips
            val talkEvents = audioEvents.filter { it.type == AudioEventType.TALK && it.clipPath != null }
            if (talkEvents.isNotEmpty()) {
                val context = LocalContext.current
                val mediaPlayer = remember { MediaPlayer() }
                DisposableEffect(mediaPlayer) {
                    onDispose { mediaPlayer.release() }
                }

                SleepCard(title = "Sleep Talk Recordings") {
                    talkEvents.forEach { event ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = "Talk Clip - ${timeFormat.format(Date(event.timestampMillis))}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = "${event.durationSeconds}s",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = {
                                    try {
                                        mediaPlayer.reset()
                                        mediaPlayer.setOnPreparedListener { it.start() }
                                        mediaPlayer.setOnErrorListener { _, what, extra ->
                                            android.util.Log.e("SessionDetailScreen",
                                                "Talk clip playback failed: what=$what extra=$extra")
                                            true
                                        }
                                        mediaPlayer.setDataSource(context, Uri.parse(event.clipPath!!))
                                        // prepareAsync() instead of prepare() — the latter blocks
                                        // synchronously on the calling thread, which here is the
                                        // main/UI thread inside a click handler.
                                        mediaPlayer.prepareAsync()
                                    } catch (e: Exception) {
                                        android.util.Log.e("SessionDetailScreen", "Failed to play talk clip", e)
                                    }
                                }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                                }
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // External Vitals (HEALTH-01) — HR/HRV/SpO2/skin temp a paired wearable wrote to Health Connect
            val context = LocalContext.current
            externalVitals?.let { vitals ->
                if (vitals.hasAnyData) {
                    // sourceApp is stored as a package name (e.g. "com.fitbit.FitbitMobile"), not
                    // a display name — resolve it here at the UI layer rather than in the data
                    // layer, so the stable package name stays what's actually persisted.
                    val sourceLabel = remember(vitals.sourceApp) {
                        vitals.sourceApp?.let { resolveAppLabel(context, it) }
                    }
                    SleepCard(title = "Vitals" + (sourceLabel?.let { " · $it" } ?: "")) {
                        PillRow {
                            vitals.avgHeartRateBpm?.let {
                                MetricChip(label = "Avg HR", value = "${it.toInt()} bpm", modifier = Modifier.weight(1f).fillMaxHeight())
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
                                    MetricChip(label = "Skin Temp", value = "${"%.1f".format(it)}°C", modifier = Modifier.weight(1f).fillMaxHeight())
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Mood
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
            }
        }
    }
}

/**
 * Resolves a Health Connect data-origin package name (e.g. "com.fitbit.FitbitMobile") to the
 * Resolves a Health Connect data-origin package name (e.g. "com.fitbit.FitbitMobile") to the
 * app's display label (e.g. "Fitbit"), falling back to the raw package name if it isn't
 * installed/resolvable — never crashes on an unresolvable package.
 *
 * Uses the plain `getApplicationInfo(String, Int)` overload rather than the API 33+
 * `ApplicationInfoFlags` variant — this module's minSdk is 26.
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
