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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
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

    Column(
        modifier = Modifier
            .fillMaxSize()
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

        // Wake Window
        SettingSection(title = "Default Wake Window") {
            Text(
                text = "${settings.wakeWindowMinutes} minutes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Slider(
                value = settings.wakeWindowMinutes.toFloat(),
                onValueChange = { viewModel.updateWakeWindow(it.toInt()) },
                valueRange = 10f..45f,
                steps = 6,
                modifier = Modifier.fillMaxWidth()
            )
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
        SettingSection(title = "Data Export") {
            Button(
                onClick = { viewModel.exportData(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Text("  Export All Data (CSV)")
            }
            exportStatus?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
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
                    text = "Scan the QR code you want to use for alarm dismissal (e.g., in your bathroom).",
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
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
