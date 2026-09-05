package dev.vic41148.somn.feature.settings.ui

import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import zxingcpp.BarcodeReader
import dev.vic41148.somn.core.domain.haptic.HapticsIntensity
import dev.vic41148.somn.core.domain.model.HealthConnectStatus
import dev.vic41148.somn.core.domain.model.HemisphereOverride
import dev.vic41148.somn.core.domain.model.TrackingMode
import dev.vic41148.somn.core.ui.haptic.LocalHaptics
import dev.vic41148.somn.feature.settings.SettingsViewModel
import dev.vic41148.somn.feature.settings.updates.UpdatesSection
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToWindDownToolkit: () -> Unit = {},
    onNavigateToDataExport: () -> Unit = {},
    onNavigateToUpdates: () -> Unit = {},
    onNavigateToBreathing: () -> Unit = {},
    onNavigateToCognitiveWindDown: () -> Unit = {},
    onNavigateToADHDCooldown: () -> Unit = {},
    onNavigateToMenoSurvey: () -> Unit = {}
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val profile by viewModel.userProfile.collectAsState()
    val menoAnswers by viewModel.menoAnswers.collectAsState()
    val exportStatus by viewModel.exportStatus.collectAsState()
    val importStatus by viewModel.importStatus.collectAsState()
    val clipDeletionStatus by viewModel.clipDeletionStatus.collectAsState()
    val haptics = LocalHaptics.current

    // The raw-vibrator path bypasses the system's automatic touch-feedback gating, so when the
    // OS-level toggle is off the user should know why some effects feel muted rather than wonder.
    val systemTouchFeedbackEnabled = remember {
        Settings.System.getInt(
            context.contentResolver,
            Settings.System.HAPTIC_FEEDBACK_ENABLED,
            1
        ) != 0
    }

    // Async operation results (export/import/NAS test) used to be plain Text sitting in a long
    // scrolling layout - easy to miss, never dismissed itself, no action. Snackbars instead.
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
    LaunchedEffect(clipDeletionStatus) {
        clipDeletionStatus?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearClipDeletionStatus()
        }
    }

    Scaffold(
        // This screen sits inside the app-level Scaffold's NavHost, which already consumed the
        // system-bar insets (innerPadding + imePadding). Re-applying them here double-pads the top
        // (~90px) and pushes the whole screen down; see Notes in SleepNavGraph.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { scaffoldPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(scaffoldPadding)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // Sleep Target
        SettingSection(title = "Sleep Target") {
            SliderWithValueLabel(
                value = settings.targetSleepHours,
                onValueChange = { viewModel.updateSleepTarget(it) },
                valueRange = 5f..12f,
                steps = 13,
                valueLabel = String.format("%.1f hours", settings.targetSleepHours),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // R2 Rest Mode — sick/injured nights stop counting: streak freezes, sick
        // nights leave baselines and correlations, Outlook switches to recovery copy.
        SettingSection(title = "Recovery") {
            SettingToggle(
                title = "Rest Mode",
                checked = settings.restModeSince != null,
                onCheckedChange = { viewModel.setRestMode(it) }
            )
            Text(
                text = if (settings.restModeSince != null) {
                    "On — nights logged now won't move your streak or baselines. " +
                        "Turn it off when you're back and counting resumes."
                } else {
                    "Turn on while sick or injured so bad nights don't poison " +
                        "your streak, baselines, or correlations."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // R5: menopause check-in lives here so the peri/meno stages Somn already
        // models get Oura's questionnaire mechanic as pure UI over prefs.
        if (profile?.showMenopauseFeatures == true) {
            SettingSection(title = "Cycle & hormones") {
                Text(
                    text = "Menopause check-in: 10 questions on how symptoms have " +
                        "bothered you the last 2 weeks, with an honest read at the end.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onNavigateToMenoSurvey) {
                    Text(
                        if (menoAnswers != null) "Review check-in"
                        else "Start check-in"
                    )
                }
            }
        }

        // Haptics - app-wide feedback master switch with intensity, a live preview so the user can
        // feel the current combo without triggering a real event, and a note when the system-level
        // toggle would mute some effects. Near the top because it affects the whole app, not a
        // single feature.
        SettingSection(title = "Haptics") {
            SettingToggle(
                title = "Haptic Feedback",
                checked = settings.hapticsEnabled,
                onCheckedChange = { viewModel.updateHapticsEnabled(it) }
            )
            Text(
                text = "Feel a tap for toggles, confirmations, and completed background tasks.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!systemTouchFeedbackEnabled && settings.hapticsEnabled) {
                Text(
                    text = "System touch feedback is off - some effects may be muted. " +
                        "Fix in phone settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (settings.hapticsEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Intensity",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                val intensityOptions = HapticsIntensity.entries
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    intensityOptions.forEachIndexed { index, intensity ->
                        SegmentedButton(
                            selected = settings.hapticsIntensity == intensity,
                            onClick = {
                                haptics.tick()
                                viewModel.updateHapticsIntensity(intensity)
                            },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = intensityOptions.size
                            ),
                            label = { Text(intensity.label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { haptics.preview() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Tap to feel it")
                }
            }
        }

        // Updates (OTA)
        UpdatesSection(
            onNavigateToUpdates = onNavigateToUpdates,
            onNavigateToBackupSetup = onNavigateToDataExport
        )

        // Appearance (THEME-01)
        SettingSection(title = "Appearance") {
            SettingToggle(
                title = "Match My Wallpaper",
                checked = settings.useDynamicColor,
                onCheckedChange = { viewModel.updateUseDynamicColor(it) }
            )
            SettingToggle(
                title = "Morning Ready Card",
                checked = settings.showReadinessCard,
                onCheckedChange = { viewModel.updateShowReadinessCard(it) }
            )
        }

        // Wake-Up Verification (WAKE-01/02)
        SettingSection(title = "Wake-Up Verification") {
            SettingToggle(
                title = "Confirm You're Awake",
                checked = settings.wakeVerificationEnabled,
                onCheckedChange = { viewModel.updateWakeVerificationEnabled(it) }
            )
            if (settings.wakeVerificationEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                SliderWithValueLabel(
                    value = settings.wakeVerificationWindowSeconds.toFloat(),
                    onValueChange = { viewModel.updateWakeVerificationWindowSeconds(it.toInt()) },
                    valueRange = 5f..60f,
                    steps = 10,
                    valueLabel = "${settings.wakeVerificationWindowSeconds}s to confirm",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Anti-Snore Nudge
        SettingSection(title = "Anti-Snore Nudge") {
            SettingToggle(
                title = "Vibrate on Snoring",
                checked = settings.snoreNudgeEnabled,
                onCheckedChange = { viewModel.updateSnoreNudgeEnabled(it) }
            )
        }

        // Sleep-talk recording retention. These clips are the most sensitive thing the app
        // stores, so the retention window is surfaced here rather than buried in a backup screen.
        SettingSection(title = "Sleep-Talk Recordings") {
            val retentionDays = settings.clipRetentionDays
            Text(
                text = "Somn saves a short audio clip when it detects you talking in your sleep. " +
                    "Set this to \"keep forever\" only if you want them to stay on the device indefinitely.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            SliderWithValueLabel(
                value = retentionDays.toFloat(),
                onValueChange = { viewModel.updateClipRetentionDays(it.toInt()) },
                // 0 is the "keep forever" sentinel, so the slider's floor doubles as the opt-out.
                valueRange = 0f..30f,
                steps = 29,
                valueLabel = if (retentionDays <= 0) "Kept until you delete them" else
                    "Deleted after $retentionDays day${if (retentionDays == 1) "" else "s"}",
                modifier = Modifier.fillMaxWidth()
            )

            var confirmingClipDeletion by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = { confirmingClipDeletion = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Delete all recordings now")
            }

            if (confirmingClipDeletion) {
                AlertDialog(
                    onDismissRequest = { confirmingClipDeletion = false },
                    title = { Text("Delete all recordings?") },
                    text = {
                        Text(
                            "Every sleep-talk audio clip on this device will be permanently " +
                                "deleted. Your sleep history and the events themselves are kept - " +
                                "only the audio goes. This cannot be undone."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmingClipDeletion = false
                            viewModel.deleteAllAudioClips()
                        }) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmingClipDeletion = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }

        // R2 per-category purge — Oura-style selective deletion without an account to
        // delete. Each category confirms separately; each reports through the shared
        // deletion status snackbar.
        SettingSection(title = "Delete Data") {
            var confirmingHabitPurge by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = { confirmingHabitPurge = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear all habit logs")
            }
            if (confirmingHabitPurge) {
                AlertDialog(
                    onDismissRequest = { confirmingHabitPurge = false },
                    title = { Text("Clear habit logs?") },
                    text = {
                        Text(
                            "Every caffeine, alcohol, exercise, stress and medication " +
                                "entry will be permanently deleted. Sleep sessions are kept. " +
                                "This cannot be undone."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmingHabitPurge = false
                            viewModel.purgeHabitLogs()
                        }) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmingHabitPurge = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            var confirmingOldSessionPurge by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = { confirmingOldSessionPurge = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Delete sessions older than 90 days")
            }
            if (confirmingOldSessionPurge) {
                AlertDialog(
                    onDismissRequest = { confirmingOldSessionPurge = false },
                    title = { Text("Delete old sessions?") },
                    text = {
                        Text(
                            "Completed sessions older than 90 days will be permanently " +
                                "deleted, with their audio clips, epochs and vitals. " +
                                "This cannot be undone."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmingOldSessionPurge = false
                            viewModel.purgeOldSessions()
                        }) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmingOldSessionPurge = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }

        // Tracking (sensor selection + standby control)
        SettingSection(title = "Sensor Mode") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Accelerometer button
                Button(
                    onClick = {
                        haptics.tick()
                        viewModel.updateTrackingMode(TrackingMode.ACCELEROMETER)
                    },
                    modifier = Modifier.weight(1f),
                    colors = if (settings.trackingMode == TrackingMode.ACCELEROMETER)
                        ButtonDefaults.buttonColors()
                    else
                        ButtonDefaults.outlinedButtonColors()
                ) {
                    Text(
                        "Accelerometer",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                // Sonar button
                Button(
                    onClick = {
                        haptics.tick()
                        viewModel.updateTrackingMode(TrackingMode.SONAR)
                    },
                    modifier = Modifier.weight(1f),
                    colors = if (settings.trackingMode == TrackingMode.SONAR)
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    else
                        ButtonDefaults.outlinedButtonColors()
                ) {
                    Text(
                        "Sonar (Beta)",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (settings.trackingMode == TrackingMode.ACCELEROMETER) {
                Text(
                    text = "Accelerometer - phone on bed, low battery usage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Sonar (Beta) - contactless, phone on nightstand. "
                        + "Uses speaker + mic continuously. Significantly higher battery drain.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Pets and partners moving may affect accuracy. "
                        + "Test on physical device only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(4.dp))
            SettingToggle(
                title = "Auto Do Not Disturb",
                checked = settings.dndEnabled,
                onCheckedChange = { viewModel.updateDndEnabled(it) }
            )
        }

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
            Spacer(modifier = Modifier.height(8.dp))
            val batteryOptions = listOf(5, 10, 15, 20, 25, 30)
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                batteryOptions.forEachIndexed { index, value ->
                    SegmentedButton(
                        selected = settings.batteryThreshold == value,
                        onClick = {
                            haptics.tick()
                            viewModel.updateBatteryThreshold(value)
                        },
                        shape = if (index == 0) SegmentedButtonDefaults.itemShape(index = 0, count = batteryOptions.size)
                        else if (index == batteryOptions.size - 1) SegmentedButtonDefaults.itemShape(index = batteryOptions.size - 1, count = batteryOptions.size)
                        else SegmentedButtonDefaults.itemShape(index = index, count = batteryOptions.size),
                        label = { Text("$value") }
                    )
                }
            }
        }

        

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
            Spacer(modifier = Modifier.height(8.dp))
            // Small slider with the same floating-value style as "Sleep Target", but snapped to
            // 30-minute steps (30m / 1h / 1h30 / 2h / 2h30 / 3h) so it stays compact. The value
            // shown on the thumb repeats the header above, keeping units obvious.
            SliderWithValueLabel(
                value = settings.oversleepThresholdMinutes.toFloat(),
                onValueChange = { viewModel.updateOversleepThresholdMinutes(it.roundToInt()) },
                valueRange = 30f..180f,
                steps = 4,
                valueLabel = oversleepLabel(settings.oversleepThresholdMinutes),
                modifier = Modifier.fillMaxWidth()
            )
        }

        

        // Seasonal Analysis - hemisphere override for the seasons used in circadian insights.
        // The default AUTO uses the device-timezone heuristic; travelers near the equator or on
        // the wrong side of a timezone boundary can pin the correct hemisphere here.
        SettingSection(title = "Seasonal Analysis") {
            Text(
                text = "Seasons are detected from your timezone. If the app labels the wrong " +
                    "season for your location, set your hemisphere here.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            HemisphereOverride.entries.forEach { override ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptics.tick()
                            viewModel.updateHemisphereOverride(override)
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = settings.hemisphereOverride == override,
                        onClick = {
                            haptics.tick()
                            viewModel.updateHemisphereOverride(override)
                        }
                    )
                    Text(text = override.displayName, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        

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
                val selected = settings.selectedCaptchaTaskId == id
                val rowColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer
                    else Color.Transparent,
                    animationSpec = tween(180),
                    label = "captchaRowColor"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(rowColor)
                        .clickable { viewModel.updateCaptchaTask(id) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected,
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
                        text = "QR Code configured",
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
        
        

        // Data export / backup / import is the one section that outgrew a card - pushing ~6 URLs,
        // file pickers and destructive actions into a single scrolling group. Moved to its own
        // screen; this row is just the pointer.
        SettingSection(title = "Data Export & Backup") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(onClick = onNavigateToDataExport)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Backup, restore, export & import",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Backups, recovery key, CSV/JSON export, Sleep as Android import",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        

        // NAS / Self-Hosted Backup
        SettingSection(title = "NAS Sync (Self-Hosted)") {
            SettingToggle(
                title = "Enable NAS Sync",
                checked = settings.nasEnabled,
                onCheckedChange = { viewModel.updateNasEnabled(it) }
            )

            if (settings.nasEnabled) {
                Spacer(modifier = Modifier.height(12.dp))

                // Protocol - WebDAV is the only transport NasProtocol defines (REL-05). Kept as a
                // labelled read-only row rather than a one-option picker so adding a second
                // transport later is a UI change, not a re-layout.
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

                // Port + Path row. The port field keeps a local string state so clearing it to type a new
                // value doesn't snap it back to the default via the repository flow's emission.
                var nasPortInput by rememberSaveable { mutableStateOf(settings.nasPort.toString()) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = nasPortInput,
                        onValueChange = {
                            nasPortInput = it
                            it.toIntOrNull()?.let { viewModel.updateNasPort(it) }
                        },
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

                // Transport security is an explicit switch, not something inferred from the port.
                SettingToggle(
                    title = "Use HTTPS",
                    checked = settings.nasUseHttps,
                    onCheckedChange = { viewModel.updateNasUseHttps(it) }
                )
                if (!settings.nasUseHttps) {
                    Text(
                        text = "HTTPS is off. Credentials would be sent unencrypted, and the " +
                            "connection will be refused by Android.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
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

                // Password - write-only: encrypted via Android Keystore, never read back (REL-06)
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
                    text = "All uploads AES-256-GCM encrypted via Android Keystore",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        

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
                checked = settings.healthConnectEnabled,
                onCheckedChange = { viewModel.updateHealthConnectEnabled(it) }
            )

            if (settings.healthConnectEnabled) {
                Spacer(modifier = Modifier.height(8.dp))

                val (statusText, statusColor) = when (settings.healthConnectStatus) {
                    HealthConnectStatus.AUTHORIZED -> "Connected" to MaterialTheme.colorScheme.primary
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
                // already wrote overlapping sleep data (dedup), and that skip is permanent - the
                // session's healthConnectRecordId stays null forever, so it'd otherwise never be
                // surfaced anywhere. This count also includes sessions simply not synced yet, so
                // it's worded as "haven't reached" rather than claiming they were all dedup-skipped.
                if (settings.healthConnectStatus == HealthConnectStatus.AUTHORIZED && settings.healthConnectUnsyncedCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (settings.healthConnectUnsyncedCount == 1)
                            "1 session hasn't reached Health Connect yet - " +
                                "either not synced, or another app already recorded overlapping sleep for that night."
                        else
                            "${settings.healthConnectUnsyncedCount} sessions haven't reached Health Connect yet - " +
                                "either not synced, or another app already recorded overlapping sleep for that night.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        

        // Experimental: YAMNet audio classification (Task 14, AUDIO-01)
        SettingSection(title = "Experimental") {
            val yamnetState by viewModel.yamnetModelState.collectAsState()

            // Toggling ON is consent-gated: with the model already verified on disk it turns on
            // immediately, otherwise the download consent dialog is raised first.
            SettingToggle(
                title = "ML Audio Classification (YAMNet)",
                checked = settings.yamnetClassificationEnabled,
                onCheckedChange = { viewModel.onYamnetToggle(it) }
            )

            when (val state = yamnetState) {
                is SettingsViewModel.YamnetModelState.Idle -> {
                    // Enabling the toggle above moves us to ConfirmingDownload. This hint covers
                    // the leftover setting on a fresh install whose model was never downloaded.
                    if (settings.yamnetClassificationEnabled) {
                        Text(
                            text = "Model not downloaded yet - switch it off and on to download.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is SettingsViewModel.YamnetModelState.ConfirmingDownload -> {
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissYamnetModelDialog() },
                        title = { Text("Download ML model?") },
                        text = {
                            Text(
                                "This downloads the ~4 MB YAMNet audio model once over the " +
                                    "internet (HTTPS, checksum-verified). It then runs entirely " +
                                    "on-device - audio never leaves your phone."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { viewModel.confirmYamnetDownload() }) {
                                Text("Download")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { viewModel.dismissYamnetModelDialog() }) {
                                Text("Not now")
                            }
                        }
                    )
                }
                is SettingsViewModel.YamnetModelState.Downloading -> {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        LinearProgressIndicator(
                            progress = { state.progress ?: 0f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Downloading model...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is SettingsViewModel.YamnetModelState.Error -> {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Text(
                            text = "Model download failed: ${state.message}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = { viewModel.confirmYamnetDownload() }) {
                            Text("Retry")
                        }
                    }
                }
                is SettingsViewModel.YamnetModelState.Ready -> {
                    // Model on disk - toggle alone is the whole surface.
                }
            }
        }

        

        // Wind-Down Toolkit
        SettingSection(title = "Wind-Down Toolkit") {
            Button(
                onClick = onNavigateToBreathing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Breathing Exercise (4-7-8)")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onNavigateToCognitiveWindDown,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cognitive Dump")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onNavigateToADHDCooldown,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("ADHD Cooldown")
            }
        }

        // About - version comes from the installed package (PackageInfo) so it always matches the
        // channel actually installed (0.1.2 vs 0.1.2-store and future releases), never a stale
        // hardcoded constant.
        val appVersion = remember(context) {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
                .getOrNull() ?: ""
        }
        Text(
            text = "Somn v$appVersion",
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

                                val barcodeReader = BarcodeReader()
                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()

                                // zxing-cpp decodes synchronously on the analyzer executor.
                                // use() closes the ImageProxy exactly once so CameraX keeps
                                // delivering frames.
                                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                                    val results = try {
                                        imageProxy.use { barcodeReader.read(it) }
                                    } catch (e: Exception) {
                                        Log.e("QRSetup", "QR scan failed", e)
                                        return@setAnalyzer
                                    }
                                    results.firstOrNull { it.text != null }?.text?.let(onScanSuccess)
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
internal fun SettingSection(
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
        OutlinedCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                content()
            }
        }
    }
}

/**
 * An M3 [Slider] with a live value callout floating above the thumb - replaces the old "label
 * line above the slider" pattern, which read as two separate controls. Slot above the track is a
 * fixed-height strip; the callout centers itself on the thumb via the same geometry the slider
 * uses (thumb travels between one thumb-radius inset and width-inset).
 */
@Composable
private fun SliderWithValueLabel(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: String,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val thumbRadius = with(density) { 10.dp.toPx() }
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start))
        .coerceIn(0f, 1f)

    var stripWidthPx by remember { mutableIntStateOf(0) }
    var labelWidthPx by remember { mutableIntStateOf(0) }
    var labelHeightPx by remember { mutableIntStateOf(0) }

    val haptics = LocalHaptics.current
    var lastHapticTickValue by remember { mutableFloatStateOf(value) }
    val stepSize = (valueRange.endInclusive - valueRange.start) / (steps + 1)

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .onSizeChanged { stripWidthPx = it.width }
        ) {
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .onSizeChanged {
                        labelWidthPx = it.width
                        labelHeightPx = it.height
                    }
                    .offset {
                        val travelPx = stripWidthPx - 2f * thumbRadius
                        val x = (thumbRadius + fraction * travelPx - labelWidthPx / 2f)
                            .coerceIn(0f, (stripWidthPx - labelWidthPx).coerceAtLeast(0).toFloat())
                        IntOffset(x.roundToInt(), -labelHeightPx / 2)
                    }
            )
        }
        Slider(
            value = value,
            onValueChange = { newValue ->
                // Physical-image haptics: one subtle tick each time the drag crosses a step stop,
                // so scrubbing between discrete options feels stepped, not continuous.
                if (abs(newValue - lastHapticTickValue) >= stepSize) {
                    lastHapticTickValue = newValue
                    haptics.tick()
                }
                onValueChange(newValue)
            },
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
private fun SettingToggle(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val haptics = LocalHaptics.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Only the Switch itself was clickable - the label row did nothing, so a tap aimed at
            // the (wide) title silently missed. Make the whole row a toggle with a Switch role so
            // TalkBack reads the switch state and both access paths toggle the same setting.
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = {
                    haptics.tick()
                    onCheckedChange(it)
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        // Visual-only switch - the Row's toggleable owns the interaction so row and switch share
        // one click target instead of fighting for the same tap.
        Switch(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.clearAndSetSemantics {}
        )
    }
}

private val HapticsIntensity.label: String
    get() = when (this) {
        HapticsIntensity.LIGHT -> "Light"
        HapticsIntensity.STANDARD -> "Standard"
        HapticsIntensity.STRONG -> "Strong"
    }

// Oversleep pill labels: compact enough to stay single-line inside a six-way split on a 411dp
// screen, but with units so the row reads like the battery one yet stays self-explanatory.
// "1h30" = 1h 30m (minutes are the unit across the whole section, per the header above it).
private fun oversleepLabel(minutes: Int): String = when (minutes) {
    30 -> "30m"
    60 -> "1h"
    90 -> "1h30"
    120 -> "2h"
    150 -> "2h30"
    180 -> "3h"
    else -> "${minutes}m"
}
