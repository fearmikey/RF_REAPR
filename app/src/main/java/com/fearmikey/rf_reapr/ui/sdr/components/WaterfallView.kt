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
    // Use remember for long-lived objects
    val bitmap = remember(fftSize) { Bitmap.createBitmap(fftSize, height, Bitmap.Config.ARGB_8888) }
    val canvasBitmap = remember(bitmap) { android.graphics.Canvas(bitmap) }
    val linePixels = remember(fftSize) { IntArray(fftSize) }
    
    // Color map logic - pre-calculate or keep it simple
    fun getWaterfallColor(magnitude: Float): Int {
        val normalized = ((magnitude + 80) / 100).coerceIn(0f, 1f)
        return when {
            normalized < 0.25f -> Color.rgb(0, 0, (normalized * 4 * 255).toInt())
            normalized < 0.5f -> Color.rgb(0, ((normalized - 0.25f) * 4 * 255).toInt(), 255)
            normalized < 0.75f -> Color.rgb(((normalized - 0.5f) * 4 * 255).toInt(), 255, (255 - (normalized - 0.5f) * 4 * 255).toInt())
            else -> Color.rgb(255, (255 - (normalized - 0.75f) * 4 * 255).toInt(), 0)
        }
    }

    // Effect to update the bitmap when new data arrives
    SideEffect {
        // Shift existing pixels down by drawing the bitmap onto itself
        canvasBitmap.drawBitmap(bitmap, 0f, 1f, null)
        
        // Prepare new line pixels
        for (i in 0 until fftSize) {
            linePixels[i] = getWaterfallColor(fftData.magnitudes[i])
        }
        
        // Update top line
        bitmap.setPixels(linePixels, 0, fftSize, 0, 0, fftSize, 1)
    }

    Canvas(modifier = modifier
        .fillMaxWidth()
        .height(height.dp)) {
        drawImage(bitmap.asImageBitmap())
    }
}
