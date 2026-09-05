package dev.vic41148.somn.feature.analytics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import dev.vic41148.somn.core.ui.theme.CycleFollicular
import dev.vic41148.somn.core.ui.theme.CycleLuteal
import dev.vic41148.somn.core.ui.theme.CycleMenstrual
import dev.vic41148.somn.core.ui.theme.CycleOvulation
import dev.vic41148.somn.core.ui.theme.CyclePremenstrual
import dev.vic41148.somn.core.ui.theme.ScoreGood
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.vic41148.somn.core.domain.model.MenstrualCyclePhase
import dev.vic41148.somn.core.ui.components.ColorLegend
import dev.vic41148.somn.core.ui.components.SleepCard
import dev.vic41148.somn.core.ui.components.TrendBand
import dev.vic41148.somn.core.ui.components.TrendLineChart
import dev.vic41148.somn.core.ui.components.TrendPoint
import dev.vic41148.somn.feature.analytics.TrendMetric
import dev.vic41148.somn.feature.analytics.TrendStats
import dev.vic41148.somn.feature.analytics.TrendsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val trendDateFormat = SimpleDateFormat("d MMM", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(
    onBack: () -> Unit,
    viewModel: TrendsViewModel = hiltViewModel()
) {
    val sessions by viewModel.rangedSessions.collectAsState()
    val selectedMetric by viewModel.selectedMetric.collectAsState()
    val rangeDays by viewModel.rangeDays.collectAsState()
    val stats by viewModel.trendStats.collectAsState()
    val cyclePhaseRuns by viewModel.cyclePhaseRuns.collectAsState()
    val lifeStageNote by viewModel.lifeStageBanner.collectAsState()
    val deepSleepTargetPercent by viewModel.deepSleepTargetPercent.collectAsState()
    val deepTarget = deepSleepTargetPercent

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trends") },
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
                .padding(24.dp)
        ) {
            if (sessions.size < 2) {
                Text(
                    text = "Track a few more nights to see trends over time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            // Metric selector dropdown (DATA-03)
            var expanded by remember { mutableStateOf(false) }
            // R5: pregnancy/postpartum trend context — phase bands do not apply here.
            lifeStageNote?.let { note ->
                SleepCard {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedMetric.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Trend Metric") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    TrendMetric.entries.forEach { metric ->
                        DropdownMenuItem(
                            text = { Text(metric.displayName) },
                            onClick = {
                                viewModel.selectMetric(metric)
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TrendRangeRow(
                selectedDays = rangeDays,
                onSelect = { viewModel.selectRange(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            stats?.let { TrendStatsCard(stats = it, metric = selectedMetric) }

            Spacer(modifier = Modifier.height(16.dp))

            SleepCard(title = "${selectedMetric.displayName} · ${stats?.nights ?: sessions.size} nights") {
                val points = sessions.map {
                    TrendPoint(it.startTimeMillis, viewModel.valueFor(it, selectedMetric))
                }
                val bands = cyclePhaseRuns.orEmpty().map { run ->
                    TrendBand(
                        startMillis = run.startMillis,
                        endMillis = run.endMillis,
                        color = run.phase.toBandColor(),
                        label = run.phase.displayName
                    )
                } + if (selectedMetric == TrendMetric.DEEP_PERCENT && deepTarget != null) {
                    // Age-calibrated deep-sleep window — the same target±5% band that
                    // calculateDeepSleepScore marks as ideal, drawn behind the line.
                    listOf(
                        TrendBand(
                            startMillis = 0L,
                            endMillis = 0L,
                            color = ScoreGood.copy(alpha = 0.30f),
                            label = "Target",
                            valueRange = (deepTarget - 5f)..(deepTarget + 5f)
                        )
                    )
                } else {
                    emptyList()
                }

                TrendLineChart(
                    series = listOf(points),
                    lineColors = listOf(MaterialTheme.colorScheme.primary),
                    bands = bands,
                    height = 220.dp,
                    yLabel = { value -> formatAxisValue(value, selectedMetric) },
                    xLabels = listOf(
                        trendDateFormat.format(Date(sessions.first().startTimeMillis)),
                        trendDateFormat.format(Date(sessions.last().startTimeMillis))
                    )
                )
                if (selectedMetric == TrendMetric.DEEP_PERCENT && deepTarget != null) {
                    Text(
                        text = "The shaded band shows your deep sleep target for your age (${"%.0f".format(deepTarget)}% ± 5).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            if (!cyclePhaseRuns.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                CyclePhaseLegend()
            }
        }
    }
}

private fun formatAxisValue(value: Float, metric: TrendMetric): String = when (metric) {
    TrendMetric.SCORE -> "${value.toInt()}"
    TrendMetric.DURATION_HOURS -> "${value.toInt()}h"
    TrendMetric.EFFICIENCY -> "${value.toInt()}%"
    TrendMetric.DEEP_PERCENT -> "${value.toInt()}%"
    TrendMetric.REM_PERCENT -> "${value.toInt()}%"
}

@Composable
private fun TrendRangeRow(
    selectedDays: Int?,
    onSelect: (Int?) -> Unit
) {
    val options = listOf(7 to "Week", 30 to "Month", 90 to "3 mo", null to "All")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (days, label) ->
            FilterChip(
                selected = selectedDays == days,
                onClick = { onSelect(days) },
                label = { Text(label) }
            )
        }
    }
}

/** Headline numbers above the chart — the screen used to show a bare line with no values. */
@Composable
private fun TrendStatsCard(stats: TrendStats, metric: TrendMetric) {
    SleepCard(title = "Now against the average") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TrendMiniStat(value = stats.current, label = "Latest")
            TrendMiniStat(value = stats.average, label = "Average")
            TrendMiniStat(value = stats.best, label = "Best")
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stats.delta,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (stats.deltaPositive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Change",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TrendMiniStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
/** DATA-04: low-alpha so the band reads as context behind the line, not a competing focal color. */
private fun MenstrualCyclePhase.toBandColor(): Color = when (this) {
    MenstrualCyclePhase.MENSTRUAL -> CycleMenstrual.copy(alpha = 0.18f)
    MenstrualCyclePhase.FOLLICULAR -> CycleFollicular.copy(alpha = 0.18f)
    MenstrualCyclePhase.OVULATION -> CycleOvulation.copy(alpha = 0.18f)
    MenstrualCyclePhase.LUTEAL -> CycleLuteal.copy(alpha = 0.18f)
    MenstrualCyclePhase.PREMENSTRUAL -> CyclePremenstrual.copy(alpha = 0.18f)
}

@Composable
private fun CyclePhaseLegend() {
    ColorLegend(
        title = "Cycle phase",
        entries = MenstrualCyclePhase.entries.map { phase ->
            phase.toBandColor().copy(alpha = 1f) to "${phase.displayName} · ${phase.sleepImpact}"
        }
    )
}
