package dev.vic41148.somn.feature.analytics.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.vic41148.somn.core.domain.usecase.ReportWindow
import dev.vic41148.somn.core.domain.usecase.buildPeriodReport
import dev.vic41148.somn.core.domain.usecase.formatDurationShort
import dev.vic41148.somn.core.domain.usecase.toReportPdfModel
import dev.vic41148.somn.core.ui.components.MetricChip
import dev.vic41148.somn.core.ui.components.PillRow
import dev.vic41148.somn.core.ui.components.SleepCard
import dev.vic41148.somn.feature.analytics.AnalyticsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * R3 Reports: in-app weekly/monthly/year-in-review screens plus on-device PDF
 * export. Anniversary = YEAR window. Same engine, all-time data, nothing paywalled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: AnalyticsViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessions by viewModel.sessions.collectAsState()
    val restModeSince by viewModel.restModeSince.collectAsState()
    var window by remember { mutableStateOf(ReportWindow.WEEK) }
    var status by remember { mutableStateOf<String?>(null) }
    var generating by remember { mutableStateOf(false) }

    val report = remember(sessions, window, restModeSince) {
        buildPeriodReport(sessions, window, restModeSince)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports") },
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
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PillRow {
                ReportWindow.entries.forEach { w ->
                    FilterChip(
                        selected = window == w,
                        onClick = { window = w },
                        label = {
                            Text(
                                when (w) {
                                    ReportWindow.WEEK -> "Week"
                                    ReportWindow.MONTH -> "Month"
                                    ReportWindow.YEAR -> "Year"
                                }
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            val summary = report.summary
            if (summary == null) {
                SleepCard {
                    Text(
                        "No nights in this period yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Track sleep. Your ${window.title.lowercase()} appears here. " +
                            "Reports stay available as long as your data does.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                SleepCard {
                    Text(report.windowLabel, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${summary.nights} nights · ${report.calibration}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                PillRow {
                    MetricChip(
                        label = "Average score",
                        value = "${summary.avgScore}",
                        modifier = Modifier.weight(1f)
                    )
                    MetricChip(
                        label = "Average sleep",
                        value = formatDurationShort(summary.avgDurationMinutes),
                        modifier = Modifier.weight(1f)
                    )
                    MetricChip(
                        label = "Efficiency",
                        value = "${summary.avgEfficiencyPercent}%",
                        modifier = Modifier.weight(1f)
                    )
                }
                PillRow {
                    MetricChip(
                        label = "Total sleep",
                        value = formatDurationShort(summary.totalSleepMinutes),
                        modifier = Modifier.weight(1f)
                    )
                    MetricChip(
                        label = "Best night",
                        value = "${summary.bestScore}",
                        modifier = Modifier.weight(1f)
                    )
                    MetricChip(
                        label = "Trend",
                        value = when {
                            summary.scoreDelta > 0 -> "+${summary.scoreDelta}"
                            else -> "${summary.scoreDelta}"
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            generating = true
                            status = null
                            scope.launch {
                                runCatching {
                                    val model = toReportPdfModel(
                                        report,
                                        viewModel.tagNamesFor(
                                            sessions.filter { it.isCompleted }
                                                .map { it.id }
                                        )
                                    )
                                    withContext(Dispatchers.IO) {
                                        ReportPdfRenderer.render(
                                            context,
                                            model,
                                            report.scoreTrend,
                                            "somn-${window.name.lowercase()}-report.pdf"
                                        )
                                    }
                                }.onSuccess {
                                    status = "The app saved the file. Share it from any file manager. Or use Share below."
                                    generating = false
                                }.onFailure { e ->
                                    status = "The export failed: ${e.message}"
                                    generating = false
                                }
                            }
                        },
                        enabled = !generating,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (generating) "Saving…" else "Save PDF") }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                runCatching {
                                    val model = toReportPdfModel(
                                        report,
                                        viewModel.tagNamesFor(
                                            sessions.filter { it.isCompleted }
                                                .map { it.id }
                                        )
                                    )
                                    val file = withContext(Dispatchers.IO) {
                                        ReportPdfRenderer.render(
                                            context,
                                            model,
                                            report.scoreTrend,
                                            "somn-${window.name.lowercase()}-report.pdf"
                                        )
                                    }
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    context.startActivity(
                                        Intent.createChooser(
                                            Intent(Intent.ACTION_SEND).apply {
                                                type = "application/pdf"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            },
                                            "Share the sleep report"
                                        )
                                    )
                                }.onFailure { e ->
                                    status = "The share failed: ${e.message}"
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Share PDF") }
                }
            }
            status?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
