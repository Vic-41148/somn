package dev.vic41148.somn.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import dev.vic41148.somn.core.ui.theme.AudioEventCough
import dev.vic41148.somn.core.ui.theme.AudioEventSnore
import dev.vic41148.somn.core.ui.theme.AudioEventTalk
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.vic41148.somn.core.domain.model.AudioEvent
import dev.vic41148.somn.core.domain.model.AudioEventType
import kotlin.math.max

/**
 * A horizontal, scrollable timeline component for audio events.
 * Supports zoom and pan gestures.
 */
@Composable
fun AudioTimeline(
    events: List<AudioEvent>,
    sessionStartTime: Long,
    sessionDurationMillis: Long,
    modifier: Modifier = Modifier,
    onSeekTo: (Long) -> Unit = {}
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableFloatStateOf(0f) }
    
    val scrollState = rememberScrollState()
    
    // Base width (e.g. 1 pixel per second)
    val baseWidthPx = sessionDurationMillis / 1000f
    
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Audio Timeline",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 10f)
                    }
                }
        ) {
            val totalWidth = (maxWidth.value * scale).dp
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState)
            ) {
                Canvas(
                    modifier = Modifier
                        .width(totalWidth)
                        .fillMaxHeight()
                        .graphicsLayer() // Caches drawing
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, _, _ ->
                                // Handle the tap separately or via click
                            }
                        }
                        .pointerInput(events) {
                            detectTransformGestures { centroid, _, _, _ ->
                                val timestamp = (centroid.x / size.width) * sessionDurationMillis
                                onSeekTo((timestamp + sessionStartTime).toLong())
                            }
                        }
                ) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    
                    // Draw the baseline
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.3f),
                        start = Offset(0f, canvasHeight / 2),
                        end = Offset(canvasWidth, canvasHeight / 2),
                        strokeWidth = 2f
                    )
                    
                    // Visible window logic
                    val scrollOffset = scrollState.value.toFloat()
                    val visibleWidth = size.width / scale
                    
                    // Only draw events that are potentially visible
                    events.forEach { event ->
                        val startX = ((event.timestampMillis - sessionStartTime).toFloat() / sessionDurationMillis) * canvasWidth
                        val eventWidth = (event.durationSeconds.toFloat() * 1000 / sessionDurationMillis) * canvasWidth
                        
                        // Optimized drawing: check if event is in visible range
                        if (startX + eventWidth >= scrollOffset && startX <= scrollOffset + visibleWidth * scale) {
                            val color = when (event.type) {
                                AudioEventType.TALK -> AudioEventTalk
                                AudioEventType.SNORE -> AudioEventSnore
                                AudioEventType.COUGH -> AudioEventCough
                                else -> Color.Gray
                            }
                            
                            val rectHeight = if (event.type == AudioEventType.TALK) canvasHeight * 0.8f else canvasHeight * 0.4f
                            
                            drawRect(
                                color = color.copy(alpha = 0.7f),
                                topLeft = Offset(startX, (canvasHeight - rectHeight) / 2),
                                size = Size(max(4f, eventWidth), rectHeight)
                            )
                        }
                    }
                }
            }
        }
    }
}
