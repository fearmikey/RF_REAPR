package com.fearmikey.rf_reapr.domain.model

data class FftData(
    val magnitudes: FloatArray,
    val centerFrequency: Long,
    val bandwidth: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FftData
        if (!magnitudes.contentEquals(other.magnitudes)) return false
        if (centerFrequency != other.centerFrequency) return false
        if (bandwidth != other.bandwidth) return false
        return true
    }

    override fun hashCode(): Int {
        var result = magnitudes.contentHashCode()
        result = 31 * result + centerFrequency.hashCode()
        result = 31 * result + bandwidth
        return result
    }
}
