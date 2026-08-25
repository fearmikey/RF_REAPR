package com.fearmikey.rf_reapr.domain.model

data class InternetDbResponse(
    val ip: String,
    val ports: List<Int> = emptyList(),
    val hostnames: List<String> = emptyList(),
    val cpes: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val vulns: List<String> = emptyList()
)
