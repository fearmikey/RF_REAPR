package com.fearmikey.rf_reapr.domain.service

import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt

/** One access point's signal at one measured point of a site survey ([x]/[y] are 0..1). */
data class SiteSample(
    val pointId: Long,
    val x: Float,
    val y: Float,
    val bssid: String,
    val ssid: String,
    val rssi: Int,
    val frequencyMhz: Int
)

/** A signal reading at a position, after choosing which network to look at. */
data class HeatPoint(val x: Float, val y: Float, val rssi: Int)

enum class SignalQuality(val label: String, val minRssi: Int) {
    EXCELLENT("Excellent", -55),
    GOOD("Good", -67),
    FAIR("Fair", -75),
    WEAK("Weak", -85),
    DEAD("No usable signal", Int.MIN_VALUE);

    companion object {
        fun of(rssi: Float): SignalQuality = entries.first { rssi >= it.minRssi }
    }
}

/** What a site survey can be viewed by: a whole SSID (best AP per point) or one BSSID. */
data class SurveyNetwork(
    val ssid: String,
    /** Null = all access points of [ssid] combined (what a roaming client would get). */
    val bssid: String?,
    val points: Int,
    val bestRssi: Int,
    val worstRssi: Int,
    val bands: Set<String>
)

/**
 * Turns the scattered measurements of a walk-around site survey into a coverage map using
 * inverse-distance weighting. Cells too far from any measurement are left empty rather than
 * extrapolated, so the map never claims coverage nobody measured.
 */
object SignalHeatmap {

    /** RSSI floor used for points where the chosen network wasn't heard at all. */
    const val NOT_HEARD_RSSI = -100

    /** Networks present in the samples, strongest first. One entry per SSID, then its BSSIDs. */
    fun networks(samples: List<SiteSample>): List<SurveyNetwork> {
        fun band(mhz: Int) = when {
            mhz >= 5925 -> "6 GHz"
            mhz >= 4900 -> "5 GHz"
            else -> "2.4 GHz"
        }
        return samples.groupBy { it.ssid }.map { (ssid, group) ->
            val perPoint = group.groupBy { it.pointId }.mapValues { (_, s) -> s.maxOf { it.rssi } }
            SurveyNetwork(ssid, null, perPoint.size, perPoint.values.max(), perPoint.values.min(), group.map { band(it.frequencyMhz) }.toSet())
        }.sortedByDescending { it.bestRssi }
    }

    fun accessPoints(samples: List<SiteSample>, ssid: String): List<SurveyNetwork> =
        samples.filter { it.ssid == ssid }.groupBy { it.bssid }.map { (bssid, group) ->
            SurveyNetwork(
                ssid, bssid, group.map { it.pointId }.distinct().size, group.maxOf { it.rssi }, group.minOf { it.rssi },
                group.map { if (it.frequencyMhz >= 5925) "6 GHz" else if (it.frequencyMhz >= 4900) "5 GHz" else "2.4 GHz" }.toSet()
            )
        }.sortedByDescending { it.bestRssi }

    /**
     * One reading per measured point for the chosen network. Points where it wasn't heard
     * get [NOT_HEARD_RSSI]: "no signal here" is a measurement too, and is what dead zones are.
     */
    fun pointsFor(samples: List<SiteSample>, ssid: String, bssid: String? = null): List<HeatPoint> =
        samples.groupBy { it.pointId }.map { (_, atPoint) ->
            val matching = atPoint.filter { it.ssid == ssid && (bssid == null || it.bssid.equals(bssid, ignoreCase = true)) }
            HeatPoint(atPoint.first().x, atPoint.first().y, matching.maxOfOrNull { it.rssi } ?: NOT_HEARD_RSSI)
        }

    /** Interpolated RSSI at ([x], [y]), or null when no measurement is within [maxDistance]. */
    fun valueAt(points: List<HeatPoint>, x: Float, y: Float, power: Double = 2.0, maxDistance: Float = 0.3f): Float? {
        var weightSum = 0.0
        var valueSum = 0.0
        var nearest = Float.MAX_VALUE
        for (p in points) {
            val d = hypot((p.x - x).toDouble(), (p.y - y).toDouble())
            if (d < 1e-4) return p.rssi.toFloat()
            if (d < nearest) nearest = d.toFloat()
            val w = 1.0 / d.pow(power)
            weightSum += w
            valueSum += w * p.rssi
        }
        if (points.isEmpty() || nearest > maxDistance) return null
        return (valueSum / weightSum).toFloat()
    }

    /** Row-major grid of [cols] x [rows] cell centres; NaN marks cells with no nearby measurement. */
    fun grid(points: List<HeatPoint>, cols: Int, rows: Int, maxDistance: Float = 0.3f): FloatArray {
        val out = FloatArray(cols * rows) { Float.NaN }
        if (points.isEmpty()) return out
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                valueAt(points, (col + 0.5f) / cols, (row + 0.5f) / rows, maxDistance = maxDistance)?.let { out[row * cols + col] = it }
            }
        }
        return out
    }

    /** Share (0..1) of the measured area at or above [threshold] dBm; null if nothing was measured. */
    fun coverage(grid: FloatArray, threshold: Int = SignalQuality.GOOD.minRssi): Float? {
        val measured = grid.filter { !it.isNaN() }
        if (measured.isEmpty()) return null
        return measured.count { it >= threshold }.toFloat() / measured.size
    }

    /** ARGB colour for an RSSI: red (-90 and below) through yellow to green (-50 and above). */
    fun colorArgb(rssi: Float, alpha: Int = 150): Int {
        val t = ((rssi + 90f) / 40f).coerceIn(0f, 1f)
        val r = if (t < 0.5f) 229 else (229 - (t - 0.5f) * 2f * (229 - 56)).roundToInt()
        val g = if (t < 0.5f) (57 + t * 2f * (200 - 57)).roundToInt() else (200 - (t - 0.5f) * 2f * (200 - 142)).roundToInt()
        val b = if (t < 0.5f) 53 else (53 + (t - 0.5f) * 2f * (60 - 53)).roundToInt()
        return (alpha.coerceIn(0, 255) shl 24) or (r shl 16) or (g shl 8) or b
    }
}
