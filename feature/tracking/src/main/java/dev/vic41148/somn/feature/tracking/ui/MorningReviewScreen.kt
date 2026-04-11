package dev.vic41148.somn.feature.tracking.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import dev.vic41148.somn.core.ui.components.Hypnogram
import dev.vic41148.somn.core.ui.components.MetricChip
import dev.vic41148.somn.core.ui.components.SleepCard
import dev.vic41148.somn.core.ui.components.SleepScoreRing
import dev.vic41148.somn.feature.tracking.SleepTrackingViewModel

@Composable
fun MorningReviewScreen(
    sessionId: Long,
    onDone: () -> Unit,
    viewModel: SleepTrackingViewModel = hiltViewModel()
) {
    val lastSession by viewModel.lastSession.collectAsState()
    val lastScore by viewModel.lastScore.collectAsState()
    val epochs by viewModel.epochs.collectAsState()

    var selectedMood by remember { mutableIntStateOf(0) }
    var notes by remember { mutableStateOf("") }

    val session = lastSession ?: return

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
                Hypnogram(
                    stages = epochs.map { it.stage },
                    modifier = Modifier.fillMaxWidth(),
                    height = 100.dp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text("🔴 Awake", style = MaterialTheme.typography.labelSmall)
                    Text("🔵 Light", style = MaterialTheme.typography.labelSmall)
                    Text("🟣 Deep", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Key metrics
        SleepCard(title = "Sleep Stats") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val hours = session.sleepDurationMinutes / 60
                val mins = session.sleepDurationMinutes % 60
                MetricChip(label = "Duration", value = "${hours}h ${mins}m")
                MetricChip(label = "Efficiency", value = "${session.sleepEfficiency.toInt()}%")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricChip(label = "Deep Sleep", value = "${session.deepSleepPercent.toInt()}%")
                MetricChip(label = "Wake Events", value = "${session.wakeEvents}")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Mood rating
        SleepCard(title = "How do you feel?") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val moods = listOf("😴" to 1, "😕" to 2, "😐" to 3, "🙂" to 4, "😄" to 5)
                moods.forEach { (emoji, value) ->
                    FilterChip(
                        selected = selectedMood == value,
                        onClick = {
                            selectedMood = value
                            viewModel.updateMood(session.id, value)
                        },
                        label = { Text(emoji, style = MaterialTheme.typography.titleLarge) }
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
            Text("  Done", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
