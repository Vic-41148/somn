package dev.vic41148.somn.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor

/** One data point in a [TrendLineChart] series — X is a wall-clock timestamp, not an index. */
data class TrendPoint(val timestampMillis: Long, val value: Float)

/**
 * DATA-04: a colored background band drawn behind the line, e.g. a menstrual cycle phase's
 * date range — [startMillis, endMillis) in the same timestamp space as [TrendPoint]s.
 */
data class TrendBand(val startMillis: Long, val endMillis: Long, val color: Color, val label: String = "")

/**
 * DATA-03: minimal multi-metric-capable trend line chart. Deliberately simple (no axis text
 * rendering, no interaction/tooltips) — callers render their own labels/legend around it, matching
 * this codebase's existing pattern of plain-Canvas components with no charting library dependency
 * (see [Hypnogram]).
 */
@Composable
fun TrendLineChart(
    series: List<List<TrendPoint>>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    lineColors: List<Color> = emptyList(),
    bands: List<TrendBand> = emptyList(),
    strokeWidthDp: Dp = 3.dp
) {
    val allPoints = series.flatten()
    if (allPoints.isEmpty()) return

    val minX = allPoints.minOf { it.timestampMillis }
    val maxX = allPoints.maxOf { it.timestampMillis }
    val minY = minOf(0f, allPoints.minOf { it.value })
    val maxY = allPoints.maxOf { it.value }.let { if (it <= minY) minY + 1f else it }

    // Sorting used to happen inside the Canvas draw lambda, which re-runs every frame of the
    // entrance animation below — that allocated a full sorted copy of every series ~40+ times
    // per chart appearance. Hoisted out so it only runs when the data actually changes.
    val sortedSeries = remember(series) { series.map { points -> points.sortedBy { it.timestampMillis } } }

    // DATA-03: entrance animation — the chart used to draw fully formed in a single Canvas pass
    // with no motion at all. Bands (context) reach full opacity quickly; the line then draws in
    // progressively, segment by segment, left to right — reads as "being plotted," not a fade.
    val progress = remember(series) { Animatable(0f) }
    LaunchedEffect(series) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing))
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val xSpan = (maxX - minX).coerceAtLeast(1L).toFloat()
        val ySpan = (maxY - minY).coerceAtLeast(0.0001f)
        val animatedProgress = progress.value
        // Bands fade in over the first third of the animation, ahead of the line, since they're
        // background context rather than the focal point.
        val bandAlpha = (animatedProgress / 0.35f).coerceIn(0f, 1f)

        fun xFor(timestampMillis: Long): Float =
            ((timestampMillis - minX).toFloat() / xSpan) * size.width

        fun yFor(value: Float): Float =
            size.height - ((value - minY) / ySpan) * size.height

        // Cycle-phase (or other) background bands, drawn first so the line renders on top.
        for (band in bands) {
            val left = xFor(band.startMillis.coerceIn(minX, maxX))
            val right = xFor(band.endMillis.coerceIn(minX, maxX))
            if (right <= left) continue
            drawRect(
                color = band.color.copy(alpha = band.color.alpha * bandAlpha),
                topLeft = Offset(x = left, y = 0f),
                size = Size(width = right - left, height = size.height)
            )
        }

        sortedSeries.forEachIndexed { seriesIndex, sorted ->
            if (sorted.size < 2) return@forEachIndexed
            val color = lineColors.getOrElse(seriesIndex) { Color.Gray }
            val segmentCount = sorted.size - 1

            // Position along the whole polyline, in "segments" — e.g. 2.4 means segments 0 and 1
            // are fully drawn and segment 2 is 40% drawn.
            val drawnSegments = animatedProgress * segmentCount
            val fullyDrawnCount = floor(drawnSegments).toInt().coerceIn(0, segmentCount)

            for (i in 0 until fullyDrawnCount) {
                val start = sorted[i]
                val end = sorted[i + 1]
                drawLine(
                    color = color,
                    start = Offset(xFor(start.timestampMillis), yFor(start.value)),
                    end = Offset(xFor(end.timestampMillis), yFor(end.value)),
                    strokeWidth = strokeWidthDp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            // The partially-drawn segment currently being revealed, if any.
            if (fullyDrawnCount < segmentCount) {
                val partialFraction = (drawnSegments - fullyDrawnCount).coerceIn(0f, 1f)
                if (partialFraction > 0f) {
                    val start = sorted[fullyDrawnCount]
                    val end = sorted[fullyDrawnCount + 1]
                    val startOffset = Offset(xFor(start.timestampMillis), yFor(start.value))
                    val endOffset = Offset(xFor(end.timestampMillis), yFor(end.value))
                    val interpolatedEnd = Offset(
                        x = startOffset.x + (endOffset.x - startOffset.x) * partialFraction,
                        y = startOffset.y + (endOffset.y - startOffset.y) * partialFraction
                    )
                    drawLine(
                        color = color,
                        start = startOffset,
                        end = interpolatedEnd,
                        strokeWidth = strokeWidthDp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // Points appear once the line has reached them (their own segment index <= drawn count).
            sorted.forEachIndexed { pointIndex, point ->
                if (pointIndex <= fullyDrawnCount) {
                    drawCircle(
                        color = color,
                        radius = strokeWidthDp.toPx(),
                        center = Offset(xFor(point.timestampMillis), yFor(point.value))
                    )
                }
            }
        }
    }
}
