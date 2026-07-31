package dev.vic41148.somn.feature.settings.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import dev.vic41148.somn.core.domain.model.HealthConnectStatus
import dev.vic41148.somn.core.domain.model.TrackingMode
import dev.vic41148.somn.feature.settings.SettingsViewModel
import java.util.concurrent.Executors

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToBreathing: () -> Unit = {},
    onNavigateToCognitiveWindDown: () -> Unit = {},
    onNavigateToADHDCooldown: () -> Unit = {}
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val exportStatus by viewModel.exportStatus.collectAsState()
    val importStatus by viewModel.importStatus.collectAsState()

    // Async operation results (export/import/NAS test) used to be plain Text sitting in a long
    // scrolling layout — easy to miss, never dismissed itself, no action. Snackbars instead.
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(exportStatus) {
        exportStatus?.let { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(importStatus) {
        importStatus?.let { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(settings.nasTestResult) {
        settings.nasTestResult?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { scaffoldPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(scaffoldPadding)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Sleep Target
        SettingSection(title = "Sleep Target") {
            Text(
                text = "${String.format("%.1f", settings.targetSleepHours)} hours",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Slider(
                value = settings.targetSleepHours,
                onValueChange = { viewModel.updateSleepTarget(it) },
                valueRange = 5f..12f,
                steps = 13,
                modifier = Modifier.fillMaxWidth()
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // Wake-Up Verification (WAKE-01/02)
        SettingSection(title = "Wake-Up Verification") {
            SettingToggle(
                title = "Confirm You're Awake",
                subtitle = "After dismissing, re-rings via CAPTCHA if you don't confirm in time",
                checked = settings.wakeVerificationEnabled,
                onCheckedChange = { viewModel.updateWakeVerificationEnabled(it) }
            )
            if (settings.wakeVerificationEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${settings.wakeVerificationWindowSeconds}s to confirm",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = settings.wakeVerificationWindowSeconds.toFloat(),
                    onValueChange = { viewModel.updateWakeVerificationWindowSeconds(it.toInt()) },
                    valueRange = 5f..60f,
                    steps = 10,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // Sensor Mode
        SettingSection(title = "Sensor Mode") {
            Text(
                text = settings.sensorMode,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Accelerometer — phone on bed, low battery usage",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // Tracking Mode selector
        SettingSection(title = "Movement Tracking Mode") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Accelerometer button
                Button(
                    onClick = { viewModel.updateTrackingMode(TrackingMode.ACCELEROMETER) },
                    modifier = Modifier.weight(1f),
                    colors = if (settings.trackingMode == TrackingMode.ACCELEROMETER)
                        ButtonDefaults.buttonColors()
                    else
                        ButtonDefaults.outlinedButtonColors()
                ) {
                    Text("Accelerometer", style = MaterialTheme.typography.labelMedium)
                }
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                // Sonar button
                Button(
                    onClick = { viewModel.updateTrackingMode(TrackingMode.SONAR) },
                    modifier = Modifier.weight(1f),
                    colors = if (settings.trackingMode == TrackingMode.SONAR)
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    else
                        ButtonDefaults.outlinedButtonColors()
                ) {
                    Text("Sonar (Beta)", style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (settings.trackingMode == TrackingMode.ACCELEROMETER) {
                Text(
                    text = "Accelerometer — phone on bed, low battery usage.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "⚡ Sonar (Beta) — contactless, phone on nightstand. "
                        + "Uses speaker + mic continuously. Significantly higher battery drain.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "⚠ Pets and partners moving may affect accuracy. "
                        + "Test on physical device only.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        SettingToggle(
            title = "Auto Do Not Disturb",
            subtitle = "Enable DND when tracking starts, disable on alarm",
            checked = settings.dndEnabled,
            onCheckedChange = { viewModel.updateDndEnabled(it) }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // Battery Threshold
        SettingSection(title = "Battery Threshold") {
            Text(
                text = "${settings.batteryThreshold}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Tracking enters standby mode below this level",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = settings.batteryThreshold.toFloat(),
                onValueChange = { viewModel.updateBatteryThreshold(it.toInt()) },
                valueRange = 5f..30f,
                steps = 4,
                modifier = Modifier.fillMaxWidth()
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // Oversleep Threshold (SESS-03)
        SettingSection(title = "Oversleep Threshold") {
            val hours = settings.oversleepThresholdMinutes / 60
            val mins = settings.oversleepThresholdMinutes % 60
            Text(
                text = if (hours > 0) "${hours}h ${mins}m past target" else "${mins}m past target",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Flag a session as oversleep once it runs this far beyond your target sleep hours",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = settings.oversleepThresholdMinutes.toFloat(),
                onValueChange = { viewModel.updateOversleepThresholdMinutes(it.toInt()) },
                valueRange = 30f..180f,
                steps = 4,
                modifier = Modifier.fillMaxWidth()
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // Alarm CAPTCHA
        SettingSection(title = "Alarm CAPTCHA") {
            var showQrSetup by remember { mutableStateOf(false) }
            val hasCameraPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) showQrSetup = true
            }

            Text(
                text = "Require a task to dismiss the morning alarm.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            val tasks = listOf(
                "math" to "Basic Math",
                "shake" to "Physical Shake",
                "sequence" to "Typed Sequence",
                "qrcode" to "QR Code Scan",
                "nfc" to "NFC Tag Tap"
            )

            tasks.forEach { (id, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.updateCaptchaTask(id) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = settings.selectedCaptchaTaskId == id,
                        onClick = { viewModel.updateCaptchaTask(id) }
                    )
                    Text(text = label, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (settings.selectedCaptchaTaskId == "qrcode") {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (hasCameraPermission) showQrSetup = true
                        else launcher.launch(Manifest.permission.CAMERA)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors()
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Text(
                        text = if (settings.qrCodeValue == null) "  Setup QR Code" else "  Update QR Code"
                    )
                }
                if (settings.qrCodeValue != null) {
                    Text(
                        text = "QR Code configured ✅",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            if (showQrSetup) {
                QRSetupDialog(
                    onDismiss = { showQrSetup = false },
                    onScanSuccess = {
                        viewModel.updateQRCodeValue(it)
                        showQrSetup = false
                    }
                )
            }
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        SettingSection(title = "Data Export & Backup") {
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                if (uri != null) {
                    val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                    viewModel.updateBackupUri(uri.toString())
                }
            }

            Text(
                text = "Backup Directory",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = { launcher.launch(null) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors()
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Text(if (settings.backupUri != null) "  Change Directory" else "  Select Backup Directory")
            }
            if (settings.backupUri != null) {
                Text(
                    text = "Auto-backup enabled on alarm dismiss ✅",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Button(
                    onClick = { viewModel.performManualBackup() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Backup Now")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Recovery Key",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (settings.backupPassphraseSet) {
                    "Backups are encrypted with your recovery key ✅"
                } else {
                    "No recovery key set. Backups stay on this device only — off-site sync is " +
                        "disabled, because an upload this phone can't outlive isn't a backup."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (settings.backupPassphraseSet) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )

            var confirmReplaceKey by remember { mutableStateOf(false) }
            Button(
                onClick = {
                    // Replacing a key orphans every backup written under the old one, so make the
                    // destructive case an explicit second step.
                    if (settings.backupPassphraseSet) confirmReplaceKey = true
                    else viewModel.generateRecoveryKey()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors()
            ) {
                Text(if (settings.backupPassphraseSet) "Replace Recovery Key" else "Generate Recovery Key")
            }

            if (confirmReplaceKey) {
                AlertDialog(
                    onDismissRequest = { confirmReplaceKey = false },
                    title = { Text("Replace recovery key?") },
                    text = {
                        Text(
                            "Backups already written can only be opened with the current key. " +
                                "Keep it somewhere safe, or you'll lose access to them."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmReplaceKey = false
                            viewModel.generateRecoveryKey()
                        }) { Text("Replace") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmReplaceKey = false }) { Text("Cancel") }
                    }
                )
            }

            val newRecoveryKey by viewModel.newRecoveryKey.collectAsState()
            newRecoveryKey?.let { key ->
                AlertDialog(
                    onDismissRequest = { viewModel.dismissRecoveryKey() },
                    title = { Text("Save your recovery key") },
                    text = {
                        Column {
                            Text(
                                "This is shown once. Without it, an encrypted backup cannot be " +
                                    "opened — not by you, and not by us."
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            SelectionContainer {
                                Text(
                                    text = key,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.dismissRecoveryKey() }) {
                            Text("I've saved it")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
            val restoreLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri -> if (uri != null) pendingRestoreUri = uri }

            Text(
                text = "Restore",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors()
            ) {
                Text("Restore From Backup File")
            }

            pendingRestoreUri?.let { uri ->
                var passphrase by remember(uri) { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { pendingRestoreUri = null },
                    title = { Text("Restore database?") },
                    text = {
                        Column {
                            Text(
                                "This replaces all sleep data on this device. Enter your recovery " +
                                    "key if the backup is encrypted; leave it blank if not."
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = passphrase,
                                onValueChange = { passphrase = it },
                                label = { Text("Recovery key") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation()
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.restoreDatabase(uri, passphrase.ifBlank { null })
                            pendingRestoreUri = null
                        }) { Text("Restore") }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingRestoreUri = null }) { Text("Cancel") }
                    }
                )
            }

            val restartRequired by viewModel.restartRequired.collectAsState()
            if (restartRequired) {
                Text(
                    text = "Restore complete — fully close and reopen Somn to load the restored data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { viewModel.exportData(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Text("  Export All Data (CSV)")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.exportAllDataZip(context) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors()
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Text("  Export All Data (JSON + CSV, .zip)")
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Import from Sleep as Android",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Best-effort — Sleep as Android's export format isn't officially documented, " +
                    "so cycle/biological context, sleep stages, and audio events can't carry over.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))

            val importLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri ->
                if (uri != null) {
                    viewModel.importSleepAsAndroidFile(context, uri)
                }
            }
            Button(
                onClick = { importLauncher.launch("text/*") },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors()
            ) {
                Text("Select sleep-export.csv")
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // NAS / Self-Hosted Backup
        SettingSection(title = "NAS Sync (Self-Hosted)") {
            SettingToggle(
                title = "Enable NAS Sync",
                subtitle = "Encrypt & upload clips + DB to your NAS",
                checked = settings.nasEnabled,
                onCheckedChange = { viewModel.updateNasEnabled(it) }
            )

            if (settings.nasEnabled) {
                Spacer(modifier = Modifier.height(12.dp))

                // Protocol — WebDAV-only for now (REL-05: SMB/NFS are unimplemented stubs
                // in NasClientImpl, gated out of the picker until a real client backs them).
                Text(
                    text = "Protocol",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "WebDAV",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "SMB and NFS support are planned for a future release.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Host
                OutlinedTextField(
                    value = settings.nasHost,
                    onValueChange = { viewModel.updateNasHost(it) },
                    label = { Text("Host / IP") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Port + Path row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = settings.nasPort.toString(),
                        onValueChange = { viewModel.updateNasPort(it.toIntOrNull() ?: 80) },
                        label = { Text("Port") },
                        singleLine = true,
                        modifier = Modifier.weight(0.3f)
                    )
                    OutlinedTextField(
                        value = settings.nasPath,
                        onValueChange = { viewModel.updateNasPath(it) },
                        label = { Text("Path") },
                        singleLine = true,
                        modifier = Modifier.weight(0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Username
                OutlinedTextField(
                    value = settings.nasUsername,
                    onValueChange = { viewModel.updateNasUsername(it) },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Password — write-only: encrypted via Android Keystore, never read back (REL-06)
                var nasPasswordInput by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = nasPasswordInput,
                    onValueChange = {
                        nasPasswordInput = it
                        viewModel.updateNasPassword(it)
                    },
                    label = { Text("Password") },
                    placeholder = { Text("Leave blank to keep existing") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Test + Sync buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.testNasConnection() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text("Test Connection")
                    }
                    Button(
                        onClick = { viewModel.triggerNasSync(context) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Sync Now")
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "🔒 All uploads AES-256-GCM encrypted via Android Keystore",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // Health Connect (HEALTH-01..04)
        SettingSection(title = "Health Connect") {
            val healthConnectContract = remember(viewModel) { viewModel.healthConnectPermissionsContract() }
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = healthConnectContract
            ) {
                viewModel.refreshHealthConnectStatus()
            }

            SettingToggle(
                title = "Sync with Health Connect",
                subtitle = "Read HR/HRV/SpO2/skin temp from wearables, write completed sessions back",
                checked = settings.healthConnectEnabled,
                onCheckedChange = { viewModel.updateHealthConnectEnabled(it) }
            )

            if (settings.healthConnectEnabled) {
                Spacer(modifier = Modifier.height(8.dp))

                val (statusText, statusColor) = when (settings.healthConnectStatus) {
                    HealthConnectStatus.AUTHORIZED -> "Connected ✅" to MaterialTheme.colorScheme.primary
                    HealthConnectStatus.NOT_AUTHORIZED -> "Not authorized" to MaterialTheme.colorScheme.error
                    HealthConnectStatus.UNAVAILABLE -> "Health Connect isn't installed on this device" to MaterialTheme.colorScheme.error
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor
                )

                if (settings.healthConnectStatus == HealthConnectStatus.NOT_AUTHORIZED) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { permissionLauncher.launch(viewModel.healthConnectRequiredPermissions) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Connect Health Connect")
                    }
                }

                // HEALTH-04: writeSleepSession() silently skips a session whenever another source
                // already wrote overlapping sleep data (dedup), and that skip is permanent — the
                // session's healthConnectRecordId stays null forever, so it'd otherwise never be
                // surfaced anywhere. This count also includes sessions simply not synced yet, so
                // it's worded as "haven't reached" rather than claiming they were all dedup-skipped.
                if (settings.healthConnectStatus == HealthConnectStatus.AUTHORIZED && settings.healthConnectUnsyncedCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${settings.healthConnectUnsyncedCount} session(s) haven't reached Health Connect yet — " +
                            "either not synced, or another app already recorded overlapping sleep for that night.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // Experimental: YAMNet audio classification (Task 14, AUDIO-01)
        SettingSection(title = "Experimental") {
            SettingToggle(
                title = "ML Audio Classification (YAMNet)",
                subtitle = "Use an on-device ML model instead of the heuristic to detect snoring/coughing/talking. Not yet validated for accuracy — takes effect next tracking session.",
                checked = settings.yamnetClassificationEnabled,
                onCheckedChange = { viewModel.updateYamnetClassificationEnabled(it) }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // Wind-Down Toolkit
        SettingSection(title = "Wind-Down Toolkit") {
            Button(
                onClick = onNavigateToBreathing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🫁  Breathing Exercise (4-7-8)")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onNavigateToCognitiveWindDown,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("📝  Cognitive Dump")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onNavigateToADHDCooldown,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🧠  ADHD Cooldown")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // About
        Text(
            text = "Somn v1.0.0",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Open source • Privacy first • No subscriptions",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    }
}

@Composable
private fun QRSetupDialog(
    onDismiss: () -> Unit,
    onScanSuccess: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().height(450.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Setup QR Code",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Scan the QR code you want to use for alarm dismissal. Scan something far from your bed!",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 8.dp)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val executor = Executors.newSingleThreadExecutor()
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().apply {
                                    surfaceProvider = previewView.surfaceProvider
                                }

                                val scanner = BarcodeScanning.getClient()
                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()

                                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                                    val mediaImage = imageProxy.image
                                    if (mediaImage != null) {
                                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                        scanner.process(image)
                                            .addOnSuccessListener { barcodes ->
                                                for (barcode in barcodes) {
                                                    val rawValue = barcode.rawValue
                                                    if (rawValue != null) {
                                                        onScanSuccess(rawValue)
                                                    }
                                                }
                                            }
                                            .addOnCompleteListener { imageProxy.close() }
                                    } else {
                                        imageProxy.close()
                                    }
                                }

                                try {
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        imageAnalysis
                                    )
                                } catch (e: Exception) {
                                    Log.e("QRSetup", "Camera binding failed", e)
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun SettingSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // HapticFeedbackType only has LongPress/TextHandleMove in this Compose UI version
        // (1.7.6, confirmed against the actual resolved jar) — no dedicated Toggle constant yet.
        val haptics = LocalHapticFeedback.current
        Switch(
            checked = checked,
            onCheckedChange = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onCheckedChange(it)
            }
        )
    }
}
