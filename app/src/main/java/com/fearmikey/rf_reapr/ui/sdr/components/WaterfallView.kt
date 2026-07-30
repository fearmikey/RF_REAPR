package com.fearmikey.rf_reapr.ui.sdr.components

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.fearmikey.rf_reapr.domain.model.FftData
import kotlin.math.max
import kotlin.math.min

@Composable
fun WaterfallView(
    fftData: FftData,
    modifier: Modifier = Modifier
) {
    val fftSize = fftData.magnitudes.size
    if (fftSize == 0) return

    val height = 300
    val bitmap = remember { Bitmap.createBitmap(fftSize, height, Bitmap.Config.ARGB_8888) }
    val canvasBitmap = remember { android.graphics.Canvas(bitmap) }
    
    // Color map logic
    fun getWaterfallColor(magnitude: Float): Int {
        val normalized = ((magnitude + 100) / 100).coerceIn(0f, 1f)
        // Simple jet-like color map: Blue -> Cyan -> Green -> Yellow -> Red
        return when {
            normalized < 0.25f -> Color.rgb(0, 0, (normalized * 4 * 255).toInt())
            normalized < 0.5f -> Color.rgb(0, ((normalized - 0.25f) * 4 * 255).toInt(), 255)
            normalized < 0.75f -> Color.rgb(((normalized - 0.5f) * 4 * 255).toInt(), 255, (255 - (normalized - 0.5f) * 4 * 255).toInt())
            else -> Color.rgb(255, (255 - (normalized - 0.75f) * 4 * 255).toInt(), 0)
        }
    }

    LaunchedEffect(fftData) {
        // Shift existing lines down
        val tempBitmap = Bitmap.createBitmap(bitmap, 0, 0, fftSize, height - 1)
        canvasBitmap.drawBitmap(tempBitmap, 0f, 1f, null)
        
        // Draw new line at top
        val pixels = IntArray(fftSize)
        for (i in 0 until fftSize) {
            pixels[i] = getWaterfallColor(fftData.magnitudes[i])
        }
        bitmap.setPixels(pixels, 0, fftSize, 0, 0, fftSize, 1)
    }

    Canvas(modifier = modifier
        .fillMaxWidth()
        .height(height.dp)) {
        drawImage(bitmap.asImageBitmap())
    }
}
