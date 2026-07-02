package com.example.rf_reapr.domain.model

import java.util.Date

data class TlsAuditResult(
    val url: String,
    val protocol: String?,
    val cipherSuite: String?,
    val certificateSubject: String?,
    val certificateIssuer: String?,
    val expiryDate: Date?,
    val vulnerabilities: List<TlsVulnerability>,
    val timestamp: Long = System.currentTimeMillis()
)

data class TlsVulnerability(
    val type: String,
    val description: String,
    val severity: RiskLevel
)
