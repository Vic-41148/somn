package dev.vic41148.somn.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
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

    // Canvas bars never inherit RTL mirroring — flip run order explicitly so the night
    // still reads start-to-end in the layout direction.
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

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

            val runWidth = (runEnd - runStart + 1) * barWidth + 1f
            val x = if (rtl) size.width - (runEnd + 1) * barWidth else runStart * barWidth
            drawRect(
                color = stage.toColor(),
                topLeft = Offset(x = x, y = yOffset),
                size = Size(width = runWidth, height = barHeight)
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

/**
 * Hypnogram with a "View as table" toggle: the same stage runs as real text rows
 * (stage + minutes per run, totals per stage), which is what TalkBack reads. The chart
 * itself announces a one-line summary.
 */
@Composable
fun HypnogramWithTable(
    stages: List<SleepStage>,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    /** Seconds each stage entry represents (tracking epochs are 30 s). */
    epochSeconds: Int = 30
) {
    if (stages.isEmpty()) return
    var showTable by remember { mutableStateOf(false) }

    val runs = remember(stages) {
        buildList {
            var start = 0
            while (start < stages.size) {
                var end = start
                while (end + 1 < stages.size && stages[end + 1] == stages[start]) end++
                add(stages[start] to (end - start + 1))
                start = end + 1
            }
        }
    }
    val summary = remember(runs, epochSeconds) {
        val totals = runs.groupBy({ it.first }, { it.second })
            .mapValues { (_, counts) -> counts.sum() * epochSeconds / 60 }
        "Sleep stages: " + totals.entries.joinToString(", ") { (stage, minutes) ->
            "${stage.displayName()} $minutes minutes"
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        TableToggleRow(showTable = showTable, onToggle = { showTable = !showTable })
        if (showTable) {
            StageRunsTable(runs = runs, epochSeconds = epochSeconds)
        } else {
            Hypnogram(
                stages = stages,
                modifier = Modifier.semantics { contentDescription = summary },
                height = height
            )
        }
    }
}

@Composable
private fun StageRunsTable(
    runs: List<Pair<SleepStage, Int>>,
    epochSeconds: Int
) {
    Column {
        runs.forEachIndexed { index, (stage, count) ->
            val minutes = count * epochSeconds / 60
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${index + 1}. ${stage.displayName()}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
