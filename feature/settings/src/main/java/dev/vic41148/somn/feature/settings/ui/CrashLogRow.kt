package dev.vic41148.somn.feature.settings.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.vic41148.somn.core.data.diagnostics.CrashLogStore

/**
 * Zero-telemetry crash reporting, user-triggered: copies the latest redacted crash log to
 * the clipboard so it can be pasted into a GitHub issue. Nothing ever leaves the device
 * on its own. Lives in Settings → About, next to the licenses row.
 */
@Composable
fun CrashLogRow(context: Context) {
    var status by remember { mutableStateOf<String?>(null) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Crash log",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Somn keeps the last few crash reports on this device only. " +
                "Copy the latest one to attach it to a bug report.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            val log = CrashLogStore.readLatest(context)
            if (log == null) {
                status = "No crash reports stored."
            } else {
                val clipboard =
                    context.getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(ClipData.newPlainText("Somn crash log", log))
                status = "Latest crash report copied."
            }
        }) {
            Text("Copy latest crash report")
        }
        status?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
