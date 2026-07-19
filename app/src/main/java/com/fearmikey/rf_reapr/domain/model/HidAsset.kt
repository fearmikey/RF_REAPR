package com.fearmikey.rf_reapr.domain.model

data class HidAsset(
    val id: String,
    val name: String,
    val path: String,
    val timestamp: Long = System.currentTimeMillis(),
    val size: Long = 0
)
