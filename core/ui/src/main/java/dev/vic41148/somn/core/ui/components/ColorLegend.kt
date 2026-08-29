package dev.vic41148.somn.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One legend row: a colour chip plus its label. Screens used to hand-roll this (stage legend,
 * cycle legend, debt legend all drew their own chip) with slightly different shapes, sizes and
 * spacing, so a legend changed shape depending on which screen it was on. One component keeps
 * a legend legible and identical everywhere.
 */
@Composable
fun ColorLegendItem(
    color: Color,
    label: String,
    modifier: Modifier = Modifier,
    swatchSize: Dp = 12.dp,
    shape: Shape = RoundedCornerShape(3.dp)
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(swatchSize)
                .background(color = color, shape = shape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

/** A stacked legend with an optional heading, for legends that list more than a couple of entries. */
@Composable
fun ColorLegend(
    entries: List<Pair<Color, String>>,
    modifier: Modifier = Modifier,
    title: String? = null
) {
    Column(modifier = modifier) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        entries.forEach { (color, label) ->
            ColorLegendItem(color = color, label = label, modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}