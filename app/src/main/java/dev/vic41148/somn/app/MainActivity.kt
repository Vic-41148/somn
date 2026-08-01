package dev.vic41148.somn.app

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import dev.vic41148.somn.app.navigation.SleepNavGraph
import dev.vic41148.somn.core.data.repository.UserProfileRepository
import dev.vic41148.somn.core.ui.battery.BatteryExemptionState
import dev.vic41148.somn.core.ui.theme.SomnTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var profileRepository: UserProfileRepository

    override fun onResume() {
        super.onResume()
        // REL-03: OEM power-management layers can silently revoke this exemption after an
        // OTA update, so re-check on every resume rather than only once at onboarding.
        BatteryExemptionState.recheck(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SomnTheme {
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
                            SleepNavGraph(
                                isOnboardingCompleted = isOnboardingCompleted ?: false
                            )
                        }
                    }
                }
            }
        }
    }
}
