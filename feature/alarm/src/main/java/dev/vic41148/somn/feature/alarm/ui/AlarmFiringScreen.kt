package dev.vic41148.somn.feature.alarm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import dev.vic41148.somn.feature.alarm.AlarmViewModel
import dev.vic41148.somn.feature.alarm.service.AlarmService

/**
 * In-app full-screen alarm firing view (route `alarm_firing`), reached automatically from the
 * nav graph when an alarm starts ringing while the app is open.
 *
 * This is deliberately a thin wrapper around the same [AlarmScreen] that [AlarmActivity] shows,
 * so the two surfaces can never drift apart: identical captcha gating (per-alarm type, resolved
 * by the ViewModel with the same precedence as the activity), identical WAKE-01 wake-confirmation
 * flow, identical Snooze/Dismiss semantics. Back is trapped exactly like the activity's — the
 * only way out is Snooze or Dismiss.
 *
 * The screen leaves itself when the firing episode ends ([AlarmService.phase] turns DISMISSED —
 * including after a snooze, which the service now reports as ending the episode); the nav graph
 * observes that transition and pops the route.
 */
@Composable
fun AlarmFiringScreen(
    viewModel: AlarmViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val captchaReady by viewModel.captchaReady.collectAsState()
    val currentTask by viewModel.captchaTask.collectAsState()

    // Same as AlarmActivity: prevent accidental dismissal of the alarm via system back.
    BackHandler {}

    // Same as AlarmActivity's taskReady gate: a null task reads as "Unlocked!" in AlarmScreen, so
    // showing it before the captcha resolves would open a brief captcha-bypass window on Dismiss.
    if (!captchaReady) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {}
    } else {
        AlarmScreen(
            currentTask = currentTask,
            onDismiss = { AlarmService.requestDismiss(context) },
            onSnooze = { AlarmService.snooze(context) },
            onConfirmAwake = { AlarmService.confirmAwake(context) }
        )
    }
}
