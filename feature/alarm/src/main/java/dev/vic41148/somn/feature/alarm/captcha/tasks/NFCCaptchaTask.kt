package dev.vic41148.somn.feature.alarm.captcha.tasks

import android.content.Context
import android.nfc.NfcAdapter
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.vic41148.somn.feature.alarm.captcha.CaptchaTask

class NFCCaptchaTask : CaptchaTask {
    override val id: String = "nfc"
    override val displayName: String = "NFC Tag Tap"
    
    private var isSolved by mutableStateOf(false)

    override fun isComplete(): Boolean = isSolved

    override fun reset() {
        isSolved = false
    }

    /**
     * Should be called by the Activity when an NFC tag is discovered.
     */
    fun onTagDiscovered() {
        isSolved = true
    }

    @Composable
    override fun TaskUI(onComplete: () -> Unit) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Nfc,
                contentDescription = null,
                modifier = Modifier.size(100.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Tap your NFC tag to dismiss the alarm",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Place the back of your phone against the tag.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isSolved) {
                LaunchedEffect(Unit) {
                    onComplete()
                }
            }
        }
    }
}
