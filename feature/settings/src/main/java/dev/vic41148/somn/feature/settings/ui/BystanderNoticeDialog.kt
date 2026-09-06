package dev.vic41148.somn.feature.settings.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * One-time notice when continuous-mic tracking (sonar) is chosen: the microphone captures
 * everyone in the room, and in several jurisdictions recording someone else's voice needs
 * their awareness or consent. Shown once, dismissible, never blocking.
 */
@Composable
fun BystanderNoticeDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Others in the room can be heard too") },
        text = {
            Text(
                "Sonar mode keeps the microphone on all night. It records snoring, " +
                    "sleep talk, and breathing — from anyone in the room, not just you. " +
                    "If someone shares your bed or bedroom, make sure they know " +
                    "recording is on before you track."
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it")
            }
        }
    )
}
