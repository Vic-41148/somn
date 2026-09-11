package dev.vic41148.somn.feature.analytics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.remember
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
import dev.vic41148.somn.core.ui.components.SlidingPillSelector
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

            // R5: pregnancy/postpartum trend context, phase bands do not apply here.
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

            // Metric selector pill (DATA-03): the standardized sliding pill,
// same as the History range bar.
            SlidingPillSelector(
                options = TrendMetric.entries.map { it.displayName },
                selectedIndex = TrendMetric.entries.indexOf(selectedMetric).coerceAtLeast(0),
                onSelect = { viewModel.selectMetric(TrendMetric.entries[it]) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            TrendRangeRow(
                selectedDays = rangeDays,
                onSelect = { viewModel.selectRange(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            stats?.let { TrendStatsCard(stats = it, metric = selectedMetric) }

            Spacer(modifier = Modifier.height(16.dp))

            // New chart instance per metric and range so the entrance animation replays on every switch.
            key(selectedMetric, rangeDays) {
            val avgValue = remember(sessions, selectedMetric) {
                sessions.map { viewModel.valueFor(it, selectedMetric) }.average().toFloat()
            }
            val scaleSuffix = if (selectedMetric == TrendMetric.SCORE) " (0-100)" else ""
            SleepCard(title = "${selectedMetric.displayName}$scaleSuffix · ${stats?.nights ?: sessions.size} nights") {
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
                    // Age-calibrated deep-sleep window, the same target±5% band that
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
                    averageLine = avgValue,
                    xLabels = listOf(
                        trendDateFormat.format(Date(sessions.first().startTimeMillis)),
                        trendDateFormat.format(Date(sessions.last().startTimeMillis))
                    ),
                    tableEntries = sessions.map {
                        trendDateFormat.format(Date(it.startTimeMillis)) to
                            formatAxisValue(viewModel.valueFor(it, selectedMetric), selectedMetric)
                    },
                    tableDeltaLabel = { delta -> formatDeltaValue(delta, selectedMetric) }
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
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Contributors: what moved under the score, so a dip reads as
            // duration vs efficiency vs deep sleep without switching pills.
            SleepCard(title = "Contributors") {
                val factors = listOf(
                    TrendMetric.SCORE,
                    TrendMetric.DURATION_HOURS,
                    TrendMetric.EFFICIENCY,
                    TrendMetric.DEEP_PERCENT
                )
                factors.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { metric ->
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = metric.displayName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                TrendLineChart(
                                    series = listOf(sessions.map {
                                        TrendPoint(
                                            it.startTimeMillis,
                                            viewModel.valueFor(it, metric)
                                        )
                                    }),
                                    lineColors = listOf(MaterialTheme.colorScheme.secondary),
                                    height = 110.dp,
                                    yLabel = { value -> formatAxisValue(value, metric) }
                                )
                            }
                        }
                        if (row.size < 2) Spacer(modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
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

/** Signed sister of [formatAxisValue] for table deltas and the Change stat. */
private fun formatDeltaValue(value: Float, metric: TrendMetric): String = when (metric) {
    TrendMetric.DURATION_HOURS -> formatTrendDurationSigned(value)
    else -> (if (value >= 0) "+" else "") + formatAxisValue(value, metric)
}

private fun formatTrendDurationSigned(value: Float): String {
    val mins = (value * 60).toInt()
    val sign = if (mins > 0) "+" else ""
    val abs = kotlin.math.abs(mins)
    val body = dev.vic41148.somn.core.domain.usecase.formatDurationShort(abs)
    return if (mins < 0) "-$body" else "$sign$body"
}

@Composable
private fun TrendRangeRow(
    selectedDays: Int?,
    onSelect: (Int?) -> Unit
) {
    val options = listOf(7 to "Week", 30 to "Month", 90 to "3 mo", null to "All")
    SlidingPillSelector(
        options = options.map { it.second },
        selectedIndex = options.indexOfFirst { it.first == selectedDays }.coerceAtLeast(0),
        onSelect = { onSelect(options[it].first) }
    )
}

/** Headline numbers above the chart, the screen used to show a bare line with no values. */
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
