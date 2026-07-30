package dev.vic41148.somn.feature.analytics.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.vic41148.somn.core.domain.model.MenstrualCyclePhase
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
                }

                TrendLineChart(
                    series = listOf(points),
                    lineColors = listOf(MaterialTheme.colorScheme.primary),
                    bands = bands,
                    height = 220.dp
                )
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
    MenstrualCyclePhase.MENSTRUAL -> Color(0xFFE57373).copy(alpha = 0.18f)
    MenstrualCyclePhase.FOLLICULAR -> Color(0xFF81C784).copy(alpha = 0.18f)
    MenstrualCyclePhase.OVULATION -> Color(0xFFFFD54F).copy(alpha = 0.18f)
    MenstrualCyclePhase.LUTEAL -> Color(0xFF64B5F6).copy(alpha = 0.18f)
    MenstrualCyclePhase.PREMENSTRUAL -> Color(0xFFBA68C8).copy(alpha = 0.18f)
}

@Composable
private fun CyclePhaseLegend() {
    Column {
        Text(
            text = "Cycle phase",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        MenstrualCyclePhase.entries.forEach { phase ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(phase.toBandColor().copy(alpha = 1f), RoundedCornerShape(3.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${phase.displayName} — ${phase.sleepImpact}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}
