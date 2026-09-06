package dev.vic41148.somn.feature.tracking.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.vic41148.somn.core.ui.components.ColorLegendItem
import dev.vic41148.somn.core.ui.components.HypnogramWithTable
import dev.vic41148.somn.core.ui.components.MetricChip
import dev.vic41148.somn.core.ui.components.PillRow
import dev.vic41148.somn.core.ui.components.SleepCard
import dev.vic41148.somn.core.ui.components.SleepScoreRing
import dev.vic41148.somn.core.ui.theme.StageAwake
import dev.vic41148.somn.core.ui.theme.StageDeep
import dev.vic41148.somn.core.ui.theme.StageLight
import dev.vic41148.somn.core.ui.theme.StageRem
import dev.vic41148.somn.core.domain.model.AudioEventType
import dev.vic41148.somn.feature.tracking.SleepTrackingViewModel

@Composable
fun MorningReviewScreen(
    sessionId: Long,
    onDone: () -> Unit,
    viewModel: SleepTrackingViewModel = hiltViewModel()
) {
    val sessionFlow by viewModel.observeSession(sessionId).collectAsState(initial = null)
    val lastScore by viewModel.lastScore.collectAsState()
    val epochs by viewModel.epochs.collectAsState()
    val audioEvents by viewModel.audioEvents.collectAsState()

    var selectedMood by rememberSaveable { mutableIntStateOf(0) }
    var notes by rememberSaveable { mutableStateOf("") }

    // This screen renders the session it was opened for (the sessionId argument) — never the
    // shared lastSession flow. The stop path fills lastSession asynchronously and can race this
    // screen's creation, so a relaunch mid-flow used to show a stale session from a previous
    // night. The detail data (score explanation, epochs, audio) loads once the session row
    // arrives, so it cannot race the stop path's commit either.
    LaunchedEffect(sessionFlow?.id) {
        sessionFlow?.let { viewModel.loadSessionDetail(it.id) }
    }

    val session = sessionFlow ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Good Morning!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Incomplete-night banner (REL-02): tracking stopped early, e.g. app/service was killed
        if (session.isPartial) {
            SleepCard(
                title = "Incomplete Night",
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Text(
                    text = "Tracking stopped early — this session may be missing data from later in the night.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Oversleep banner (SESS-03)
        if (session.isOversleep) {
            SleepCard(
                title = "Oversleep Detected",
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Text(
                    text = "This session ran well beyond your target sleep duration. Oversleeping can " +
                        "leave you groggy — consider a consistent wake time even on rest days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Sleep Score
        SleepScoreRing(
            score = session.sleepScore,
            size = 140.dp,
            strokeWidth = 10.dp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Score explanation
        lastScore?.let { score ->
            Text(
                text = score.explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Hypnogram
        if (epochs.isNotEmpty()) {
            SleepCard(title = "Sleep Stages") {
                val hypnogramStages = remember(epochs) { epochs.map { it.stage } }
                HypnogramWithTable(
                    stages = hypnogramStages,
                    modifier = Modifier.fillMaxWidth(),
                    height = 100.dp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ColorLegendItem(color = StageAwake, label = "Awake")
                    ColorLegendItem(color = StageRem, label = "REM")
                    ColorLegendItem(color = StageLight, label = "Light")
                    ColorLegendItem(color = StageDeep, label = "Deep")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sickness Flag Card
        if (session.coughEventCount >= 3) {
            SleepCard(
                title = "Health Alert",
                containerColor = MaterialTheme.colorScheme.errorContainer
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Unusual breathing patterns detected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "We noticed frequent coughing during the night. Monitor your symptoms.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Key metrics
        SleepCard(title = "Sleep Stats") {
            PillRow {
                val hours = session.sleepDurationMinutes / 60
                val mins = session.sleepDurationMinutes % 60
                MetricChip(label = "Duration", value = "${hours}h ${mins}m", modifier = Modifier.weight(1f).fillMaxHeight())
                MetricChip(label = "Efficiency", value = "${session.sleepEfficiency.toInt()}%", modifier = Modifier.weight(1f).fillMaxHeight())
            }
            Spacer(modifier = Modifier.height(8.dp))
            PillRow {
                MetricChip(label = "Deep Sleep", value = "${session.deepSleepPercent.toInt()}%", modifier = Modifier.weight(1f).fillMaxHeight())
                MetricChip(label = "Wake Events", value = "${session.wakeEvents}", modifier = Modifier.weight(1f).fillMaxHeight())
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Audio Events
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

        // Mood rating — FlowRow so five labels ("Exhausted"…) wrap instead of squeezing.
        SleepCard(title = "How do you feel?") {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val moods = listOf("Exhausted" to 1, "Tired" to 2, "Okay" to 3, "Good" to 4, "Great" to 5)
                moods.forEach { (label, value) ->
                    FilterChip(
                        selected = selectedMood == value,
                        onClick = {
                            selectedMood = value
                            viewModel.updateMood(session.id, value)
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Notes
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Notes (optional)") },
            placeholder = { Text("How was your night?") },
            minLines = 2,
            maxLines = 4
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Done button
        Button(
            onClick = {
                if (notes.isNotBlank()) {
                    viewModel.updateNotes(session.id, notes)
                }
                onDone()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Done", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
