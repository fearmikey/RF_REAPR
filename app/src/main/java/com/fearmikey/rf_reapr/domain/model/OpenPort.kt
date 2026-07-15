package com.fearmikey.rf_reapr.domain.model

data class OpenPort(
    val port: Int,
    val serviceName: String = "Unknown",
    val banner: String? = null,
    val vulnerabilities: List<Vulnerability> = emptyList()
)
