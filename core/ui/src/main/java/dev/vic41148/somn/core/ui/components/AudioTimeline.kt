package dev.vic41148.somn.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.vic41148.somn.core.domain.model.AudioEvent
import dev.vic41148.somn.core.domain.model.AudioEventType
import dev.vic41148.somn.core.ui.theme.AudioEventCough
import dev.vic41148.somn.core.ui.theme.AudioEventSnore
import dev.vic41148.somn.core.ui.theme.AudioEventTalk
import kotlin.math.abs

/** Screen-reader and legend label per audio event type. */
fun AudioEventType.label(): String = when (this) {
    AudioEventType.TALK -> "Talk"
    AudioEventType.SNORE -> "Snore"
    AudioEventType.COUGH -> "Cough"
    AudioEventType.ANOMALY -> "Other"
}

/**
 * Full-session audio event strip: one tappable marker per event, bar height scaled by
 * loudness, full alpha for events that kept a recording. Tapping a marker calls
 * [onEventSelected] — the caller decides whether that plays a clip. Wrap in [SleepCard].
 */
@Composable
fun AudioTimeline(
    events: List<AudioEvent>,
    sessionStartTime: Long,
    sessionDurationMillis: Long,
    startLabel: String,
    endLabel: String,
    modifier: Modifier = Modifier,
    selectedEventId: Long? = null,
    onEventSelected: (AudioEvent) -> Unit = {}
) {
    if (events.isEmpty() || sessionDurationMillis <= 0) return

    val talkColor = AudioEventTalk
    val snoreColor = AudioEventSnore
    val coughColor = AudioEventCough
    val otherColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val selectionColor = MaterialTheme.colorScheme.primary
    fun AudioEvent.color() = when (type) {
        AudioEventType.TALK -> talkColor
        AudioEventType.SNORE -> snoreColor
        AudioEventType.COUGH -> coughColor
        AudioEventType.ANOMALY -> otherColor
    }

    val counts = remember(events) { events.groupingBy { it.type }.eachCount() }
    val summary = remember(events, counts) {
        "Audio timeline: " + AudioEventType.entries.mapNotNull { type ->
            counts[type]?.let { "${it} ${type.label().lowercase()}" }
        }.joinToString(", ")
    }
    val density = LocalDensity.current
    // Custom Canvas drawing never inherits RTL mirroring — mirror the time axis explicitly.
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    fun xFor(timestampMillis: Long, width: Float): Float {
        val fraction = ((timestampMillis - sessionStartTime).toFloat() / sessionDurationMillis)
            .coerceIn(0f, 1f)
        return (if (rtl) 1f - fraction else fraction) * width
    }

    Column(modifier = modifier.fillMaxWidth()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AudioEventType.entries.forEach { type ->
                val count = counts[type] ?: return@forEach
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(10.dp),
                        shape = CircleShape,
                        color = when (type) {
                            AudioEventType.TALK -> talkColor
                            AudioEventType.SNORE -> snoreColor
                            AudioEventType.COUGH -> coughColor
                            AudioEventType.ANOMALY -> otherColor
                        }
                    ) {}
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${type.label()} ($count)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .semantics {
                    contentDescription = summary
                    role = Role.Image
                }
                .pointerInput(events, sessionStartTime, sessionDurationMillis, rtl) {
                    detectTapGestures { tap ->
                        val slopPx = with(density) { 24.dp.toPx() }
                        val hit = events.minByOrNull { event ->
                            abs(tap.x - xFor(event.timestampMillis, size.width.toFloat()))
                        }
                        hit?.let { event ->
                            if (abs(tap.x - xFor(event.timestampMillis, size.width.toFloat())) <= slopPx) {
                                onEventSelected(event)
                            }
                        }
                    }
                }
        ) {
            val barMinPx = with(density) { 6.dp.toPx() }
            drawLine(
                color = gridColor,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 2f
            )
            events.forEach { event ->
                val startX = xFor(event.timestampMillis, size.width)
                val width = maxOf(
                    (event.durationSeconds * 1000f / sessionDurationMillis) * size.width,
                    barMinPx
                )
                // Loudness-scaled bar so a loud cough reads louder than a faint snore.
                val loudness =
                    ((event.intensityDecibels - 30) / 60f).coerceIn(0f, 1f)
                val barHeight = size.height * (0.3f + 0.7f * loudness)
                val hasClip = event.clipPath != null
                drawRoundRect(
                    color = event.color().copy(alpha = if (hasClip) 1f else 0.45f),
                    topLeft = Offset(startX, size.height - barHeight),
                    size = Size(width, barHeight),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                if (event.id == selectedEventId) {
                    drawRoundRect(
                        color = selectionColor,
                        topLeft = Offset(startX - 2f, size.height - barHeight - 2f),
                        size = Size(width + 4f, barHeight + 4f),
                        cornerRadius = CornerRadius(6f, 6f),
                        style = Stroke(width = 3f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = startLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = endLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
