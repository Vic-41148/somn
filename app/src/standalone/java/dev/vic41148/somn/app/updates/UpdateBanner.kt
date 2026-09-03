package dev.vic41148.somn.app.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Dismissible card shown under the Home header when a newer release was found. Update runs the full
 * guarded flow (backup first, checksum-verified download, system installer); "Not now" hides the
 * card for this session.
 */
@Composable
fun UpdateBanner(
    onOpenUpdates: () -> Unit,
    onGoToBackup: () -> Unit = onOpenUpdates,
    viewModel: UpdateBannerViewModel = hiltViewModel()
) {
    val staged by viewModel.staged.collectAsState()
    val visible by viewModel.visible.collectAsState()
    val phase by viewModel.phase.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val error by viewModel.error.collectAsState()
    val showInterstitial by viewModel.showBackupInterstitial.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    if (visible && staged.isPresent) {
        OutlinedCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "Somn ${staged.versionName} is available",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = staged.notes.lineSequence().take(2).joinToString(" ")
                        .take(140)
                        .ifEmpty { "A new version of Somn is ready." },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                when (phase) {
                    UpdateBannerViewModel.DownloadPhase.BackingUp -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Backing up your data first...", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    UpdateBannerViewModel.DownloadPhase.Downloading -> {
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

                    UpdateBannerViewModel.DownloadPhase.Idle -> {}
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.requestUpdate(staged) },
                        enabled = phase == UpdateBannerViewModel.DownloadPhase.Idle,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Backup then Update")
                    }
                    TextButton(onClick = { viewModel.dismiss() }) {
                        Text("Not now")
                    }
                }
            }
        }
    }

    if (showInterstitial) {
        AlertDialog(
            onDismissRequest = { viewModel.onInterstitialContinue() },
            title = { Text("Back up your data first") },
            text = {
                Text(
                    "You haven't configured NAS Sync or a recovery passphrase. A backup will " +
                        "still be created automatically before the update."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onInterstitialSetup(); onGoToBackup() }) {
                    Text("Set Up Backup")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onInterstitialContinue() }) {
                    Text("Continue Anyway")
                }
            }
        )
    }

    SnackbarHost(hostState = snackbarHostState)
}