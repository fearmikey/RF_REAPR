package com.fearmikey.rf_reapr.domain.model

data class SdrConfig(
    val frequency: Long = 100_000_000L, // 100 MHz default
    val sampleRate: Int = 2_048_000,    // 2.048 MSPS default
    val gain: Int = 0,                 // 0 = Auto
    val ppm: Int = 0,
    val isConnected: Boolean = false
)
