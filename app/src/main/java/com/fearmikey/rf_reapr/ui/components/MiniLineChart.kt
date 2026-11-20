package com.fearmikey.rf_reapr.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Lightweight line chart for live diagnostics (ping RTT, throughput per interval).
 * Null values are drawn as gaps with a marker on the baseline (e.g. lost packets).
 *
 * @param values series to plot; null = missing sample.
 * @param maxLabel formats the y-axis maximum shown in the top-left corner.
 */
@Composable
fun MiniLineChart(
    values: List<Float?>,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    gapColor: Color = MaterialTheme.colorScheme.error,
    contentDescription: String = "Chart",
    maxLabel: (Float) -> String = { "%.0f".format(it) }
) {
    val textMeasurer = rememberTextMeasurer()
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = remember(labelColor) { TextStyle(color = labelColor, fontSize = 10.sp) }

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { this.contentDescription = contentDescription }
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
            val present = values.filterNotNull()
            if (values.isEmpty()) return@Canvas
            val yMax = (present.maxOrNull() ?: 1f).let { if (it <= 0f) 1f else it * 1.15f }
            val stepX = if (values.size > 1) size.width / (values.size - 1) else size.width
            fun y(v: Float) = size.height - (v / yMax) * size.height

            // Grid: baseline + midline
            val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            drawLine(gridColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1f, pathEffect = dash)
            drawLine(gridColor, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1f)

            val path = Path()
            var penDown = false
            values.forEachIndexed { i, v ->
                val x = i * stepX
                if (v == null) {
                    penDown = false
                    drawCircle(gapColor, 3.dp.toPx(), Offset(x, size.height - 3.dp.toPx()))
                } else if (!penDown) {
                    path.moveTo(x, y(v))
                    penDown = true
                } else {
                    path.lineTo(x, y(v))
                }
            }
            drawPath(path, lineColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (values.size == 1) values[0]?.let { drawCircle(lineColor, 3.dp.toPx(), Offset(size.width / 2, y(it))) }

            val label = textMeasurer.measure(maxLabel(yMax / 1.15f), labelStyle)
            drawText(label, topLeft = Offset(4.dp.toPx(), 0f))
        }
    }
}
