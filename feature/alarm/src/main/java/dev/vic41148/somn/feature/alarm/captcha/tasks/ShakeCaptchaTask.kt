package dev.vic41148.somn.feature.alarm.captcha.tasks

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.vic41148.somn.core.audio.sensor.AccelerometerCollector
import dev.vic41148.somn.feature.alarm.captcha.CaptchaTask
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

class ShakeCaptchaTask : CaptchaTask {
    override val id: String = "shake"
    override val displayName: String = "Hard Shake"
    
    private var progress by mutableStateOf(0f)
    private var isSolved by mutableStateOf(false)

    override fun isComplete(): Boolean = isSolved

    override fun reset() {
        progress = 0f
        isSolved = false
    }

    @Composable
    override fun TaskUI(onComplete: () -> Unit) {
        val context = LocalContext.current
        var startTime by remember { mutableLongStateOf(0L) }
        
        DisposableEffect(Unit) {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
            val accelerometer = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
            
            val listener = object : android.hardware.SensorEventListener {
                override fun onSensorChanged(event: android.hardware.SensorEvent?) {
                    if (event == null) return
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    val magnitude = kotlin.math.sqrt(x * x + y * y + z * z) / 9.81f
                    
                    if (magnitude > 2.5f) {
                        if (startTime == 0L) startTime = System.currentTimeMillis()
                        val duration = System.currentTimeMillis() - startTime
                        progress = (duration / 3000f).coerceAtMost(1.0f)
                        if (progress >= 1.0f) {
                            isSolved = true
                            onComplete()
                        }
                    } else {
                        startTime = 0L
                        progress = 0f
                    }
                }
                override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
            }
            
            sensorManager.registerListener(listener, accelerometer, android.hardware.SensorManager.SENSOR_DELAY_UI)
            
            onDispose {
                sensorManager.unregisterListener(listener)
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Shake your phone hard!",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(48.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(16.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "${(progress * 100).toInt()}% Done",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
