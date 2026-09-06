package dev.vic41148.somn.app.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.vic41148.somn.core.ui.theme.SomnTheme

/**
 * Health Connect permission rationale, shown from inside Health Connect's own permission
 * screen (pre-Android-14 `ACTION_SHOW_PERMISSIONS_RATIONALE`). On 14+ the
 * `HealthConnectRationaleAlias` covers the framework path; this covers Health Connect as
 * a separate app. Reads the same story as the in-app toggle: optional, on-device, pausable.
 */
class HealthRationaleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SomnTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "Why Somn asks for health data",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Somn reads sleep and vitals from Health Connect only to enrich " +
                            "your sleep history — heart rate, HRV, SpO2, and skin temperature " +
                            "next to your tracked nights. Nothing leaves your phone: there is " +
                            "no account, no analytics, no cloud. You can pause sync any time " +
                            "in Settings without revoking the system permission.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { finish() }) {
                        Text("Done")
                    }
                }
            }
        }
    }
}
