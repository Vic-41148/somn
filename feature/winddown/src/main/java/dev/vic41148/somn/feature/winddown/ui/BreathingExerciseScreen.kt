package dev.vic41148.somn.feature.winddown.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreathingExerciseScreen(
    onBack: () -> Unit = {}
) {
    var phase by remember { mutableStateOf("Breathe In") }
    val haptic = LocalHapticFeedback.current

    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val animatedSize by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "animatedSize"
    )

    LaunchedEffect(Unit) {
        // 4-7-8 Breathing logic
        while (true) {
            phase = "Breathe In" // 4 seconds
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(4000)
            
            phase = "Hold" // 7 seconds
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            delay(7000)
            
            phase = "Exhale" // 8 seconds
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(8000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Breathing Exercise") },
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
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = phase,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(64.dp))

            Canvas(modifier = Modifier.size(200.dp)) {
                drawCircle(
                    color = Color.Cyan.copy(alpha = 0.3f),
                    radius = animatedSize * (size.width / 2f),
                    center = Offset(size.width / 2f, size.height / 2f)
                )
                drawCircle(
                    color = Color.Cyan,
                    radius = animatedSize * (size.width / 2f),
                    center = Offset(size.width / 2f, size.height / 2f),
                    style = Stroke(width = 4.dp.toPx())
                )
            }
        }
    }
}
