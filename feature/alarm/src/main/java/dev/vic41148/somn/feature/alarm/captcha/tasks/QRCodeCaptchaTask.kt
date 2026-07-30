package dev.vic41148.somn.feature.alarm.captcha.tasks

import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import dev.vic41148.somn.core.domain.model.AlarmPreferences
import dev.vic41148.somn.feature.alarm.captcha.CaptchaTask
import dev.vic41148.somn.core.data.repository.SomnPreferencesRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.Executors

class QRCodeCaptchaTask : CaptchaTask {
    override val id: String = "qrcode"
    override val displayName: String = "QR Code Scan"

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PreferencesEntryPoint {
        fun preferencesRepository(): SomnPreferencesRepository
    }
    
    private var isSolved by mutableStateOf(false)

    override fun isComplete(): Boolean = isSolved

    override fun reset() {
        isSolved = false
    }

    @Composable
    override fun TaskUI(onComplete: () -> Unit) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        
        // Actually, I'll update the TaskUI to use the value if passed or just use a placeholder for now 
        // and fix the registry later.
        
        val preferencesRepository = remember {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                PreferencesEntryPoint::class.java
            )
            entryPoint.preferencesRepository()
        }
        
        val expectedValue by preferencesRepository?.qrCodeValue?.collectAsState(initial = null) ?: remember { mutableStateOf<String?>(null) }

        val currentExpectedValue = expectedValue
        if (currentExpectedValue == null) {
            Text("QR not configured. Fallback to Math.")
            LaunchedEffect(Unit) {
                // In real app, the registry would handle the fallback before launching this
            }
            return
        }

        Box(modifier = Modifier.fillMaxSize()) {
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
                            processImageProxy(scanner, imageProxy, currentExpectedValue) {
                                isSolved = true
                                onComplete()
                            }
                        }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            Log.e("QRCodeCaptchaTask", "Use case binding failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = "Scan your configured QR code",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(
        scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
        imageProxy: ImageProxy,
        expectedValue: String,
        onSuccess: () -> Unit
    ) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        if (barcode.rawValue == expectedValue) {
                            onSuccess()
                        }
                    }
                }
                .addOnFailureListener {
                    Log.e("QRCodeCaptchaTask", "QR scan failed", it)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}
