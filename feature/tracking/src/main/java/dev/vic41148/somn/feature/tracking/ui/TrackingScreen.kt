package dev.vic41148.somn.feature.tracking.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.vic41148.somn.core.audio.SonarCollector
import dev.vic41148.somn.core.domain.model.TrackingMode
import dev.vic41148.somn.core.ui.components.Hypnogram
import dev.vic41148.somn.feature.tracking.SleepTrackingViewModel
import dev.vic41148.somn.feature.tracking.service.TrackingState
import kotlinx.coroutines.delay

@Composable
fun TrackingScreen(
    onTrackingStopped: (sessionId: Long) -> Unit,
    viewModel: SleepTrackingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activeSession       by viewModel.activeSession.collectAsState()
    val epochs              by viewModel.epochs.collectAsState()
    val activeMode          by viewModel.activeTrackingMode.collectAsState()
    val calibrationState    by viewModel.sonarCalibrationState.collectAsState()
    val trackingState       by viewModel.trackingState.collectAsState()
    val isSonar             = activeMode == TrackingMode.SONAR
    val isCalibrating       = isSonar && calibrationState == SonarCollector.SonarCalibrationState.CALIBRATING

    // Back must never strand a live session. Popping this screen mid-tracking dumps the user
    // back on Home with a running foreground service and no in-app way to stop it (the Home
    // moon button only starts sessions). Block system back for as long as the service is
    // actually tracking — the stop has to go through the explicit Wake Up button, same as the
    // alarm firing screen's BackHandler {}. Once tracking ends (Wake Up, or the service stops
    // via smart-alarm wake) the back stack unlocks again.
    BackHandler(enabled = trackingState == TrackingState.TRACKING) { }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Tracking indicator
        Text(
            text = if (isCalibrating) "Calibrating Sonar…" else "Tracking Your Sleep",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isCalibrating) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (isCalibrating) {
            Text(
                text = "We set the acoustic baseline (60s). Keep still…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Sonar mode — high battery usage",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        } else if (isSonar) {
            Text(
                text = "Sonar active — contactless sensing",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Sonar mode — high battery usage",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Text(
                text = "Place your phone on the bed. Then relax.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        // It is isolated in its own composable with its own 1Hz-ticking state. The per-second
        // recomposition this timer requires does not cascade into the rest of the screen
        // (hypnogram, epoch count) below it.
        ElapsedTimeText(startTimeMillis = activeSession?.startTimeMillis)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "${epochs.size} epochs recorded",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Live hypnogram (last 60 epochs = ~30 min)
        if (epochs.isNotEmpty() && !isCalibrating) {
            Text(
                text = "Live Movement",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            val liveStages = remember(epochs) { epochs.takeLast(60).map { it.stage } }
            Hypnogram(
                stages = liveStages,
                modifier = Modifier.fillMaxWidth(),
                height = 80.dp
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Stop button
        FilledTonalButton(
            onClick = {
                viewModel.stopTracking(context)
                activeSession?.let { session ->
                    onTrackingStopped(session.id)
                }
            },
            modifier = Modifier.size(width = 200.dp, height = 56.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = "Stop tracking",
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Wake Up",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ElapsedTimeText(startTimeMillis: Long?) {
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(startTimeMillis) {
        if (startTimeMillis == null) return@LaunchedEffect
        while (true) {
            elapsedSeconds = (System.currentTimeMillis() - startTimeMillis) / 1000
            delay(1000L)
        }
    }

    val hours   = elapsedSeconds / 3600
    val minutes = (elapsedSeconds % 3600) / 60
    val seconds = elapsedSeconds % 60
    Text(
        text = String.format("%02d:%02d:%02d", hours, minutes, seconds),
        style = MaterialTheme.typography.displayLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
    )
}
