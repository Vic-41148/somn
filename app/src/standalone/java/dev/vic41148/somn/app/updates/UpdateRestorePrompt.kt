package dev.vic41148.somn.app.updates

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * First-launch dialog after an update/reinstall: if the database is empty and a pre-update backup
 * exists in user-visible storage, offer to restore it. "Restore" replays the CSV through the
 * standard import; "Not now" records the answer so it never nags again on this install.
 */
@Composable
fun UpdateRestorePrompt(
    modifier: Modifier = Modifier,
    viewModel: UpdateRestoreViewModel = hiltViewModel()
) {
    val offer by viewModel.offer.collectAsState()
    val restoring by viewModel.restoring.collectAsState()
    val resultMessage by viewModel.resultMessage.collectAsState()

    LaunchedEffect(Unit) { viewModel.checkForBackup() }

    val currentOffer = offer
    if (currentOffer != null) {
        AlertDialog(
            onDismissRequest = { viewModel.decline() },
            title = { Text("Restore your data?") },
            text = {
                if (restoring) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Text(
                        "We found a backup made before your last update (${currentOffer.name}). " +
                            "Restore it into the fresh install?"
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.restore(currentOffer) },
                    enabled = !restoring
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.decline() }, enabled = !restoring) {
                    Text("Not now")
                }
            }
        )
    }

    resultMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissResult() },
            title = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissResult() }) { Text("OK") }
            }
        )
    }
}