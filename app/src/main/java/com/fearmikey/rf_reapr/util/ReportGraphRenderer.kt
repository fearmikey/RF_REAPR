package com.fearmikey.rf_reapr.util

import android.graphics.*
import com.fearmikey.rf_reapr.domain.model.WifiAccessPoint

object ReportGraphRenderer {

    fun getColorForBssid(bssid: String): Int {
        val hash = bssid.hashCode()
        val hues = listOf(0f, 30f, 60f, 150f, 180f, 210f, 240f, 280f, 310f, 340f)
        val hue = hues[Math.abs(hash) % hues.size]
        return Color.HSVToColor(floatArrayOf(hue, 0.7f, 0.8f))
    }

    fun renderWifiSpectrum(aps: List<WifiAccessPoint>, minFreq: Float, maxFreq: Float, title: String): Bitmap {
        val width = 800
        val height = 400
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val margin = 40f
        
        // Background
        val bgPaint = Paint().apply {
            color = Color.rgb(240, 240, 240)
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val chartWidth = width - (margin * 2)
        val chartHeight = height - (margin * 2)
        
        val minY = -100f
        val maxY = -20f

        fun freqToX(freq: Float): Float {
            val normalizedX = (freq - minFreq) / (maxFreq - minFreq) * chartWidth
            return normalizedX + margin
        }

        fun rssiToY(rssi: Int): Float {
            val clampedRssi = rssi.coerceIn(minY.toInt(), maxY.toInt())
            return chartHeight - (clampedRssi - minY) / (maxY - minY) * chartHeight + margin
        }

        // Title
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 24f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(title, width / 2f, margin - 10f, titlePaint)

        // Grid lines
        val gridPaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        val labelPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 18f
        }

        for (dbm in -100..-20 step 20) {
            val y = rssiToY(dbm)
            canvas.drawLine(margin, y, width - margin, y, gridPaint)
            canvas.drawText("$dbm", 5f, y + 6f, labelPaint)
        }

        // Access Points
        aps.forEach { ap ->
            val color = getColorForBssid(ap.bssid)
            val centerX = freqToX(ap.frequency.toFloat())
            val bandwidthWidth = (ap.bandwidth.toFloat() / (maxFreq - minFreq)) * chartWidth
            val halfWidth = bandwidthWidth / 2f
            val topY = rssiToY(ap.signalLevel)

            val path = Path().apply {
                moveTo(centerX - halfWidth, chartHeight + margin)
                cubicTo(
                    centerX - halfWidth / 2f, chartHeight + margin,
                    centerX - halfWidth / 2f, topY,
                    centerX, topY
                )
                cubicTo(
                    centerX + halfWidth / 2f, topY,
                    centerX + halfWidth / 2f, chartHeight + margin,
                    centerX + halfWidth, chartHeight + margin
                )
            }

            val fillPaint = Paint().apply {
                this.color = color
                this.alpha = 60
                style = Paint.Style.FILL
            }
            val strokePaint = Paint().apply {
                this.color = color
                strokeWidth = 3f
                style = Paint.Style.STROKE
            }

            canvas.drawPath(path, fillPaint)
            canvas.drawPath(path, strokePaint)

            // Labels
            val apLabelPaint = Paint().apply {
                this.color = Color.BLACK
                textSize = 16f
                isFakeBoldText = true
                textAlign = Paint.Align.CENTER
            }
            val ssidLabel = if (ap.ssid.isEmpty()) "[Hidden]" else ap.ssid
            canvas.drawText(ssidLabel.take(15), centerX, topY - 10f, apLabelPaint)
        }

        return bitmap
    }
}
