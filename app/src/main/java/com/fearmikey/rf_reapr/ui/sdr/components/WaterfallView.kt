package com.fearmikey.rf_reapr.ui.sdr.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.fearmikey.rf_reapr.domain.model.FftData
import com.fearmikey.rf_reapr.domain.service.SdrDsp
import java.util.Locale

private const val WATERFALL_ROWS = 256
private const val DISPLAY_RANGE_DB = 50f

/**
 * Spectrum trace + scrolling waterfall. Colours auto-range from the noise floor, so the
 * view adapts to gain changes. Tap anywhere to tune to that frequency.
 */
@Composable
fun WaterfallView(
    fftData: FftData,
    modifier: Modifier = Modifier,
    onTapFrequency: ((Long) -> Unit)? = null
) {
    val bins = fftData.magnitudes.size
    if (bins == 0) return

    // Downsample to at most 1024 columns (max-hold so narrow signals don't vanish).
    val columns = minOf(bins, 1024)
    val bitmap = remember(columns) { Bitmap.createBitmap(columns, WATERFALL_ROWS, Bitmap.Config.ARGB_8888) }
    val pixels = remember(columns) { IntArray(columns * WATERFALL_ROWS) }
    var frame by remember { mutableLongStateOf(0L) }
    // Smoothed colour scale so it doesn't flicker frame to frame.
    var floor by remember { mutableFloatStateOf(fftData.noiseFloor) }

    val reduced = remember(fftData.sequence, columns) {
        val out = FloatArray(columns)
        val ratio = bins.toFloat() / columns
        for (c in 0 until columns) {
            val start = (c * ratio).toInt()
            val end = ((c + 1) * ratio).toInt().coerceAtMost(bins).coerceAtLeast(start + 1)
            var m = -200f
            for (i in start until end) if (fftData.magnitudes[i] > m) m = fftData.magnitudes[i]
            out[c] = m
        }
        out
    }

    LaunchedEffect(fftData.sequence) {
        floor += (fftData.noiseFloor - floor) * 0.1f
        // Scroll down one row, then paint the newest row on top.
        System.arraycopy(pixels, 0, pixels, columns, columns * (WATERFALL_ROWS - 1))
        for (c in 0 until columns) pixels[c] = heatColor((reduced[c] - (floor - 5f)) / DISPLAY_RANGE_DB)
        bitmap.setPixels(pixels, 0, columns, 0, 0, columns, WATERFALL_ROWS)
        frame++
    }

    val tapModifier = if (onTapFrequency != null) {
        Modifier.pointerInput(fftData.centerFrequency, fftData.bandwidth) {
            detectTapGestures { pos ->
                val bin = (pos.x / size.width * bins).toInt().coerceIn(0, bins - 1)
                onTapFrequency(SdrDsp.binToFrequency(bin, bins, fftData.centerFrequency, fftData.bandwidth))
            }
        }
    } else Modifier

    Column(modifier) {
        val traceColor = Color(0xFF4DD0E1)
        val gridColor = Color.White.copy(alpha = 0.12f)
        val peakColor = Color(0xFFFFEB3B)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .then(tapModifier)
        ) {
            val top = floor + DISPLAY_RANGE_DB + 10f
            val bottom = floor - 10f
            fun y(db: Float) = size.height * (1f - ((db - bottom) / (top - bottom)).coerceIn(0f, 1f))
            drawRect(Color(0xFF101418))
            val paint = android.graphics.Paint().apply {
                color = Color.White.copy(alpha = 0.6f).toArgb()
                textSize = 22f
            }
            for (k in 0..4) {
                val db = bottom + (top - bottom) * k / 4f
                val yy = y(db)
                drawLine(gridColor, Offset(0f, yy), Offset(size.width, yy))
                drawContext.canvas.nativeCanvas.drawText(String.format(Locale.US, "%.0f dB", db), 4f, yy - 4f, paint)
            }
            val path = Path()
            reduced.forEachIndexed { i, db ->
                val x = i / (columns - 1f) * size.width
                if (i == 0) path.moveTo(x, y(db)) else path.lineTo(x, y(db))
            }
            drawPath(path, traceColor, style = Stroke(width = 1.5.dp.toPx()))
            fftData.peak?.let { p ->
                val x = p.bin / (bins - 1f) * size.width
                drawLine(peakColor.copy(alpha = 0.7f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            }
            // Centre marker (tuned frequency).
            drawLine(Color.Red.copy(alpha = 0.5f), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height))
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .then(tapModifier)
        ) {
            frame // read so the canvas redraws every new row
            drawImage(
                image = bitmap.asImageBitmap(),
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(columns, WATERFALL_ROWS),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.toInt(), size.height.toInt())
            )
            val paint = android.graphics.Paint().apply {
                color = Color.White.toArgb()
                textSize = 24f
                setShadowLayer(3f, 0f, 0f, android.graphics.Color.BLACK)
            }
            // Frequency axis: left edge, centre, right edge.
            val half = fftData.bandwidth / 2
            listOf(
                0f to fftData.centerFrequency - half,
                size.width / 2 to fftData.centerFrequency,
                size.width to fftData.centerFrequency + half
            ).forEach { (x, hz) ->
                val label = String.format(Locale.US, "%.3f", hz / 1e6)
                val w = paint.measureText(label)
                drawContext.canvas.nativeCanvas.drawText(label, (x - w / 2).coerceIn(2f, size.width - w - 2f), 26f, paint)
            }
        }
    }
}

/** Black → blue → cyan → yellow → red → white. */
private fun heatColor(t: Float): Int {
    val v = t.coerceIn(0f, 1f)
    val stops = arrayOf(
        floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0.6f), floatArrayOf(0f, 0.8f, 1f),
        floatArrayOf(1f, 1f, 0f), floatArrayOf(1f, 0.1f, 0f), floatArrayOf(1f, 1f, 1f)
    )
    val pos = v * (stops.size - 1)
    val i = pos.toInt().coerceAtMost(stops.size - 2)
    val f = pos - i
    val a = stops[i]
    val b = stops[i + 1]
    return android.graphics.Color.rgb(
        ((a[0] + (b[0] - a[0]) * f) * 255).toInt(),
        ((a[1] + (b[1] - a[1]) * f) * 255).toInt(),
        ((a[2] + (b[2] - a[2]) * f) * 255).toInt()
    )
}
