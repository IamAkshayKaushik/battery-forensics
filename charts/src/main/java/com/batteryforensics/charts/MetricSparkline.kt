package com.batteryforensics.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Lightweight sparkline for investigator overview (no third-party chart binding required). */
@Composable
fun MetricSparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (values.size < 2) return
    val min = values.minOrNull() ?: return
    val max = values.maxOrNull() ?: return
    val range = (max - min).takeIf { it > 1e-3f } ?: 1f
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp),
    ) {
        val path = Path()
        values.forEachIndexed { index, v ->
            val x = size.width * index / (values.size - 1).coerceAtLeast(1)
            val y = size.height - ((v - min) / range) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3f, cap = StrokeCap.Round),
        )
        val lastX = size.width
        val lastY = size.height - ((values.last() - min) / range) * size.height
        drawCircle(color = lineColor, radius = 5f, center = Offset(lastX, lastY))
    }
}
