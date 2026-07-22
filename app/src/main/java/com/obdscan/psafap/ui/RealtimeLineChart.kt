package com.obdscan.psafap.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Graphique simple en ligne (sans bibliothèque externe) pour visualiser
 * l'évolution d'une série de valeurs dans le temps.
 */
@Composable
fun RealtimeLineChart(
    title: String,
    values: List<Float>,
    unit: String,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        Text("$title${if (values.isNotEmpty()) " (${"%.1f".format(values.last())} $unit)" else ""}",
            style = MaterialTheme.typography.titleSmall)

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(vertical = 8.dp)
        ) {
            if (values.size < 2) return@Canvas

            val maxVal = values.max().let { if (it == 0f) 1f else it }
            val minVal = values.min()
            val range = (maxVal - minVal).let { if (it == 0f) 1f else it }

            val stepX = size.width / (values.size - 1)

            val path = androidx.compose.ui.graphics.Path()
            values.forEachIndexed { index, value ->
                val x = index * stepX
                val normalized = (value - minVal) / range
                val y = size.height - (normalized * size.height)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )

            // Ligne de base
            drawLine(
                color = Color.LightGray,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 2f
            )
        }
    }
}
