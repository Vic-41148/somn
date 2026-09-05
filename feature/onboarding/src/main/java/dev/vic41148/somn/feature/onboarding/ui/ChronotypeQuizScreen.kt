package dev.vic41148.somn.feature.onboarding.ui

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
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.vic41148.somn.core.domain.model.Chronotype

/**
 * Reduced Morningness-Eveningness Questionnaire (rMEQ).
 * 5 questions that produce a score mapping to Chronotype.
 *
 * Research doc §2.11: Chronotype is neurologically determined,
 * encoded in circadian genes (PNAS, WashU, 2025).
 */

data class MeqQuestion(
    val question: String,
    val options: List<MeqOption>
)

data class MeqOption(
    val text: String,
    val score: Int
)

private val meqQuestions = listOf(
    MeqQuestion(
        question = "With no commitments, what time do you naturally wake?",
        options = listOf(
            MeqOption("Before 6:30 AM", 5),
            MeqOption("6:30 – 7:45 AM", 4),
            MeqOption("7:45 – 9:45 AM", 3),
            MeqOption("9:45 – 11:00 AM", 2),
            MeqOption("After 11:00 AM", 1)
        )
    ),
    MeqQuestion(
        question = "With no commitments, what time do you go to bed?",
        options = listOf(
            MeqOption("Before 9:00 PM", 5),
            MeqOption("9:00 – 10:15 PM", 4),
            MeqOption("10:15 PM – 12:30 AM", 3),
            MeqOption("12:30 – 1:45 AM", 2),
            MeqOption("After 1:45 AM", 1)
        )
    ),
    MeqQuestion(
        question = "How alert do you feel in the first 30 minutes after waking?",
        options = listOf(
            MeqOption("Very alert", 4),
            MeqOption("Fairly alert", 3),
            MeqOption("Fairly groggy", 2),
            MeqOption("Very groggy", 1)
        )
    ),
    MeqQuestion(
        question = "At what time of day do you feel your best?",
        options = listOf(
            MeqOption("Morning (8–10 AM)", 5),
            MeqOption("Late morning (11 AM – 1 PM)", 4),
            MeqOption("Afternoon (1–5 PM)", 3),
            MeqOption("Evening (5–9 PM)", 2),
            MeqOption("Night (after 9 PM)", 1)
        )
    ),
    MeqQuestion(
        question = "Are you a morning or an evening person?",
        options = listOf(
            MeqOption("Definitely morning", 6),
            MeqOption("More morning than evening", 4),
            MeqOption("Neither", 2),
            MeqOption("More evening than morning", 1),
            MeqOption("Definitely evening", 0)
        )
    )
)

@Composable
fun ChronotypeQuizScreen(
    answers: Map<Int, Int>,
    chronotype: Chronotype,
    onAnswer: (Int, Int) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
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
            text = "Your chronotype",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Your circadian genes set your chronotype. It is not a lifestyle choice. " +
                "We will score your consistency against your natural pattern, not a universal ideal.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        meqQuestions.forEachIndexed { qIndex, question ->
            Text(
                text = "${qIndex + 1}. ${question.question}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            question.options.forEach { option ->
                val isSelected = answers[qIndex] == option.score
                OutlinedCard(
                    onClick = { onAnswer(qIndex, option.score) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
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
                    Text(
                        text = option.text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Show result if all answered
        if (chronotype != Chronotype.UNKNOWN) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Your chronotype: ${chronotype.displayName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "We will calibrate your sleep targets and the consistency scoring to match.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onNext) {
                Text("Skip")
            }
            Button(
                onClick = onNext,
                enabled = answers.size == meqQuestions.size
            ) {
                Text("Continue")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
