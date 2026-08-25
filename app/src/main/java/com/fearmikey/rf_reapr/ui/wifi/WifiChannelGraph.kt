package com.fearmikey.rf_reapr.ui.wifi

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fearmikey.rf_reapr.domain.model.WifiAccessPoint
import com.fearmikey.rf_reapr.ui.wifi.WifiFingerprintViewModel.FrequencyRange

fun getColorForBssid(bssid: String): Color {
    val hash = bssid.hashCode()
    // Avoid too dark or too light colors
    val hues = listOf(0f, 30f, 60f, 150f, 180f, 210f, 240f, 280f, 310f, 340f)
    val hue = hues[Math.abs(hash) % hues.size]
    return Color.hsv(hue, 0.7f, 0.8f)
}

@Composable
fun WifiChannelGraph(
    accessPoints: List<WifiAccessPoint>,
    hiddenBssids: Set<String>,
    range: FrequencyRange,
    modifier: Modifier = Modifier
) {
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    
    // Zoom state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    // We need the width to calculate bounds, but it's only available in the draw phase or via BoxWithConstraints
    // For a simpler approach, we'll track the width when it's available
    var canvasWidth by remember { mutableFloatStateOf(0f) }

    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 10f)
        scale = newScale
        
        // If canvasWidth is known, we can constrain the offset
        if (canvasWidth > 0f) {
            val maxOffset = 0f
            val minOffset = -(canvasWidth * scale - canvasWidth)
            
            val newOffsetX = (offset.x + offsetChange.x).coerceIn(minOffset, maxOffset)
            offset = Offset(newOffsetX, 0f) // Keep Y offset at 0
        } else {
            offset += offsetChange
        }
    }

    val (minFreq, maxFreq) = when (range) {
        FrequencyRange.FREQ_2_4GHZ -> 2400f to 2484f
        FrequencyRange.FREQ_5GHZ -> 5145f to 5840f
        FrequencyRange.FREQ_6GHZ -> 5900f to 7150f
    }

    val visibleAps = remember(accessPoints, hiddenBssids) {
        accessPoints.filter { !hiddenBssids.contains(it.bssid) }
    }

    val apsByFreq = remember(visibleAps) {
        visibleAps.groupBy { it.frequency }
    }

    val sortedApsForDrawing = remember(visibleAps) {
        visibleAps.sortedBy { it.signalLevel }
    }

    Column(modifier = modifier
        .fillMaxWidth()
        .background(surfaceColor, shape = MaterialTheme.shapes.medium)
        .padding(8.dp)
    ) {
        Text(
            text = when(range) {
                FrequencyRange.FREQ_2_4GHZ -> "2.4 GHz Spectrum"
                FrequencyRange.FREQ_5GHZ -> "5 GHz Spectrum"
                FrequencyRange.FREQ_6GHZ -> "6 GHz Spectrum"
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Canvas(modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .graphicsLayer(clip = true) // Ensure zoom doesn't bleed out
            .transformable(state = state)
        ) {
            canvasWidth = size.width
            val width = size.width
            val height = size.height
            
            // Y-axis: -100 to -20 dBm
            val minY = -100f
            val maxY = -20f
            
            fun freqToX(freq: Float): Float {
                val normalizedX = (freq - minFreq) / (maxFreq - minFreq) * width
                return normalizedX * scale + offset.x
            }
            
            fun rssiToY(rssi: Int): Float {
                val clampedRssi = rssi.coerceIn(minY.toInt(), maxY.toInt())
                return height - (clampedRssi - minY) / (maxY - minY) * height
            }

            // Draw Y-axis labels and grid lines
            val paint = android.graphics.Paint().apply {
                this.color = onSurfaceColor.toArgb()
                this.textSize = 24f
                this.alpha = 150
            }

            for (dbm in -100..-20 step 20) {
                val y = rssiToY(dbm)
                drawLine(
                    color = onSurfaceColor.copy(alpha = 0.1f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f
                )
                drawContext.canvas.nativeCanvas.drawText(
                    "$dbm",
                    5f,
                    y - 5f,
                    paint
                )
            }

            // Draw X-axis (frequencies/channels)
            val channelPaint = android.graphics.Paint().apply {
                this.color = onSurfaceColor.toArgb()
                this.textSize = 24f
                this.textAlign = android.graphics.Paint.Align.CENTER
                this.alpha = 180
            }

            when (range) {
                FrequencyRange.FREQ_2_4GHZ -> {
                    for (ch in 1..13) {
                        val f = 2412f + (ch - 1) * 5f
                        val x = freqToX(f)
                        if (x in 0f..width) {
                            drawContext.canvas.nativeCanvas.drawText("$ch", x, height - 5f, channelPaint)
                        }
                    }
                }
                FrequencyRange.FREQ_5GHZ -> {
                    listOf(36, 48, 64, 100, 128, 144, 149, 161).forEach { ch ->
                        val f = 5000f + ch * 5f
                        val x = freqToX(f)
                        if (x in 0f..width) {
                            drawContext.canvas.nativeCanvas.drawText("$ch", x, height - 5f, channelPaint)
                        }
                    }
                }
                FrequencyRange.FREQ_6GHZ -> {
                    listOf(1, 33, 65, 97, 129, 161, 193, 233).forEach { ch ->
                        val f = 5945f + (ch - 1) * 5f
                        val x = freqToX(f)
                        if (x in 0f..width) {
                            drawContext.canvas.nativeCanvas.drawText("$ch", x, height - 5f, channelPaint)
                        }
                    }
                }
            }

            // Draw access points as humps (weakest first so strongest is drawn on top)
            sortedApsForDrawing.forEach { ap ->
                val color = getColorForBssid(ap.bssid)
                val centerX = freqToX(ap.frequency.toFloat())
                val bandwidthWidth = (ap.bandwidth.toFloat() / (maxFreq - minFreq)) * width * scale
                val halfWidth = bandwidthWidth / 2f
                val topY = rssiToY(ap.signalLevel)
                
                // Only draw if within visible range
                if (centerX + halfWidth < 0 || centerX - halfWidth > width) return@forEach

                val path = Path().apply {
                    moveTo(centerX - halfWidth, height)
                    cubicTo(
                        centerX - halfWidth / 2f, height,
                        centerX - halfWidth / 2f, topY,
                        centerX, topY
                    )
                    cubicTo(
                        centerX + halfWidth / 2f, topY,
                        centerX + halfWidth / 2f, height,
                        centerX + halfWidth, height
                    )
                }
                
                drawPath(path = path, color = color.copy(alpha = 0.3f))
                drawPath(path = path, color = color, style = Stroke(width = 2.dp.toPx()))
            }
            
            // Draw labels, grouped by frequency to prevent overlap
            apsByFreq.forEach { (_, aps) ->
                // Sort by signal strength descending (strongest first, so it gets placed at the top visually)
                // If signal strengths are the same, they just get stacked because of the logic below.
                val sortedAps = aps.sortedByDescending { it.signalLevel }
                
                var lastLabelBottomY = -Float.MAX_VALUE
                val labelSpacing = 40f // Vertical space required for a label block
                
                // Note: we draw labels from strongest to weakest to ensure the strongest is placed at its ideal position (topY).
                // However, doing so means the weakest label is drawn LAST (on top in Z-order).
                // To ensure the strongest text is drawn ON TOP of weaker text, we calculate positions first, then draw in reverse.
                
                val labelPositions = sortedAps.map { ap ->
                    val topY = rssiToY(ap.signalLevel)
                    val baseY = maxOf(topY, lastLabelBottomY + labelSpacing)
                    lastLabelBottomY = baseY
                    ap to baseY
                }
                
                // Draw in reverse (weakest first) so strongest text is on top Z-order
                labelPositions.reversed().forEach { (ap, baseY) ->
                    val centerX = freqToX(ap.frequency.toFloat())
                    
                    if (centerX < 0 || centerX > width) return@forEach
                    
                    // Draw SSID
                    val labelPaint = android.graphics.Paint().apply {
                        this.color = onSurfaceColor.toArgb()
                        this.textSize = 28f
                        this.isFakeBoldText = true
                        this.textAlign = android.graphics.Paint.Align.CENTER
                    }
                    
                    val ssidLabel = if (ap.ssid.isEmpty()) "[Hidden]" else ap.ssid
                    drawContext.canvas.nativeCanvas.drawText(
                        ssidLabel,
                        centerX,
                        baseY - 30f,
                        labelPaint
                    )
                    
                    // Draw Bandwidth Info
                    val infoPaint = android.graphics.Paint().apply {
                        this.color = onSurfaceColor.toArgb()
                        this.textSize = 22f
                        this.textAlign = android.graphics.Paint.Align.CENTER
                        this.alpha = 200
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        "${ap.bandwidth}MHz",
                        centerX,
                        baseY - 5f,
                        infoPaint
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Y-axis: Signal Strength (dBm) | X-axis: Channel Number",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
