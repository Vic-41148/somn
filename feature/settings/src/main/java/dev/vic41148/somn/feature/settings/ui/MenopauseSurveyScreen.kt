package dev.vic41148.somn.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.vic41148.somn.core.domain.usecase.MENOPAUSE_QUESTIONS
import dev.vic41148.somn.core.domain.usecase.MenoBand
import dev.vic41148.somn.core.domain.usecase.scoreMenopause
import dev.vic41148.somn.core.ui.components.PillRow
import dev.vic41148.somn.core.ui.components.SleepCard
import dev.vic41148.somn.feature.settings.SettingsViewModel

private val MENO_OPTIONS = listOf("Not at all", "A little", "Quite a bit", "A lot")

/**
 * R5 menopause check-in: Oura-Menopause-Impact-Scale mechanics, Somn-shortened to 10
 * sleep-relevant questions. Pure UI over prefs storage — wellness information, and the
 * top band says "mention to a doctor" because effective treatments exist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenopauseSurveyScreen(
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val saved by viewModel.menoAnswers.collectAsState()
    var answers by remember(saved) {
        mutableStateOf(saved ?: List(MENOPAUSE_QUESTIONS.size) { -1 })
    }
    val complete = answers.all { it >= 0 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Menopause check-in") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SleepCard {
                Text(
                    "How much has each bothered you in the last 2 weeks?",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            MENOPAUSE_QUESTIONS.forEachIndexed { i, q ->
                SleepCard(title = q.text) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MENO_OPTIONS.indices.chunked(2).forEach { pair ->
                            PillRow {
                                pair.forEach { value ->
                                    FilterChip(
                                        selected = answers[i] == value,
                                        onClick = {
                                            answers = answers.toMutableList().also { it[i] = value }
                                        },
                                        label = { Text(MENO_OPTIONS[value]) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Button(
                onClick = { viewModel.saveMenoAnswers(answers) },
                enabled = complete,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save check-in") }
            if (complete) {
                val band: MenoBand = scoreMenopause(answers)
                SleepCard(title = "Your result: ${band.displayName}") {
                    Text(
                        band.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
