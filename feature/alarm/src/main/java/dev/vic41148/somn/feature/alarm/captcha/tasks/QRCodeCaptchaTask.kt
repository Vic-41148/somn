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
import zxingcpp.BarcodeReader
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

        val preferencesRepository = remember {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                PreferencesEntryPoint::class.java
            )
            entryPoint.preferencesRepository()
        }

        val expectedValue by preferencesRepository.qrCodeValue.collectAsState(initial = null)

        val currentExpectedValue = expectedValue
        if (currentExpectedValue == null) {
            // AlarmActivity already verified a QR value is configured before selecting this
            // task (falling back to math otherwise) — a null here just means this DataStore
            // flow hasn't emitted its first value yet, not a real "not configured" state.
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        val executor = remember { Executors.newSingleThreadExecutor() }
        val barcodeReader = remember { BarcodeReader() }
        val cameraProviderRef = remember { arrayOfNulls<ProcessCameraProvider>(1) }

        DisposableEffect(Unit) {
            onDispose {
                cameraProviderRef[0]?.unbindAll()
                executor.shutdown()
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        cameraProviderRef[0] = cameraProvider
                        val preview = Preview.Builder().build().apply {
                            surfaceProvider = previewView.surfaceProvider
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(executor) { imageProxy ->
                            processImageProxy(barcodeReader, imageProxy, currentExpectedValue) {
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
                        text = "Scan your configured QR code to dismiss the alarm",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }

    /**
     * zxing-cpp decodes synchronously on the calling thread — this runs on the single-threaded
     * analyzer executor, not the main thread. The ImageProxy must be closed exactly once for
     * CameraX to deliver the next frame, hence [use].
     */
    private fun processImageProxy(
        barcodeReader: BarcodeReader,
        imageProxy: ImageProxy,
        expectedValue: String,
        onSuccess: () -> Unit
    ) {
        val results = try {
            imageProxy.use { barcodeReader.read(it) }
        } catch (e: Exception) {
            Log.e("QRCodeCaptchaTask", "QR scan failed", e)
            return
        }
        if (results.any { it.text == expectedValue }) {
            onSuccess()
        }
    }
}
