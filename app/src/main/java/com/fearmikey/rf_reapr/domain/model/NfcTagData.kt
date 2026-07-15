package com.fearmikey.rf_reapr.domain.model

data class NfcTagData(
    val id: String,
    val techList: List<String>,
    val ndefMessages: List<String>,
    val isVulnerable: Boolean = false,
    val vulnerabilityDescription: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
