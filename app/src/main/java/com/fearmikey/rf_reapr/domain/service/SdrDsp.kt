package com.fearmikey.rf_reapr.domain.service

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10

data class RtlTcpDongleInfo(val tunerType: Int, val gainCount: Int) {
    val tunerName: String get() = when (tunerType) {
        1 -> "Elonics E4000"
        2 -> "Fitipower FC0012"
        3 -> "Fitipower FC0013"
        4 -> "FCI FC2580"
        5 -> "Rafael Micro R820T/R820T2"
        6 -> "Rafael Micro R828D"
        else -> "Unknown tuner"
    }
}

data class SpectrumPeak(val bin: Int, val frequencyHz: Long, val powerDb: Float, val snrDb: Float)

/** Pure DSP helpers for the rtl_tcp spectrum view (unit-testable, no Android types). */
object SdrDsp {

    /** rtl_tcp sends a 12-byte header: "RTL0", tuner type (u32 BE), gain count (u32 BE). */
    fun parseDongleInfo(header: ByteArray): RtlTcpDongleInfo? {
        if (header.size < 12 || header[0] != 'R'.code.toByte() || header[1] != 'T'.code.toByte() || header[2] != 'L'.code.toByte()) return null
        fun u32(o: Int) = ((header[o].toInt() and 0xFF) shl 24) or ((header[o + 1].toInt() and 0xFF) shl 16) or
            ((header[o + 2].toInt() and 0xFF) shl 8) or (header[o + 3].toInt() and 0xFF)
        return RtlTcpDongleInfo(u32(4), u32(8))
    }

    fun hannWindow(size: Int): FloatArray = FloatArray(size) { i -> (0.5 - 0.5 * cos(2.0 * PI * i / (size - 1))).toFloat() }

    /**
     * Converts accumulated |X|² power (summed over [averages] FFTs) to dBFS, removing the
     * FFT/window gain so a full-scale tone reads ~0 dB, and swaps halves so DC is centred.
     */
    fun powerToDbfsShifted(power: FloatArray, averages: Int, windowSum: Float): FloatArray {
        val n = power.size
        val fullScale = (127.5f * windowSum).let { it * it }
        val out = FloatArray(n)
        val half = n / 2
        for (i in 0 until n) {
            val p = power[i] / averages.coerceAtLeast(1) / fullScale
            val db = 10f * log10(p + 1e-12f)
            out[(i + half) % n] = db
        }
        return out
    }

    /** Median of the spectrum: a robust noise-floor estimate. */
    fun noiseFloor(db: FloatArray): Float {
        if (db.isEmpty()) return -100f
        val sorted = db.copyOf().also { it.sort() }
        return sorted[sorted.size / 2]
    }

    /** Strongest bin, ignoring the DC spike at the centre that RTL dongles always show. */
    fun findPeak(db: FloatArray, centerHz: Long, sampleRate: Int): SpectrumPeak? {
        if (db.isEmpty()) return null
        val n = db.size
        val dcGuard = (n / 256).coerceAtLeast(2)
        var best = -1
        for (i in db.indices) {
            if (kotlin.math.abs(i - n / 2) <= dcGuard) continue
            if (best == -1 || db[i] > db[best]) best = i
        }
        if (best == -1) return null
        return SpectrumPeak(best, binToFrequency(best, n, centerHz, sampleRate), db[best], db[best] - noiseFloor(db))
    }

    fun binToFrequency(bin: Int, size: Int, centerHz: Long, sampleRate: Int): Long =
        centerHz + ((bin - size / 2).toDouble() / size * sampleRate).toLong()

    /** rtl_tcp gain is in tenths of a dB. */
    fun gainCommandValue(gainDb: Int): Int = gainDb * 10
}
