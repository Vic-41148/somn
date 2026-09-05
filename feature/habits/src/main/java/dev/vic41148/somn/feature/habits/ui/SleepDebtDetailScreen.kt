package dev.vic41148.somn.feature.habits.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.vic41148.somn.core.ui.components.ColorLegendItem
import dev.vic41148.somn.core.ui.theme.DebtMild
import dev.vic41148.somn.core.ui.theme.DebtModerate
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.vic41148.somn.core.domain.model.DailyDebt
import dev.vic41148.somn.core.domain.model.DebtLevel
import dev.vic41148.somn.core.domain.model.DebtTrend
import dev.vic41148.somn.feature.habits.HabitViewModel
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepDebtDetailScreen(
    onBack: () -> Unit = {},
    viewModel: HabitViewModel = hiltViewModel()
) {
    val debt by viewModel.sleepDebt.collectAsState()
    val plan by viewModel.recoveryPlan.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // This screen (reached by tapping the Sleep Debt card on Home) had no TopAppBar/back button
    // at all and its route hides the bottom nav bar — a dead end with no visible way back short
    // of the system back gesture.
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sleep Debt") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Your last 14 nights",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (isLoading || debt == null) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        val sleepDebt = debt!!

        // ---- Summary card ----
        DebtSummaryCard(
            debtMinutes = sleepDebt.totalDebtMinutes,
            level = sleepDebt.level,
            trend = sleepDebt.trend
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ---- 14-day bar chart ----
        Text(
            text = "14-Day Breakdown",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(12.dp))
        DebtBarChart(days = sleepDebt.dailyBreakdown)

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ColorLegendItem(color = MaterialTheme.colorScheme.primary, label = "Surplus")
            ColorLegendItem(color = MaterialTheme.colorScheme.error, label = "Deficit")
            ColorLegendItem(color = MaterialTheme.colorScheme.surfaceVariant, label = "No data")
        }

        // ---- Recovery plan ----
        plan?.let { recoveryPlan ->
            if (recoveryPlan.additionalMinutesPerNight > 0) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Recovery Plan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            RecoveryMetric(
                                label = "Extra sleep",
                                value = "${recoveryPlan.additionalMinutesPerNight} min/night"
                            )
                            RecoveryMetric(
                                label = "Go to bed",
                                value = "${recoveryPlan.suggestedBedtimeShiftMinutes} min earlier"
                            )
                            RecoveryMetric(
                                label = "Recovery",
                                value = "${recoveryPlan.estimatedRecoveryDays} nights"
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = recoveryPlan.explanation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
    }
}

// ---- Debt summary card ----

@Composable
private fun DebtSummaryCard(
    debtMinutes: Int,
    level: DebtLevel,
    trend: DebtTrend
) {
    val levelColor = when (level) {
        DebtLevel.NONE -> MaterialTheme.colorScheme.primary
        DebtLevel.MILD -> DebtMild
        DebtLevel.MODERATE -> DebtModerate
        DebtLevel.SEVERE -> MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = levelColor.copy(alpha = 0.12f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val debtH = debtMinutes / 60
            val debtM = debtMinutes % 60
            Text(
                text = if (debtMinutes == 0) "No debt!"
                else "${if (debtH > 0) "${debtH}h " else ""}${debtM}m",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = levelColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (debtMinutes == 0) "You are all caught up" else "the accumulated sleep debt",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Level badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(levelColor.copy(alpha = 0.2f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = level.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = levelColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                // Trend
                Text(
                    text = "${trend.glyph} ${trend.displayName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---- 14-day bar chart ----

@Composable
private fun DebtBarChart(days: List<DailyDebt>) {
    val dayFormatter = DateTimeFormatter.ofPattern("d/M")
    val maxHeight = 80.dp
    val surplusColor = MaterialTheme.colorScheme.primary
    val deficitColor = MaterialTheme.colorScheme.error
    val noDataColor = MaterialTheme.colorScheme.surfaceVariant

    // Animated bars
    var chartReady by remember { mutableStateOf(false) }
    LaunchedEffect(days) { chartReady = true }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            days.forEach { day ->
                val fraction = if (day.hasData) {
                    (kotlin.math.abs(day.debtMinutes).toFloat() / 240f).coerceIn(0.05f, 1f)
                } else 0.15f

                val animFraction by animateFloatAsState(
                    targetValue = if (chartReady) fraction else 0f,
                    animationSpec = tween(600),
                    label = "barAnim"
                )

                val barColor = when {
                    !day.hasData -> noDataColor
                    day.hasSurplus -> surplusColor
                    else -> deficitColor
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(maxHeight)
                            .graphicsLayer {
                                scaleY = animFraction
                                transformOrigin = TransformOrigin(0.5f, 1f)
                            }
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(barColor)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = day.date.format(dayFormatter),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ---- Recovery metric ----

@Composable
private fun RecoveryMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}
