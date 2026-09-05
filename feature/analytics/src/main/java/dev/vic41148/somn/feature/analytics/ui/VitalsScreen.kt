package dev.vic41148.somn.feature.analytics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.vic41148.somn.core.domain.usecase.VitalFlag
import dev.vic41148.somn.core.ui.components.SleepCard
import dev.vic41148.somn.feature.analytics.AnalyticsViewModel

/**
 * R2 vitals dashboard — WHOOP Health Monitor pattern without the hardware: each
 * wearable vital against its own recent range. The screen flags it only on real drift. Ranges
 * are personal (last 14 nights), never population norms. Thin history renders as
 * "needs more nights", never as a false alarm.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VitalsScreen(
    onBack: () -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val flags by viewModel.vitalFlags.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vitals") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                flags == null -> SleepCard(title = "The app reads your vitals") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                    }
                }
                flags!!.isEmpty() -> SleepCard(title = "No wearable data yet") {
                    Text(
                        text = "Connect a wearable in Settings through Health Connect. " +
                            "Your resting heart rate, HRV, oxygen, and skin temperature " +
                            "appear here against your own usual range.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                else -> {
                    SleepCard(title = "Overnight vitals") {
                        flags!!.forEach { flag ->
                            VitalRow(flag = flag)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                    Text(
                        text = "Ranges are your own last 14 nights. They are not population " +
                            "averages. A flag means you moved from your usual. " +
                            "It does not mean that a number is wrong.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun VitalRow(flag: VitalFlag) {
    val (icon, tint) = when {
        !flag.hasData -> Icons.Default.Remove to MaterialTheme.colorScheme.onSurfaceVariant
        flag.inRange -> Icons.Default.Check to MaterialTheme.colorScheme.primary
        else -> Icons.Default.Warning to MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = flag.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = flag.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = flag.value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
