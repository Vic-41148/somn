package dev.vic41148.somn.feature.settings.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.vic41148.somn.core.ui.haptic.LocalHaptics
import dev.vic41148.somn.core.domain.model.StagedRelease
import dev.vic41148.somn.feature.settings.ui.SettingSection
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Updates section in Settings: master switch, check interval, current staged release incl. the
 * backup-first update button, a manual Check now, and the entry point to full version history.
 */
@Composable
fun UpdatesSection(
    onNavigateToUpdates: () -> Unit,
    onNavigateToBackupSetup: () -> Unit,
    viewModel: UpdatesViewModel = hiltViewModel()
) {
    val autoCheck by viewModel.autoCheck.collectAsState()
    val intervalDays by viewModel.intervalDays.collectAsState()
    val lastChecked by viewModel.lastChecked.collectAsState()
    val staged by viewModel.staged.collectAsState()
    val phase by viewModel.phase.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val showInterstitial by viewModel.showBackupInterstitial.collectAsState()
    val haptics = LocalHaptics.current

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatus()
        }
    }
    LaunchedEffect(phase) {
        if (phase is UpdatesViewModel.Phase.Error) {
            snackbarHostState.showSnackbar((phase as UpdatesViewModel.Phase.Error).message)
        }
    }

    // A manual check is enqueued via WorkManager; "last checked" advancing is our completion signal.
    // A failed check never advances lastChecked, so a hard cap prevents the button staying
    // disabled forever.
    var checking by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(lastChecked) {
        if (checking && lastChecked != null) {
            checking = false
        }
    }
    LaunchedEffect(checking) {
        if (checking) {
            delay(30_000)
            checking = false
        }
    }

    // An Error phase is terminal and retryable - the Update / Check buttons stay usable after it.
    val phaseCanTakeWork = phase is UpdatesViewModel.Phase.Idle || phase is UpdatesViewModel.Phase.Error
    val checkingEnabled = phaseCanTakeWork && !checking

    SettingSection(title = "Updates") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Check for updates automatically",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = autoCheck,
                onCheckedChange = {
                    haptics.tick()
                    viewModel.setAutoCheck(it)
                }
            )
        }

        if (autoCheck) {
            Spacer(modifier = Modifier.height(8.dp))
            val options = listOf(1 to "Daily", 7 to "Weekly")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, (days, label) ->
                    SegmentedButton(
                        selected = intervalDays == days,
                        onClick = {
                            haptics.tick()
                            viewModel.setIntervalDays(days)
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = options.size
                        ),
                        label = { Text(label) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (staged.isPresent) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Somn ${staged.versionName} is ready to install",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (phase is UpdatesViewModel.Phase.BackingUp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Backing up your data first...")
                    }
                }
                if (phase is UpdatesViewModel.Phase.Downloading) {
                    val pair = progress
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        progress = {
                            if (pair != null && pair.second > 0L) {
                                pair.first.toFloat() / pair.second.toFloat()
                            } else {
                                0f
                            }
                        }
                    )
                    Text("Downloading + verifying...", style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = {
                        haptics.tick()
                        viewModel.requestUpdate(staged)
                    },
                    enabled = phaseCanTakeWork,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Backup then Update")
                }
                TextButton(
                    onClick = { viewModel.skip(staged) },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Not now")
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (checking) "Checking for updates..." else "You're up to date",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (checking) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                }
            }
            if (lastChecked != null) {
                Text(
                    text = "Last checked ${formatTimestamp(lastChecked)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = {
                haptics.tick()
                checking = true
                viewModel.checkNow()
            },
            enabled = checkingEnabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Check for updates now")
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Version history",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = { haptics.tick(); onNavigateToUpdates() }) {
                Text("Open")
            }
        }
    }

    if (showInterstitial) {
        AlertDialog(
            onDismissRequest = { viewModel.onBackupInterstitialContinue() },
            title = { Text("Back up your data first") },
            text = {
                Text(
                    "Updating wipes nothing, but if the new version has a problem you'll want " +
                        "your data safe before you tap Update. You haven't configured NAS Sync or a " +
                        "recovery passphrase yet. A backup will still be created automatically either " +
                        "way."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onBackupInterstitialSetup(onNavigateToBackupSetup) }) {
                    Text("Set Up Backup")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onBackupInterstitialContinue() }) {
                    Text("Continue Anyway")
                }
            }
        )
    }

    SnackbarHost(hostState = snackbarHostState)
}

internal fun formatTimestamp(ms: Long?): String {
    if (ms == null) return "never"
    return SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(ms))
}