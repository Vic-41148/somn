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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import dev.vic41148.somn.core.domain.model.AlarmPreferences
import dev.vic41148.somn.feature.alarm.captcha.CaptchaTaskRegistry
import dev.vic41148.somn.feature.alarm.captcha.tasks.NFCCaptchaTask
import dev.vic41148.somn.feature.alarm.service.AlarmService
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.flow.first

@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {

    @javax.inject.Inject
    lateinit var preferencesRepository: dev.vic41148.somn.core.data.repository.SomnPreferencesRepository

    private var currentTask: dev.vic41148.somn.feature.alarm.captcha.CaptchaTask? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        turnScreenOnAndShowOnLockScreen()

        // Intercept back button to prevent accidental dismissal of the alarm
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing - user must complete CAPTCHA or press Snooze
            }
        })

        setContent {
            MaterialTheme {
                // Reading DataStore synchronously (runBlocking) here would block onCreate on the
                // highest-stakes screen in the app. Load it asynchronously instead — this class's
                // `currentTask` field (needed synchronously by onNewIntent's NFC routing) is set
                // as a side effect of the same LaunchedEffect once it resolves.
                var loadedTask by remember { mutableStateOf<dev.vic41148.somn.feature.alarm.captcha.CaptchaTask?>(null) }
                var taskReady by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    val taskId = preferencesRepository.selectedCaptchaTaskId.first()
                    val qrValue = preferencesRepository.qrCodeValue.first()
                    var task = CaptchaTaskRegistry.getTask(taskId)
                    if (taskId == "qrcode" && qrValue == null) {
                        task = CaptchaTaskRegistry.getTask("math")
                    }
                    task?.reset()
                    currentTask = task
                    loadedTask = task
                    taskReady = true
                }

                if (!taskReady) {
                    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {}
                } else {
                    val phase by AlarmService.phase.collectAsState()
                    LaunchedEffect(phase) {
                        if (phase == AlarmService.AlarmPhase.DISMISSED) finish()
                    }

                    AlarmScreen(
                        currentTask = loadedTask,
                        onDismiss = {
                            AlarmService.requestDismiss(this)
                        },
                        onSnooze = {
                            AlarmService.snooze(this)
                            finish()
                        },
                        onConfirmAwake = {
                            AlarmService.confirmAwake(this)
                        }
                    )
                }
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
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }
}

@Composable
fun AlarmScreen(
    currentTask: dev.vic41148.somn.feature.alarm.captcha.CaptchaTask?,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
    onConfirmAwake: () -> Unit
) {
    val phase by AlarmService.phase.collectAsState()
    val wakeConfirmDeadline by AlarmService.wakeConfirmDeadlineMillis.collectAsState()
    val alarmLabel by AlarmService.currentAlarmLabel.collectAsState()
    val canSnooze by AlarmService.canSnooze.collectAsState()
    val currentTime = remember { mutableStateOf(System.currentTimeMillis()) }

    // Re-keyed by phase: a WAKE-02 re-ring transitions AWAITING_WAKE_CONFIRMATION -> FIRING,
    // which must reset both the captcha's own solved-state and this screen's completion flag.
    var isCaptchaComplete by remember(phase) { mutableStateOf(false) }

    LaunchedEffect(phase) {
        if (phase == AlarmService.AlarmPhase.FIRING) currentTask?.reset()
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime.value = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }

    // The alarm's phase transitions (a WAKE-02 re-ring flips AWAITING_WAKE_CONFIRMATION back to
    // FIRING, dismiss flips FIRING to AWAITING_WAKE_CONFIRMATION) used to be an instant cut
    // between two entirely separate Composables. This is the single highest-stakes screen in the
    // app — deliberately calm motion here, not a bounce/overshoot, matching the color system.
    AnimatedContent(
        targetState = phase,
        transitionSpec = {
            fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(250))
        },
        label = "alarm_phase_transition"
    ) { targetPhase ->
        if (targetPhase == AlarmService.AlarmPhase.AWAITING_WAKE_CONFIRMATION) {
            WakeConfirmScreen(deadlineMillis = wakeConfirmDeadline, onConfirmAwake = onConfirmAwake)
        } else {
            AlarmFiringContent(
                currentTimeMillis = currentTime.value,
                alarmLabel = alarmLabel,
                currentTask = currentTask,
                isCaptchaComplete = isCaptchaComplete,
                onCaptchaComplete = { isCaptchaComplete = true },
                canSnooze = canSnooze,
                onSnooze = onSnooze,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun AlarmFiringContent(
    currentTimeMillis: Long,
    alarmLabel: String,
    currentTask: dev.vic41148.somn.feature.alarm.captcha.CaptchaTask?,
    isCaptchaComplete: Boolean,
    onCaptchaComplete: () -> Unit,
    canSnooze: Boolean,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = LocalHapticFeedback.current

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        // Slow, calm breathing pulse for as long as this screen is shown (i.e. the alarm is
        // actively ringing) — ambient urgency independent of captcha progress, not a "try me"
        // cue on any specific control. Deliberately gentle: tween easing, no spring/bounce.
        val infiniteTransition = rememberInfiniteTransition(label = "alarm_ringing_pulse")
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.75f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alarm_ringing_pulse_alpha"
        )

        val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = timeFormatter.format(Date(currentTimeMillis)),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.graphicsLayer { alpha = pulseAlpha }
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
                    currentTask.TaskUI(onComplete = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCaptchaComplete()
                    })
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
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSnooze()
                        },
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) {
                        Text("Snooze")
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                }

                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDismiss()
                    },
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

/** WAKE-01: shown after dismiss, before the alarm is fully silenced — WAKE-02 re-rings via [onConfirmAwake]'s deadline lapsing if the user doesn't respond. */
@Composable
private fun WakeConfirmScreen(deadlineMillis: Long?, onConfirmAwake: () -> Unit) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }

    val secondsLeft = deadlineMillis?.let { ((it - now) / 1000).coerceAtLeast(0) } ?: 0
    val haptics = LocalHapticFeedback.current

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
                text = "Are You Awake?",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Confirm within ${secondsLeft}s or the alarm will ring again.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onConfirmAwake()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("I'm Awake")
            }
        }
    }
}
