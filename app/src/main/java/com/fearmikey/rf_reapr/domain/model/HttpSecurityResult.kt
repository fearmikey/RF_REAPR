package com.fearmikey.rf_reapr.domain.model

data class HttpSecurityResult(
    val url: String,
    val statusCode: Int,
    val headers: Map<String, String>,
    val misconfigurations: List<SecurityMisconfiguration>,
    val timestamp: Long = System.currentTimeMillis(),
    /** Security-header grade A–F and 0..100 score (see SecurityHeaderAnalyzer). */
    val grade: String? = null,
    val score: Int? = null,
    /** Protections that are present and correctly configured. */
    val passed: List<String> = emptyList()
)

data class SecurityMisconfiguration(
    val header: String,
    val issue: String,
    val severity: RiskLevel,
    val recommendation: String
)
