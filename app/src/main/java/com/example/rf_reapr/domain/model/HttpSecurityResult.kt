package com.example.rf_reapr.domain.model

data class HttpSecurityResult(
    val url: String,
    val statusCode: Int,
    val headers: Map<String, String>,
    val misconfigurations: List<SecurityMisconfiguration>,
    val timestamp: Long = System.currentTimeMillis()
)

data class SecurityMisconfiguration(
    val header: String,
    val issue: String,
    val severity: RiskLevel,
    val recommendation: String
)
