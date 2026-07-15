package com.fearmikey.rf_reapr.domain.model

import kotlin.math.sqrt

data class MagnetometerData(
    val x: Float,
    val y: Float,
    val z: Float,
    val timestamp: Long = System.currentTimeMillis()
) {
    val totalStrength: Float
        get() = sqrt(x * x + y * y + z * z)
}
