package dev.vic41148.somn.feature.onboarding.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.vic41148.somn.core.domain.model.LifeStage
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val femaleLifeStages = listOf(
    LifeStage.CYCLING,
    LifeStage.PREGNANT,
    LifeStage.POSTPARTUM,
    LifeStage.PERIMENOPAUSE,
    LifeStage.MENOPAUSE,
    LifeStage.POST_MENOPAUSE
)

/**
 * Everything the screen can select: an explicit "Default" opt-out card first, then the
 * hormone-driven stages. Previously the screen offered no way to unselect a stage once the user tapped
 * it (a mis-tap was sticky until reinstall/restore). The Default card is the unselect path.
 */
private val lifeStageOptions = listOf(LifeStage.DEFAULT) + femaleLifeStages

@Composable
fun LifeStageScreen(
    selected: LifeStage,
    onSelected: (LifeStage) -> Unit,
    cycleLength: Int,
    onCycleLengthChanged: (Int) -> Unit,
    lastPeriodStart: LocalDate?,
    onLastPeriodStartChanged: (LocalDate) -> Unit,
    pregnancyTrimester: Int?,
    onPregnancyTrimesterChanged: (Int) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Life stage",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Each stage has unique hormonal effects on sleep. We will adjust your scores " +
                "to show what is healthy for your body right now.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        lifeStageOptions.forEach { stage ->
            val isSelected = selected == stage
            OutlinedCard(
                onClick = { onSelected(stage) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
                ),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (isSelected)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stage.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        text = stage.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Cycle-specific inputs
        if (selected == LifeStage.CYCLING) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Cycle details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Cycle length: $cycleLength days",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = cycleLength.toFloat(),
                onValueChange = { onCycleLengthChanged(it.toInt()) },
                valueRange = 21f..35f,
                steps = 13,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    val now = lastPeriodStart ?: LocalDate.now()
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            onLastPeriodStartChanged(LocalDate.of(year, month + 1, day))
                        },
                        now.year,
                        now.monthValue - 1,
                        now.dayOfMonth
                    ).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = lastPeriodStart?.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
                        ?: "When did your last period start?"
                )
            }
        }

        // Pregnancy-specific inputs
        if (selected == LifeStage.PREGNANT) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Which trimester?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (1..3).forEach { trimester ->
                    val isActive = pregnancyTrimester == trimester
                    OutlinedCard(
                        onClick = { onPregnancyTrimesterChanged(trimester) },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(
                            width = if (isActive) 2.dp else 1.dp,
                            color = if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${trimester}${if (trimester == 1) "st" else if (trimester == 2) "nd" else "rd"}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(onClick = onNext) {
                Text("Continue")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
