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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import dev.vic41148.somn.feature.analytics.TrendsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(
    onBack: () -> Unit,
    viewModel: TrendsViewModel = hiltViewModel()
) {
    val sessions by viewModel.sessions.collectAsState()
    val selectedMetric by viewModel.selectedMetric.collectAsState()
    val cyclePhaseRuns by viewModel.cyclePhaseRuns.collectAsState()
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

            // Metric selector (DATA-03) — one metric at a time, they don't share a scale.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TrendMetric.entries.forEach { metric ->
                    FilterChip(
                        selected = selectedMetric == metric,
                        onClick = { viewModel.selectMetric(metric) },
                        label = { Text(metric.displayName) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SleepCard(title = selectedMetric.displayName) {
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
                    height = 220.dp
                )
                if (selectedMetric == TrendMetric.DEEP_PERCENT && deepTarget != null) {
                    Text(
                        text = "Shaded band: your age-adjusted deep sleep target (${"%.0f".format(deepTarget)}% ± 5).",
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
