package dev.vic41148.somn.feature.alarm.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import dev.vic41148.somn.core.domain.model.AlarmPreferences
import dev.vic41148.somn.feature.alarm.captcha.CaptchaTaskRegistry
import dev.vic41148.somn.feature.alarm.captcha.tasks.NFCCaptchaTask
import dev.vic41148.somn.feature.alarm.service.AlarmService
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {

    private var currentTask: dev.vic41148.somn.feature.alarm.captcha.CaptchaTask? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        turnScreenOnAndShowOnLockScreen()

        val taskId = AlarmPreferences.selectedCaptchaTaskId
        currentTask = CaptchaTaskRegistry.getTask(taskId)
        if (taskId == "qrcode" && AlarmPreferences.qrCodeValue == null) {
            currentTask = CaptchaTaskRegistry.getTask("math")
        }
        currentTask?.reset()

        setContent {
            MaterialTheme {
                AlarmScreen(
                    currentTask = currentTask,
                    onDismiss = {
                        AlarmService.dismiss(this)
                        finish()
                    },
                    onSnooze = {
                        AlarmService.snooze(this)
                        finish()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        nfcAdapter?.enableForegroundDispatch(this, null, null, null)
    }

    override fun onPause() {
        super.onPause()
        val nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {
            (currentTask as? NFCCaptchaTask)?.onTagDiscovered()
        }
    }

    private fun turnScreenOnAndShowOnLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.`LayoutParams`.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        with(getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requestDismissKeyguard(this@AlarmActivity, null)
            }
        }
    }
}

@Composable
fun AlarmScreen(
    currentTask: dev.vic41148.somn.feature.alarm.captcha.CaptchaTask?,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {
    val alarmLabel by AlarmService.currentAlarmLabel.collectAsState()
    val canSnooze by AlarmService.canSnooze.collectAsState()
    val currentTime = remember { mutableStateOf(System.currentTimeMillis()) }

    var isCaptchaComplete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime.value = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(currentTime.value)),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = alarmLabel.ifEmpty { "Wake Up!" },
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(48.dp))

            if (currentTask != null && !isCaptchaComplete) {
                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    currentTask.TaskUI(onComplete = { isCaptchaComplete = true })
                }
            } else if (isCaptchaComplete || currentTask == null) {
                Text(
                    text = "Unlocked!",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (canSnooze) {
                    OutlinedButton(
                        onClick = onSnooze,
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) {
                        Text("Snooze")
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                }
                
                Button(
                    onClick = onDismiss,
                    enabled = isCaptchaComplete,
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isCaptchaComplete) "Dismiss" else "Locked")
                }
            }
        }
    }
}
