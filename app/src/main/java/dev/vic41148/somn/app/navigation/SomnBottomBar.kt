package dev.vic41148.somn.app.navigation

// Gradle: androidx.graphics:graphics-shapes:1.0.1
//         implementation("androidx.graphics:graphics-shapes:1.0.1")

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import kotlin.math.roundToInt

/**
 * Floating dock bar. At rest it's a flat pill with the selected tab tinted;
 * press or drag and a bubble rises above the tab under your finger, carrying
 * its icon, then settles back on release.
 *
 * One gesture loop selects on first touch and keeps selecting across tab
 * boundaries while dragging, so taps and drags always navigate. Spatial motion
 * (position, scale) shares one motionScheme spring; color/alpha share the
 * effects spec. The pill stays flat (no notch); the bubble shadow carries the
 * elevation and its shape morphs circle -> squircle under press.
 */
@Composable
fun SomnBottomBar(
    screens: List<Screen>,
    selectedIndex: Int,
    onSelect: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val count = screens.size

    val barHeight = 64.dp
    val bubbleSize = 56.dp
    val iconSize = 24.dp
    val overhang = bubbleSize / 2 + 6.dp
    val pillShape = remember { RoundedCornerShape(percent = 50) }

    var barWidthPx by remember { mutableFloatStateOf(0f) }
    fun xFor(index: Int): Float {
        if (barWidthPx <= 0f) return 0f
        val slot = barWidthPx / count
        return slot * (index + 0.5f)
    }
    fun indexAt(xPx: Float): Int {
        if (barWidthPx <= 0f) return selectedIndex
        val raw = ((xPx / barWidthPx) * count).toInt().coerceIn(0, count - 1)
        return if (isRtl) count - 1 - raw else raw
    }

    // The bubble only exists while a finger is down.
    var pressedIndex by remember { mutableIntStateOf(-1) }
    val pressing = pressedIndex >= 0
    val bubbleRider = (if (pressing) pressedIndex else selectedIndex).coerceIn(0, count - 1)

    // One coherent motion source: springs for spatial changes (position, scale),
    // a tween for color. (motionScheme is internal in material3 1.4.0, so the
    // specs are spelled out until it goes public.)
    val glideSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )
    val popSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    )
    val sizeSpec = spring<Dp>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )
    val tintSpec = tween<Color>(durationMillis = 200)

    val scoopX by animateFloatAsState(
        targetValue = xFor(bubbleRider),
        animationSpec = glideSpec,
        label = "dockBubbleX",
    )
    // Allowed to overshoot past 1 slightly for a soft bounce on pop-in — only
    // the alpha derived from it gets coerced, not the scale.
    val pressProgress by animateFloatAsState(
        targetValue = if (pressing) 1f else 0f,
        animationSpec = popSpec,
        label = "dockBubblePress",
    )

    val pillColor = MaterialTheme.colorScheme.surfaceContainer
    val circleColor = MaterialTheme.colorScheme.primary
    val onCircleColor = MaterialTheme.colorScheme.onPrimary
    val idleIconColor = MaterialTheme.colorScheme.onSurfaceVariant
    val selectedIconColor = MaterialTheme.colorScheme.primary

    // Morph endpoints, equal vertex counts: a fully-rounded square reads as a
    // circle and eases toward a soft squircle as press progress rises.
    val circleShape = remember { RoundedPolygon(numVertices = 4, rounding = CornerRounding(1f)) }
    val squircleShape = remember { RoundedPolygon(numVertices = 4, rounding = CornerRounding(0.6f)) }
    val bubbleMorph = remember(circleShape, squircleShape) { Morph(circleShape, squircleShape) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .padding(bottom = 8.dp)
            .height(overhang + barHeight),
    ) {
        // The flat pill. No notch — just a shadow and a background fill.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(barHeight)
                .shadow(elevation = 3.dp, shape = pillShape, clip = false)
                .background(pillColor, pillShape)
                .onSizeChanged { barWidthPx = it.width.toFloat() }
                .pointerInput(count) {
                    // Single gesture loop: selects immediately on first touch,
                    // then keeps selecting as the finger crosses tab boundaries.
                    // Rely on the nav layer's launchSingleTop to no-op repeats
                    // rather than tracking selectedIndex here, since that
                    // value can go stale inside a long-lived pointerInput
                    // coroutine when it changes without `count` changing.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        var idx = indexAt(down.position.x)
                        pressedIndex = idx
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(screens[idx])

                        try {
                            var pointerUp = false
                            while (!pointerUp) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed) {
                                    pointerUp = true
                                } else {
                                    val newIdx = indexAt(change.position.x)
                                    if (newIdx != idx) {
                                        idx = newIdx
                                        pressedIndex = idx
                                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                        onSelect(screens[idx])
                                    }
                                    change.consume()
                                }
                            }
                        } finally {
                            // Guaranteed reset even if the gesture is cancelled
                            // (e.g. stolen by a parent scroll), so the bubble
                            // never gets stuck showing.
                            pressedIndex = -1
                        }
                    }
                },
        ) {
            screens.forEachIndexed { index, screen ->
                val isSelected = index == selectedIndex
                val isRider = index == bubbleRider
                val tint by animateColorAsState(
                    targetValue = if (isSelected) selectedIconColor else idleIconColor,
                    animationSpec = tintSpec,
                    label = "tabTint",
                )
                val iconSizeAnim by animateDpAsState(
                    targetValue = if (isSelected) 26.dp else 24.dp,
                    animationSpec = sizeSpec,
                    label = "tabIconSize",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .semantics(mergeDescendants = true) {
                            contentDescription = screen.label
                            role = Role.Tab
                            selected = isSelected
                            onClick(label = "Select ${screen.label}") {
                                onSelect(screen)
                                true
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    // Fades under the bubble instead of vanishing abruptly.
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.graphicsLayer {
                            alpha = if (isRider) (1f - pressProgress).coerceIn(0f, 1f) else 1f
                        },
                    ) {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(iconSizeAnim),
                        )
                        Text(text = screen.label, fontSize = 11.sp, color = tint)
                    }
                }
            }
        }

        // The bubble: one layer carrying shape, scale, shadow, and icon
        // together, so nothing can drift out of sync with anything else.
        if (pressProgress > 0.01f) {
            val bubblePx = with(density) { bubbleSize.toPx() }
            val xPx = scoopX - bubblePx / 2f
            val yPx = with(density) { overhang.toPx() } - bubblePx / 2f
            Box(
                modifier = Modifier
                    .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
                    .size(bubbleSize)
                    .graphicsLayer {
                        scaleX = pressProgress
                        scaleY = pressProgress
                        alpha = pressProgress.coerceIn(0f, 1f)
                        shadowElevation = 8.dp.toPx() * pressProgress.coerceIn(0f, 1f)
                        shape = BubbleMorphShape(bubbleMorph, (pressProgress * 0.4f).coerceIn(0f, 1f))
                        clip = true
                    }
                    .background(circleColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = screens[bubbleRider].icon,
                    contentDescription = null,
                    tint = onCircleColor,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}

/** Clips a box to a point on the circle -> squircle morph. The library exposes
 *  the morphed outline as cubics, so the path is rebuilt here (unit square
 *  [-1, 1] mapped onto the box) with an explicit matrix reset to stay safe
 *  across recompositions. */
private class BubbleMorphShape(
    private val morph: Morph,
    private val progress: Float,
) : Shape {
    private val matrix = Matrix()

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        matrix.reset()
        matrix.scale(size.width / 2f, size.height / 2f)
        matrix.translate(1f, 1f)
        val androidPath = android.graphics.Path()
        var first = true
        morph.forEachCubic(progress, androidx.graphics.shapes.MutableCubic()) { cubic ->
            if (first) {
                androidPath.moveTo(cubic.anchor0X, cubic.anchor0Y)
                first = false
            }
            androidPath.cubicTo(
                cubic.control0X, cubic.control0Y,
                cubic.control1X, cubic.control1Y,
                cubic.anchor1X, cubic.anchor1Y,
            )
        }
        androidPath.close()
        val path = androidPath.asComposePath()
        path.transform(matrix)
        return Outline.Generic(path)
    }
}
