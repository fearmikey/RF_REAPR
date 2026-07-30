package com.fearmikey.rf_reapr.domain.model

data class SdrConfig(
    val frequency: Long = 100_000_000L,
    val sampleRate: Int = 2_048_000,
    val gain: Int = 20,
    val ppm: Int = 0,
    val isConnected: Boolean = false
)
