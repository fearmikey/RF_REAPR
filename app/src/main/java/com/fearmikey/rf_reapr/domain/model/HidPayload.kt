package com.fearmikey.rf_reapr.domain.model

data class HidPayload(
    val id: Long = 0,
    val name: String,
    val script: String,
    val createdAt: Long = System.currentTimeMillis()
)
