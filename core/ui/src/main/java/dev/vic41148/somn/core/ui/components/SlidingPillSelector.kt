package dev.vic41148.somn.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * The standardized big pill: one sliding thumb in primary/onPrimary (the dock
 * bubble language) over a solid track. Taps select directly, horizontal drags
 * slide the thumb under the finger and snap to the nearest segment on release.
 * Labels preview the segment under the finger and crossfade with the glide.
 */
@Composable
fun SlidingPillSelector(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeIndex = selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0))
    val density = LocalDensity.current
    // NaN = not dragging, a pixel offset into the content while a drag is live.
    var dragOffsetPx by remember { mutableFloatStateOf(Float.NaN) }
    // Release bookkeeping: the px we let go at plus the index we committed.
    // Held until the caller's selectedIndex catches up, so the thumb never
    // detours back through the old segment.
    var settlePx by remember { mutableFloatStateOf(0f) }
    var settleIndex by remember { mutableStateOf<Int?>(null) }
    // Row height in px: the overlay thumb copies it explicitly, fillMaxHeight
    // collapses to zero inside unbounded-height list items.
    var rowHeightPx by remember { mutableIntStateOf(0) }
    // One continuous position source in px (gesture math already lives in px):
    // the finger owns it mid-drag, a snap owns the release, the spring owns
    // taps. Splitting those across two sources is what made releases visit
    // the old segment on the way over.
    val thumbAnim = remember { Animatable(0f) }
    var thumbReady by remember { mutableStateOf(false) }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp)
    ) {
        // Segments split the padded content width: maxWidth still spans the 4.dp
        // padding on both sides, so dividing it raw made the thumb a touch too
        // wide per segment and drift right, clipping flat against the pill edge
        // on the last option.
        val segmentWidth = (maxWidth - 8.dp) / options.size
        val segmentWidthPx = with(density) { segmentWidth.toPx() }
        val glide = spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        )
        // First layout snaps (no entrance sweep), taps glide, size changes snap.
        // A tap landing while a release is still settling re-takes the glide.
        LaunchedEffect(segmentWidthPx, safeIndex) {
            if (!thumbReady) {
                thumbAnim.snapTo(segmentWidthPx * safeIndex)
                thumbReady = true
            } else if (!dragOffsetPx.isNaN()) {
                // Finger owns the thumb, nothing to do.
            } else if (settleIndex != null && settleIndex == safeIndex) {
                // Caller caught up: re-anchor silently, hand back to the spring.
                thumbAnim.snapTo(settlePx)
                settleIndex = null
            } else {
                settleIndex = null
                thumbAnim.animateTo(segmentWidthPx * safeIndex, glide)
            }
        }
        // Finger owns the thumb mid-drag, the held release px while settling,
        // the single animatable otherwise. Labels preview the nearest segment
        // under the finger and commit on release.
        val thumbOffset = when {
            !dragOffsetPx.isNaN() -> with(density) { dragOffsetPx.toDp() }
            settleIndex != null -> with(density) { settlePx.toDp() }
            else -> with(density) { thumbAnim.value.toDp() }
        }
        val previewIndex = if (dragOffsetPx.isNaN()) null
        else (dragOffsetPx / segmentWidthPx).roundToInt().coerceIn(0, options.size - 1)
        val effectiveIndex = previewIndex ?: safeIndex
        // Drag lives on the content box (segment pixels are known here), taps
        // still land on the per-segment click targets below.
        Box(
            modifier = Modifier.pointerInput(segmentWidthPx) {
                detectHorizontalDragGestures(
                    onDragStart = { dragOffsetPx = thumbAnim.value },
                    onHorizontalDrag = { _, dragAmount ->
                        dragOffsetPx = (dragOffsetPx + dragAmount)
                            .coerceIn(0f, segmentWidthPx * (options.size - 1))
                    },
                    onDragEnd = {
                        val index = (dragOffsetPx / segmentWidthPx).roundToInt()
                            .coerceIn(0, options.size - 1)
                        // Hold the release point on screen, then tell the
                        // caller: no pass through the old segment.
                        settlePx = dragOffsetPx
                        dragOffsetPx = Float.NaN
                        settleIndex = index
                        onSelect(index)
                    },
                    onDragCancel = { dragOffsetPx = Float.NaN }
                )
            }
        ) {
            // True segment-wide thumb with an explicit measured height:
            // matchParentSize() sizes to the whole row (Box forces it) and
            // fillMaxHeight() collapses in unbounded parents.
            Box(
                modifier = Modifier
                    .width(segmentWidth)
                    .height(with(density) { rowHeightPx.toDp() })
                    .offset(x = thumbOffset)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { rowHeightPx = it.height }
            ) {
                options.forEachIndexed { index, label ->
                    val selected = index == effectiveIndex
                    val labelColor by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(180),
                        label = "pillLabelColor"
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .clickable(
                                onClickLabel = "Show $label",
                                role = Role.Tab
                            ) { onSelect(index) }
                            .semantics { this.selected = selected }
                            .padding(vertical = 10.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleSmall,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = labelColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
