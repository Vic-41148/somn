package dev.vic41148.somn.feature.tracking.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.vic41148.somn.core.domain.model.DebtLevel
import dev.vic41148.somn.core.domain.model.SleepDebt
import dev.vic41148.somn.core.ui.components.MetricChip
import dev.vic41148.somn.core.ui.components.SleepCard
import dev.vic41148.somn.core.ui.components.SleepScoreRing
import dev.vic41148.somn.feature.habits.HabitViewModel
import dev.vic41148.somn.feature.tracking.SleepTrackingViewModel
import dev.vic41148.somn.feature.tracking.service.TrackingState

@Composable
fun HomeScreen(
    onNavigateToTracking: () -> Unit,
    onNavigateToMorningReview: (Long) -> Unit,
    onNavigateToDebt: () -> Unit = {},
    trackingMode: dev.vic41148.somn.core.domain.model.TrackingMode = dev.vic41148.somn.core.domain.model.TrackingMode.ACCELEROMETER,
    viewModel: SleepTrackingViewModel = hiltViewModel(),
    habitViewModel: HabitViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val trackingState by viewModel.trackingState.collectAsState()
    val lastSession by viewModel.lastSession.collectAsState()
    val lastScore by viewModel.lastScore.collectAsState()
    val sleepDebt by habitViewModel.sleepDebt.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Text(
            text = "Somn",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Good night, sleep tight",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // REL-03: battery-optimization exemption re-check — OEM power managers can silently
        // revoke this after an OTA, which kills overnight tracking without any user-visible error.
        val isBatteryExempted by dev.vic41148.somn.core.ui.battery.BatteryExemptionState.isExempted.collectAsState()
        var batteryBannerDismissed by remember { mutableStateOf(false) }
        AnimatedVisibility(
            visible = !isBatteryExempted && !batteryBannerDismissed,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                SleepCard(
                    title = "Battery Optimization Is On",
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = "Your phone may stop tracking overnight because battery optimization " +
                            "is restricting Somn in the background.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        androidx.compose.material3.TextButton(onClick = { batteryBannerDismissed = true }) {
                            Text("Dismiss")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        androidx.compose.material3.Button(onClick = {
                            context.startActivity(
                                dev.vic41148.somn.core.ui.battery.BatteryExemptionState.buildFixIntent(context)
                            )
                        }) {
                            Text("Fix")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Session type selector (SESS-01/02) — only meaningful before a session starts.
        var selectedSessionType by remember {
            mutableStateOf(dev.vic41148.somn.core.domain.model.SessionType.MAIN_SLEEP)
        }
        AnimatedVisibility(visible = trackingState == TrackingState.IDLE, enter = fadeIn(), exit = fadeOut()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                dev.vic41148.somn.core.domain.model.SessionType.entries.forEach { type ->
                    androidx.compose.material3.FilterChip(
                        selected = selectedSessionType == type,
                        onClick = { selectedSessionType = type },
                        label = { Text(type.displayName, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Moon button — start tracking
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(
                    if (trackingState == TrackingState.TRACKING)
                        MaterialTheme.colorScheme.tertiaryContainer
                    else
                        MaterialTheme.colorScheme.primaryContainer
                )
                .clickable {
                    if (trackingState == TrackingState.IDLE) {
                        viewModel.startTracking(context, trackingMode, selectedSessionType)
                        onNavigateToTracking()
                    }
                }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Nightlight,
                    contentDescription = "Start sleep tracking",
                    modifier = Modifier.size(56.dp),
                    tint = if (trackingState == TrackingState.TRACKING)
                        MaterialTheme.colorScheme.onTertiaryContainer
                    else
                        MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (trackingState == TrackingState.TRACKING) "Tracking..." else "Sleep",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (trackingState == TrackingState.TRACKING)
                        MaterialTheme.colorScheme.onTertiaryContainer
                    else
                        MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Last night's score
        AnimatedVisibility(
            visible = lastSession != null && lastSession!!.isCompleted,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            lastSession?.let { session ->
                SleepCard(title = "Last Night") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val hours = session.sleepDurationMinutes / 60
                            val mins = session.sleepDurationMinutes % 60
                            Text(
                                text = "${hours}h ${mins}m",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "sleep duration",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        SleepScoreRing(
                            score = session.sleepScore,
                            size = 80.dp,
                            strokeWidth = 8.dp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MetricChip(
                            label = "Efficiency",
                            value = "${session.sleepEfficiency.toInt()}%"
                        )
                        MetricChip(
                            label = "Deep",
                            value = "${session.deepSleepPercent.toInt()}%"
                        )
                        MetricChip(
                            label = "Wakes",
                            value = "${session.wakeEvents}"
                        )
                    }

                    // Score explanation
                    lastScore?.let { score ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = score.explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Empty state
        if (lastSession == null) {
            Spacer(modifier = Modifier.height(24.dp))
            SleepCard {
                Text(
                    text = "Tap the moon to start tracking your first night of sleep.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Sleep Debt card
        sleepDebt?.let { debt ->
            Spacer(modifier = Modifier.height(16.dp))
            SleepDebtHomeCard(debt = debt, onClick = onNavigateToDebt)
        }
    }
}

@Composable
private fun SleepDebtHomeCard(debt: SleepDebt, onClick: () -> Unit) {
    val levelColor = when (debt.level) {
        DebtLevel.NONE -> MaterialTheme.colorScheme.primary
        DebtLevel.MILD -> Color(0xFFF9A825)
        DebtLevel.MODERATE -> Color(0xFFE65100)
        DebtLevel.SEVERE -> MaterialTheme.colorScheme.error
    }

    SleepCard(
        title = "Sleep Debt",
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                val h = debt.totalDebtMinutes / 60
                val m = debt.totalDebtMinutes % 60
                val debtStr = when {
                    debt.totalDebtMinutes == 0 -> "None 🎉"
                    h > 0 -> "${h}h ${m}m"
                    else -> "${m}m"
                }
                Text(
                    text = debtStr,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = levelColor
                )
                Text(
                    text = "${debt.trend.emoji} ${debt.trend.displayName}  •  ${debt.level.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.TrendingDown,
                contentDescription = "Sleep debt trend",
                tint = levelColor,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap for your 14-day breakdown and recovery plan →",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
