package dev.vic41148.somn.feature.onboarding.ui

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

data class PermissionItem(
    val permission: String,
    val title: String,
    val description: String,
    val required: Boolean
)

@Composable
fun PermissionsScreen(
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val permissions = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(
                    PermissionItem(
                        Manifest.permission.POST_NOTIFICATIONS,
                        "Notifications",
                        "Bedtime reminders, morning briefings, and sleep insights",
                        required = true
                    )
                )
            }
            // Motion sensing uses the raw TYPE_ACCELEROMETER sensor (AccelerometerCollector),
            // which needs no runtime permission — Activity Recognition and its permission are
            // unused. Requesting it here was also dead on arrival: it was never declared in
            // AndroidManifest.xml, so the dialog could never actually grant it.
            //
            // Exact alarms are scheduled via AlarmManager.setAlarmClock() (AlarmReceiver),
            // which is exempt from exact-alarm restrictions on every Android version without
            // needing SCHEDULE_EXACT_ALARM at all. That permission is also a special-access
            // grant on API 31+ that a RequestMultiplePermissions dialog cannot obtain in the
            // first place — only a dedicated Settings deep-link can, which is why this always
            // showed as "not granted" regardless of what was tapped.
            add(
                PermissionItem(
                    Manifest.permission.RECORD_AUDIO,
                    "Microphone (optional)",
                    "Snoring detection, sleep talk recording, and breathing analysis",
                    required = false
                )
            )
        }
    }

    val grantedPermissions = remember { mutableStateMapOf<String, Boolean>() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        results.forEach { (permission, granted) ->
            grantedPermissions[permission] = granted
        }
    }

    // Body scrolls, footer stays pinned. This used to be one unscrollable Column whose Continue
    // button was held down by a weight(1f) Spacer — fine until the content above outgrew the
    // viewport (a large system font scale, or enough permission rows on a newer SDK), at which
    // point the spacer collapsed to zero and the button was pushed off the bottom of the screen
    // with no way to scroll to it.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Permissions",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "We need a few permissions to track your sleep accurately. " +
                "All processing happens on your device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        permissions.forEach { item ->
            val isGranted = grantedPermissions[item.permission] == true
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    // Unlike the other icons flagged in the audit, this one isn't decorative —
                    // it's the only indicator of grant status. item.title/description never say
                    // whether the permission was actually granted, so a screen reader user had no
                    // way to tell which permissions still needed granting.
                    contentDescription = if (isGranted) "${item.title} granted" else "${item.title} not granted",
                    tint = if (isGranted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = {
                permissionLauncher.launch(permissions.map { it.permission }.toTypedArray())
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Grant Permissions")
        }

            Spacer(modifier = Modifier.height(24.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Button(onClick = onNext) {
                Text("Continue")
            }
        }
    }
}
