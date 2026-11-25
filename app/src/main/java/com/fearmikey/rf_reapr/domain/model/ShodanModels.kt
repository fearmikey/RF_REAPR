package com.fearmikey.rf_reapr.domain.model

/** A Shodan `/shodan/host/{ip}` report, built by ShodanParser (never by reflection). */
data class ShodanHostReport(
    val ip: String,
    val org: String? = null,
    val isp: String? = null,
    val asn: String? = null,
    val os: String? = null,
    val ports: List<Int> = emptyList(),
    val hostnames: List<String> = emptyList(),
    val domains: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val city: String? = null,
    val country: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val lastUpdate: String? = null,
    /** CVE IDs Shodan associates with the host (inferred from banners, not verified). */
    val vulns: List<String> = emptyList(),
    val services: List<ShodanService> = emptyList()
)

data class ShodanService(
    val port: Int,
    val transport: String? = null,
    val product: String? = null,
    val version: String? = null,
    val module: String? = null,
    val banner: String? = null,
    val timestamp: String? = null,
    val cpes: List<String> = emptyList(),
    val vulns: List<String> = emptyList()
) {
    val productLabel: String?
        get() = listOfNotNull(product, version).joinToString(" ").ifBlank { null }
}

data class ShodanSearchResponse(
    val total: Int = 0,
    val matches: List<ShodanMatch> = emptyList()
)

data class ShodanMatch(
    val ip: String,
    val port: Int,
    val transport: String? = null,
    val org: String? = null,
    val product: String? = null,
    val banner: String? = null,
    val hostnames: List<String> = emptyList(),
    val location: String? = null,
    val timestamp: String? = null
)
