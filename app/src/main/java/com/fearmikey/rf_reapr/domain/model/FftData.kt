package com.fearmikey.rf_reapr.domain.model

import com.fearmikey.rf_reapr.domain.service.SpectrumPeak

/** One averaged spectrum frame, in dBFS with DC centred. */
data class FftData(
    val magnitudes: FloatArray,
    val centerFrequency: Long,
    val bandwidth: Int,
    /** Increments every frame so identical-looking frames still trigger a redraw. */
    val sequence: Long = 0,
    val noiseFloor: Float = -100f,
    val peak: SpectrumPeak? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FftData
        return sequence == other.sequence &&
            centerFrequency == other.centerFrequency &&
            bandwidth == other.bandwidth &&
            magnitudes.contentEquals(other.magnitudes)
    }

    override fun hashCode(): Int {
        var result = magnitudes.contentHashCode()
        result = 31 * result + centerFrequency.hashCode()
        result = 31 * result + bandwidth
        result = 31 * result + sequence.hashCode()
        return result
    }
}
