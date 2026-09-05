package dev.vic41148.somn.feature.onboarding.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.vic41148.somn.core.domain.model.BiologicalSex

@Composable
fun BiologicalSexScreen(
    selected: BiologicalSex,
    onSelected: (BiologicalSex) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    // Body scrolls, footer stays pinned. This was one unscrollable Column. A weight(1f) Spacer
    // held the footer down. This worked until the content above outgrew the viewport (a large
    // system font scale, or the optional sections below expanding). Then the spacer
    // collapsed to zero and pushed the buttons off the bottom with no way to scroll to them.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Biological sex",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "This enables hormonal context for your sleep. For example, menstrual cycles " +
                "directly alter sleep architecture — we can adjust your scores accordingly.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        BiologicalSex.entries.forEach { sex ->
            val isSelected = selected == sex
            OutlinedCard(
                onClick = { onSelected(sex) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
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
                        text = sex.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

            Spacer(modifier = Modifier.height(24.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Button(onClick = onNext) {
                Text("Continue")
            }
        }
    }
}
