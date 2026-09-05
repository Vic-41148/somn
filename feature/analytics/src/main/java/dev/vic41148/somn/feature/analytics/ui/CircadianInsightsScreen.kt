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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.vic41148.somn.core.domain.model.AssessmentConfidence
import dev.vic41148.somn.core.domain.model.ChronotypeAssessment
import dev.vic41148.somn.core.domain.model.SeasonalAnalysis
import dev.vic41148.somn.core.domain.model.SocialJetLag
import dev.vic41148.somn.core.ui.components.SleepCard
import dev.vic41148.somn.feature.analytics.CircadianViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircadianInsightsScreen(
    viewModel: CircadianViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val chronotype by viewModel.chronotypeAssessment.collectAsState()
    val socialJetLag by viewModel.socialJetLag.collectAsState()
    val seasonalAnalysis by viewModel.seasonalAnalysis.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    // Previously any exception in chronotype/social-jetlag/seasonal analysis was caught and
    // silently discarded (`// Ignore for now`) — the screen would just show stale/empty state
    // forever. Now surfaced as a Snackbar, matching the same pattern used elsewhere in the app.
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Circadian Insights") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isLoading && chronotype == null && socialJetLag == null && seasonalAnalysis == null) {
                SleepCard(title = "The app reads your rhythm") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "The app reviews your recent nights.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            chronotype?.let { ChronotypeCard(it) }
            val lag = socialJetLag
            if (lag != null) SocialJetLagCard(lag) else SocialJetLagPendingCard()
            seasonalAnalysis?.let { SeasonalAnalysisCard(it) }
            if (!isLoading && chronotype == null && socialJetLag == null && seasonalAnalysis == null) {
                SleepCard(title = "Not enough nights yet") {
                    Text(
                        text = "Track a few more nights. Include at least one weekend night. " +
                            "Your chronotype, social jet lag, and seasonal patterns appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun ChronotypeCard(assessment: ChronotypeAssessment) {
    SleepCard(title = "Chronotype") {
            Text("Base: ${assessment.questionnaireBased.displayName}", style = MaterialTheme.typography.bodyLarge)

            Spacer(modifier = Modifier.height(8.dp))
            if (assessment.confidence != AssessmentConfidence.INSUFFICIENT) {
                Text("Data-Driven: ${assessment.dataDriven?.displayName ?: "Unknown"}")
                assessment.dataDrivenMidpoint?.let {
                    Text("Natural Midpoint: $it")
                }
                Text("Confidence: ${assessment.confidence.displayName} (${assessment.alarmFreeNightsUsed} nights)")
                Text("Agreement: ${assessment.agreementStatus.displayName}")
            } else {
                val progress = (assessment.alarmFreeNightsUsed.toFloat() /
                    ChronotypeAssessment.MIN_ALARM_FREE_NIGHTS).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "${assessment.alarmFreeNightsUsed}/${ChronotypeAssessment.MIN_ALARM_FREE_NIGHTS} " +
                        "alarm-free nights so far. Keep tracking. The data-driven result unlocks here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
    }
}

@Composable
fun SocialJetLagCard(lag: SocialJetLag) {
    SleepCard(title = "Social Jet Lag") {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Weekday Midpoint:")
                Text("${lag.weekdayMidpoint}")
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Weekend Midpoint:")
                Text("${lag.weekendMidpoint}")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Difference: ${lag.jetLagMinutes} minutes", fontWeight = FontWeight.Bold)
            Text("Risk Level: ${lag.riskLevel.displayName}")
            Spacer(modifier = Modifier.height(8.dp))
            Text(lag.insight, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Shown instead of nothing when there isn't weekday+weekend data yet to compute the lag. */
@Composable
private fun SocialJetLagPendingCard() {
    SleepCard(title = "Social Jet Lag") {
        Text(
            text = "The app needs nights on weekdays and weekends to compare your midpoints. " +
                "Weekend tracking fills this gap.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SeasonalAnalysisCard(analysis: SeasonalAnalysis) {
    SleepCard(title = "Seasonal Trends") {
            Text("Current Season: ${analysis.currentSeason.displayName}")
            Spacer(modifier = Modifier.height(8.dp))

            if (analysis.hasMultiSeasonData) {
                analysis.currentTrend?.let { trend ->
                    Text("Trend: ${trend.type.displayName}", fontWeight = FontWeight.Bold)
                    Text(trend.insight, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                 Text(analysis.insight ?: "There is not enough cross-season data yet.")
            }
    }
}
