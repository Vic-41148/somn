package dev.vic41148.somn.feature.habits.ui

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.vic41148.somn.core.domain.usecase.CorrelationConfidence
import dev.vic41148.somn.core.domain.usecase.CorrelationResult
import dev.vic41148.somn.core.domain.usecase.CorrelationStrength
import dev.vic41148.somn.core.domain.usecase.CorrelationUseCase
import dev.vic41148.somn.core.domain.usecase.ShiftFlag
import dev.vic41148.somn.core.domain.usecase.TagImpact
import dev.vic41148.somn.core.domain.usecase.maturityLabel
import dev.vic41148.somn.feature.habits.HabitViewModel
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorrelationInsightsScreen(
    onBack: () -> Unit = {},
    viewModel: HabitViewModel = hiltViewModel()
) {
    val report by viewModel.correlationReport.collectAsState()
    val shiftFlags by viewModel.shiftFlags.collectAsState()
    val tagImpacts by viewModel.tagImpacts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // This screen had no TopAppBar/back button and was not reachable from anywhere in the app.
    // The route existed in the nav graph but nothing ever called navigate() to it. As a result
    // this finished feature (habit-to-sleep correlation insights) was entirely dead to users.
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your Patterns") },
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
            text = "How your habits connect to your sleep",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
            ),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Text(
                text = "These patterns are personal to you — not population averages. " +
                    "Minimum ${CorrelationUseCase.MIN_DATA_POINTS} sleep sessions needed per correlation. " +
                    "Findings from fewer nights are a tentative early read — they firm up as you log more.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Was `if (isLoading) { ...; return }` followed by `!report?.hasAnyData!!` — if
        // loadDebtAndCorrelations() ever hit its catch block before report was first assigned
        // (isLoading still gets set back to false there), report stays null forever and
        // `null!!` crashed this screen with an NPE. Guard on report == null directly instead.
        if (isLoading || report == null) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }
        val safeReport = report!!

        val correlations = safeReport.availableCorrelations

        if (!safeReport.hasAnyData) {
            EmptyCorrelationsState()
        } else {
            // R4: material moves first. The code flags it without being asked.
            shiftFlags.forEach { flag ->
                ShiftFlagCard(flag = flag)
                Spacer(modifier = Modifier.height(12.dp))
            }
            correlations.forEach { result ->
                CorrelationCard(result = result)
                Spacer(modifier = Modifier.height(12.dp))
            }
            // R4: tag presence as binary predictors next to the big-four habits.
            tagImpacts.forEach { impact ->
                TagImpactCard(impact = impact)
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Deliberate empty state: one InsufficientDataCard per correlation with
            // too few nights to compute a result (not a placeholder).
            listOf(
                "Caffeine → Sleep Onset" to report?.caffeineToOnset,
                "Alcohol → Efficiency" to report?.alcoholToEfficiency,
                "Stress → Wake Events" to report?.stressToWakes,
                "Exercise → Sleep Score" to report?.exerciseToScore
            ).filter { (_, result) -> result == null }.forEach { (label, _) ->
                InsufficientDataCard(label = label)
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
    }
}

@Composable
private fun CorrelationCard(result: CorrelationResult) {
    val barColor by animateColorAsState(
        targetValue = barColorFor(result.strength, result.isPositive),
        label = "barColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${result.factor} → ${result.outcomeMetric}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                StrengthBadge(strength = result.strength, isPositive = result.isPositive)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Correlation bar
            val barFraction = abs(result.correlation).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(barFraction)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(barColor)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("r = ${"%.2f".format(result.correlation)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Sample size + maturity: a strong r from 7 nights is still an early
                // read, the same r from 90 nights is settled (CorrelationConfidence).
                Text(
                    text = "n = ${result.dataPoints} · ${result.confidence.maturityLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = when (result.confidence) {
                        CorrelationConfidence.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
                        CorrelationConfidence.MEDIUM -> MaterialTheme.colorScheme.secondary
                        CorrelationConfidence.HIGH -> MaterialTheme.colorScheme.primary
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Insight
            Text(
                text = result.insight,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Diverging scale, direction-aware at every strength: positive correlations read as
 * "helpful" (primary), negative as "undesirable" (error), with an alpha ramp so MILD
 * reads clearly under MODERATE and STRONG. It matches the score-ring colour language.
 */
@Composable
private fun barColorFor(strength: CorrelationStrength, isPositive: Boolean): Color = when (strength) {
    CorrelationStrength.NONE -> MaterialTheme.colorScheme.surfaceVariant
    CorrelationStrength.MILD -> directionColor(isPositive).copy(alpha = 0.4f)
    CorrelationStrength.MODERATE -> directionColor(isPositive).copy(alpha = 0.7f)
    CorrelationStrength.STRONG -> directionColor(isPositive)
}

@Composable
private fun containerFor(isPositive: Boolean): Color =
    if (isPositive) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.errorContainer

@Composable
private fun onContainerFor(isPositive: Boolean): Color =
    if (isPositive) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onErrorContainer

@Composable
private fun directionColor(isPositive: Boolean): Color =
    if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

@Composable
private fun StrengthBadge(strength: CorrelationStrength, isPositive: Boolean) {
    val bgColor = when (strength) {
        CorrelationStrength.NONE -> MaterialTheme.colorScheme.surfaceVariant
        CorrelationStrength.MILD ->
            containerFor(isPositive).copy(alpha = 0.45f)
        CorrelationStrength.MODERATE ->
            containerFor(isPositive).copy(alpha = 0.7f)
        CorrelationStrength.STRONG -> containerFor(isPositive)
    }
    val textColor = when (strength) {
        CorrelationStrength.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
        CorrelationStrength.MILD ->
            onContainerFor(isPositive).copy(alpha = 0.6f)
        CorrelationStrength.MODERATE ->
            onContainerFor(isPositive).copy(alpha = 0.8f)
        CorrelationStrength.STRONG -> onContainerFor(isPositive)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = strength.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** R4 shift flag: a material move. The code surfaces it without being asked. */
@Composable
private fun ShiftFlagCard(flag: ShiftFlag) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Heads up",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = flag.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = flag.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

/** R4 tag read: binary predictor with its sample on both sides. */
@Composable
private fun TagImpactCard(impact: TagImpact) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tag · ${impact.tagName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = impact.insight,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${impact.taggedAvgScore} tagged scores vs ${impact.untaggedAvgScore} untagged scores",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InsufficientDataCard(label: String) {    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Log data for ${CorrelationUseCase.MIN_DATA_POINTS}+ nights",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("No data", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmptyCorrelationsState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.BarChart,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Not enough data yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Log your habits for ${CorrelationUseCase.MIN_DATA_POINTS}+ nights to reveal your personal sleep patterns.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
