package dev.vic41148.somn.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Small generic progress ring with a center value and a caption underneath — the building block
 * for the "how is each stat doing" strips on Home and History. Unlike [SleepScoreRing] (which
 * owns the 0-100 score color language), the caller supplies [color] so duration/efficiency/
 * consistency rings can each use their own semantic color.
 *
 * @param fraction 0f..1f progress to display; coerced into range.
 */
@Composable
fun StatRing(
    label: String,
    value: String,
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 84.dp,
    strokeWidth: Dp = 9.dp,
    sublabel: String? = null,
    /** When set, the ring becomes a button announcing "Show details for <label>". */
    onClick: (() -> Unit)? = null,
    /** When false, the caption under the ring is hidden and all text sits in one column. */
    showLabel: Boolean = true
) {
    var target by remember { mutableFloatStateOf(0f) }
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 900),
        label = "statRing"
    )
    LaunchedEffect(fraction) { target = fraction.coerceIn(0f, 1f) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = (if (onClick != null) {
            modifier.clickable(
                onClickLabel = "Show details for $label",
                role = Role.Button,
                onClick = onClick
            )
        } else modifier).semantics(mergeDescendants = true) {
            contentDescription = "$label: $value" + (sublabel?.let { ", $it" } ?: "")
        }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
            Canvas(modifier = Modifier.size(size)) {
                val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
                drawArc(
                    color = color.copy(alpha = 0.15f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = stroke
                )
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    style = stroke
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                if (sublabel != null) {
                    Text(
                        text = sublabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
