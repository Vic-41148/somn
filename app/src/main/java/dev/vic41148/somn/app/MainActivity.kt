package dev.vic41148.somn.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import dev.vic41148.somn.app.integration.UpdateIntegration
import dev.vic41148.somn.app.lock.AppLockGate
import dev.vic41148.somn.app.navigation.SleepNavGraph
import dev.vic41148.somn.core.data.repository.SomnPreferencesRepository
import dev.vic41148.somn.core.data.repository.UserProfileRepository
import dev.vic41148.somn.core.domain.haptic.HapticsManager
import dev.vic41148.somn.core.ui.battery.BatteryExemptionState
import dev.vic41148.somn.core.ui.haptic.ProvideHaptics
import dev.vic41148.somn.core.ui.theme.SomnTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var profileRepository: UserProfileRepository
    @Inject lateinit var preferencesRepository: SomnPreferencesRepository
    @Inject lateinit var hapticsManager: HapticsManager
    @Inject lateinit var updateIntegrations: Set<@JvmSuppressWildcards UpdateIntegration>

    override fun onResume() {
        super.onResume()
        // REL-03: OEM power-management layers can silently revoke this exemption after an
        // OTA update, so re-check on every resume rather than only once at onboarding.
        BatteryExemptionState.recheck(this)
    }

    // The tracking FGS notification carries EXTRA_OPEN_TRACKING on its content intent. On a
    // warm tap the activity is already alive, so onNewIntent must refresh the intent property
    // — SleepNavGraph keys an effect on activity.intent, which re-fires and navigates to the
    // tracking screen (cold starts pick the original intent up directly).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val useDynamicColor by preferencesRepository.useDynamicColor
                .collectAsState(initial = true)
            SomnTheme(dynamicColor = useDynamicColor) {
                val isOnboardingCompleted by profileRepository
                    .observeOnboardingCompleted()
                    .collectAsState(initial = null)

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (isOnboardingCompleted) {
                        null -> {
                            // Loading state while DB query resolves
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        else -> {
                            // Channel-scoped components that must render above the nav graph:
                            // standalone wraps for haptics + shows the first-launch restore prompt.
                            ProvideHaptics(delegate = hapticsManager) {
                                AppLockGate(preferencesRepository = preferencesRepository) {
                                    SleepNavGraph(
                                        isOnboardingCompleted = isOnboardingCompleted ?: false,
                                        updateIntegrations = updateIntegrations
                                    )
                                }
                                updateIntegrations.forEach { it.RestorePrompt() }
                            }
                        }
                    }
                }
            }
        }
    }
}
