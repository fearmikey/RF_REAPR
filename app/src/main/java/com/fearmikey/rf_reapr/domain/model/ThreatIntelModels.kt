package com.fearmikey.rf_reapr.domain.model

data class ThreatIntelReport(
    val reportedAt: String,
    val comment: String,
    val categories: List<Int>,
    val reporterCountryCode: String?
)

data class ThreatIntelResult(
    val ipAddress: String,
    val isPublic: Boolean,
    val ipVersion: Int,
    val isWhitelisted: Boolean,
    val abuseConfidenceScore: Int,
    val countryCode: String?,
    val countryName: String?,
    val usageType: String?,
    val isp: String?,
    val domain: String?,
    val hostnames: List<String>,
    val totalReports: Int,
    val numDistinctUsers: Int,
    val lastReportedAt: String?,
    val reports: List<ThreatIntelReport>
)
