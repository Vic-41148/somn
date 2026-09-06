package dev.vic41148.somn.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/** One data point in a [TrendLineChart] series — X is a wall-clock timestamp, not an index. */
data class TrendPoint(val timestampMillis: Long, val value: Float)

/**
 * DATA-04: a colored background band drawn behind the line, e.g. a menstrual cycle phase's
 * date range — [startMillis, endMillis) in the same timestamp space as [TrendPoint]s.
 * Set [valueRange] instead to draw a full-width band between two Y values (e.g. the age
 * calibrated deep-sleep target window), for metrics where the band is value-driven, not
 * date-driven.
 */
data class TrendBand(
    val startMillis: Long,
    val endMillis: Long,
    val color: Color,
    val label: String = "",
    /** When set, the band spans the chart's full width at these plot values instead of an X-range. */
    val valueRange: ClosedFloatingPointRange<Float>? = null
)

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
    strokeWidthDp: Dp = 3.dp,
    /** Formats a Y value for the axis labels — callers pass metric-aware formatting. */
    yLabel: (Float) -> String = { it.toInt().toString() },
    /** [first, last] date captions drawn under the chart's left/right edges. */
    xLabels: List<String> = emptyList()
) {
    val allPoints = series.flatten()
    if (allPoints.isEmpty()) return

    val minX = allPoints.minOf { it.timestampMillis }
    val maxX = allPoints.maxOf { it.timestampMillis }
    val minVal = allPoints.minOf { it.value }
    val maxVal = allPoints.maxOf { it.value }
    val span = (maxVal - minVal).coerceAtLeast(1f)
    val minY = if (minVal >= 0f) max(0f, minVal - span * 0.15f) else minVal - span * 0.15f
    val maxY = maxVal + span * 0.15f

    // DATA-03: entrance animation — the chart used to draw fully formed in a single Canvas pass
    // with no motion at all. Bands (context) reach full opacity quickly; the line then draws in
    // progressively, segment by segment, left to right — reads as "being plotted," not a fade.
    val progress = remember(series) { Animatable(0f) }
    LaunchedEffect(series) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing))
    }

    // Sort once per series change, not inside the Canvas draw scope below. That scope runs on
    // every animation frame (~42 times over the 700ms entrance). Re-sorting that often wastes
    // UI-thread work every frame, and the visible cause of dropped frames on this
    // screen for any real amount of trend data.
    val sortedSeries = remember(series) { series.map { it.sortedBy { point -> point.timestampMillis } } }

    val textMeasurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    // Time-series Canvas drawing never inherits RTL mirroring — mirror the X mapping and
    // the edge captions explicitly. The Y gutter stays left; only the time axis flips.
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val labelStyle = TextStyle(color = axisColor, fontSize = 11.sp)
        // Reserve room for the Y labels on the left and date captions at the bottom.
        val yGutter = 40.dp.toPx()
        val xGutter = if (xLabels.isNotEmpty()) 18.dp.toPx() else 0f
        val plotWidth = (size.width - yGutter).coerceAtLeast(1f)
        val plotHeight = (size.height - xGutter).coerceAtLeast(1f)
        val xSpan = (maxX - minX).coerceAtLeast(1L).toFloat()
        val ySpan = (maxY - minY).coerceAtLeast(0.0001f)
        val animatedProgress = progress.value
        // Bands fade in over the first third of the animation, ahead of the line, since they're
        // background context rather than the focal point.
        val bandAlpha = (animatedProgress / 0.35f).coerceIn(0f, 1f)

        fun xFor(timestampMillis: Long): Float {
            val fraction = ((timestampMillis - minX).toFloat() / xSpan).coerceIn(0f, 1f)
            return yGutter + (if (rtl) 1f - fraction else fraction) * plotWidth
        }

        fun yFor(value: Float): Float =
            plotHeight - ((value - minY) / ySpan) * plotHeight

        // Horizontal gridlines + Y labels at min/mid/max so the line's scale reads at a glance.
        // Previously the chart drew a bare line with no scale at all — a 47-to-36 drop looked
        // identical to a 90-to-85 one.
        val gridValues = listOf(minY, (minY + maxY) / 2f, maxY)
        for (gridValue in gridValues) {
            val y = yFor(gridValue)
            drawLine(
                color = gridColor,
                start = Offset(x = yGutter, y = y),
                end = Offset(x = size.width, y = y),
                strokeWidth = 1.dp.toPx()
            )
            val measured = textMeasurer.measure(text = yLabel(gridValue), style = labelStyle)
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(x = 0f, y = (y - measured.size.height / 2f).coerceIn(0f, plotHeight))
            )
        }

        // Date captions under the left/right edges (swapped in RTL to match the mirrored axis).
        if (xLabels.isNotEmpty()) {
            val (edgeFirst, edgeLast) = if (rtl) {
                xLabels.last() to xLabels.first()
            } else {
                xLabels.first() to xLabels.last()
            }
            val first = textMeasurer.measure(text = edgeFirst, style = labelStyle)
            drawText(
                textLayoutResult = first,
                topLeft = Offset(x = yGutter, y = plotHeight + 4.dp.toPx())
            )
            if (xLabels.size > 1) {
                val last = textMeasurer.measure(text = edgeLast, style = labelStyle)
                drawText(
                    textLayoutResult = last,
                    topLeft = Offset(
                        x = (size.width - last.size.width).coerceAtLeast(yGutter),
                        y = plotHeight + 4.dp.toPx()
                    )
                )
            }
        }

        // Cycle-phase (or other) background bands, drawn first so the line renders on top.
        for (band in bands) {
            if (band.valueRange != null) {
                val yBottom = yFor(band.valueRange.start)
                val yTop = yFor(band.valueRange.endInclusive)
                val top = minOf(yTop, yBottom)
                drawRect(
                    color = band.color.copy(alpha = band.color.alpha * bandAlpha),
                    topLeft = Offset(x = yGutter, y = top),
                    size = Size(width = plotWidth, height = abs(yBottom - yTop))
                )
                continue
            }
            val left = xFor(band.startMillis.coerceIn(minX, maxX))
            val right = xFor(band.endMillis.coerceIn(minX, maxX))
            if (right <= left) continue
            drawRect(
                color = band.color.copy(alpha = band.color.alpha * bandAlpha),
                topLeft = Offset(x = left, y = 0f),
                size = Size(width = right - left, height = plotHeight)
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
