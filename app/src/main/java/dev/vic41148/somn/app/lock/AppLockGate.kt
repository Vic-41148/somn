package dev.vic41148.somn.app.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dev.vic41148.somn.core.data.repository.SomnPreferencesRepository

/**
 * Opt-in cold-start gate: when the user enabled app lock, the UI stays behind biometrics
 * or the device credential until unlocked. Process-scoped ([rememberSaveable] survives
 * rotation, not process death), so background tracking, alarms, and workers are never
 * gated — only what is on screen. If the OS cannot authenticate (no screen lock
 * enrolled), the gate opens rather than bricking the app.
 */
@Composable
fun AppLockGate(
    preferencesRepository: SomnPreferencesRepository,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val lockEnabled by preferencesRepository.appLockEnabled.collectAsState(initial = false)
    var unlocked by rememberSaveable { mutableStateOf(false) }

    if (!lockEnabled || unlocked || activity == null) {
        content()
        return
    }

    val canAuthenticate = remember(activity) {
        BiometricManager.from(activity).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
    }
    if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
        // Nothing to verify against — locking here would brick the app until a credential
        // is enrolled elsewhere. Open instead; the toggle stays on for when one exists.
        content()
        return
    }

    var error by remember { mutableStateOf<String?>(null) }
    val executor = remember(activity) { ContextCompat.getMainExecutor(activity) }
    val prompt = remember(activity) {
        BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    unlocked = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    error = errString.toString()
                }
            }
        )
    }
    val promptInfo = remember {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Somn")
            .setSubtitle("Confirm it is you to see your sleep data")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
    }

    LaunchedEffect(prompt) {
        prompt.authenticate(promptInfo)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Somn is locked",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = error ?: "Unlock with biometrics or your device PIN, pattern, or password.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = {
            error = null
            prompt.authenticate(promptInfo)
        }) {
            Text("Unlock")
        }
    }
}
