package dev.vic41148.somn.feature.settings.updates

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.vic41148.somn.core.domain.model.ReleaseInfo

/**
 * Full-screen release history. Lists every published release newest-first, highlights the installed
 * version, and offers a guarded downgrade for older releases: backup first, browser download, then
 * uninstall, with the next-launch restore prompt picking up where the downgrade leaves off.
 */
@Composable
fun UpdatesScreen(
    onBack: () -> Unit,
    viewModel: UpdatesViewModel = hiltViewModel()
) {
    val history by viewModel.history.collectAsState()
    val currentVersionName by viewModel.currentVersionName.collectAsState()
    val historyLoading by viewModel.historyLoading.collectAsState()
    val historyError by viewModel.historyError.collectAsState()
    val staged by viewModel.staged.collectAsState()
    val phase by viewModel.phase.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val downgradeCandidate by viewModel.downgradeCandidate.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadHistory() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Version History",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = "Installed: Somn ${currentVersionName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (staged.isPresent) {
            OutlinedCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Somn ${staged.versionName} is ready to install",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
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
                    }
                    Button(
                        onClick = { viewModel.requestUpdate(staged) },
                        enabled = phase is UpdatesViewModel.Phase.Idle,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Backup then Update")
                    }
                }
            }
        }

        // Version list
        when {
            historyLoading && history.isEmpty() -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Loading releases...")
                }
            }

            historyError != null && history.isEmpty() -> {
                Text(
                    text = historyError ?: "Could not load release history.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(onClick = { viewModel.loadHistory() }) { Text("Retry") }
            }

            else -> {
                for (release in history) {
                    ReleaseHistoryRow(
                        release = release,
                        isCurrent = release.versionName == currentVersionName,
                        isStaged = release.tag == staged.tag,
                        isDisabled = phase is UpdatesViewModel.Phase.DowngradePreparing,
                        onDowngrade = { viewModel.requestDowngrade(release) }
                    )
                }
            }
        }
    }

    downgradeCandidate?.let { release ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDowngrade() },
            title = { Text("Downgrade to Somn ${release.versionName}?") },
            text = {
                Text(
                    "Android won't install an older version over a newer one. You'll download " +
                        "${release.versionName}, remove this install, then reinstall from the " +
                        "downloaded file. Your data is backed up first, and we'll offer to restore " +
                        "it on the next launch."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDowngrade(release) }) {
                    Text("Download & Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDowngrade() }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ReleaseHistoryRow(
    release: ReleaseInfo,
    isCurrent: Boolean,
    isStaged: Boolean,
    isDisabled: Boolean,
    onDowngrade: () -> Unit
) {
    OutlinedCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = release.versionName + if (release.isPrerelease) " (pre-release)" else "",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = when {
                        isCurrent -> "Installed"
                        isStaged -> "Update ready"
                        else -> release.publishedAt ?: release.tag
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isCurrent && (release.apkUrl != null)) {
                TextButton(
                    onClick = onDowngrade,
                    enabled = !isDisabled
                ) {
                    Text("Downgrade")
                }
            }
        }
    }
}