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

        // A full night is ~960 30s epochs, but stages run in multi-minute streaks — merging
        // consecutive same-stage epochs into one rect per run cuts draw calls by an order of
        // magnitude with identical output, since adjacent same-stage bars already share the
        // same y/height and abut at their x boundary.
        var runStart = 0
        while (runStart < stages.size) {
            val stage = stages[runStart]
            var runEnd = runStart
            while (runEnd + 1 < stages.size && stages[runEnd + 1] == stage) runEnd++

            val yOffset = when (stage) {
                SleepStage.AWAKE -> 0f
                SleepStage.REM -> stageHeight
                SleepStage.LIGHT -> stageHeight * 2
                SleepStage.DEEP -> stageHeight * 3
                SleepStage.UNKNOWN -> stageHeight * 2
            }
            val barHeight = size.height - yOffset

            drawRect(
                color = stage.toColor(),
                topLeft = Offset(x = runStart * barWidth, y = yOffset),
                size = Size(width = (runEnd - runStart + 1) * barWidth + 1f, height = barHeight)
            )

            runStart = runEnd + 1
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
