package dev.vic41148.somn.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.vic41148.somn.core.domain.model.SleepStage
import dev.vic41148.somn.core.ui.theme.StageAwake
import dev.vic41148.somn.core.ui.theme.StageDeep
import dev.vic41148.somn.core.ui.theme.StageLight
import dev.vic41148.somn.core.ui.theme.StageRem

/**
 * Visual timeline of sleep stages across the night (hypnogram).
 * Renders as stacked colored bars showing the progression of stages.
 */
@Composable
fun Hypnogram(
    stages: List<SleepStage>,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp
) {
    if (stages.isEmpty()) return

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val barWidth = size.width / stages.size
        val stageHeight = size.height / 4f  // 4 stage levels

        stages.forEachIndexed { index, stage ->
            val color = stage.toColor()
            val yOffset = when (stage) {
                SleepStage.AWAKE -> 0f
                SleepStage.REM -> stageHeight
                SleepStage.LIGHT -> stageHeight * 2
                SleepStage.DEEP -> stageHeight * 3
                SleepStage.UNKNOWN -> stageHeight * 2
            }
            val barHeight = size.height - yOffset

            drawRect(
                color = color,
                topLeft = Offset(x = index * barWidth, y = yOffset),
                size = Size(width = barWidth + 1f, height = barHeight)
            )
        }
    }
}

fun SleepStage.toColor(): Color = when (this) {
    SleepStage.AWAKE -> StageAwake
    SleepStage.LIGHT -> StageLight
    SleepStage.DEEP -> StageDeep
    SleepStage.REM -> StageRem
    SleepStage.UNKNOWN -> StageLight.copy(alpha = 0.3f)
}
