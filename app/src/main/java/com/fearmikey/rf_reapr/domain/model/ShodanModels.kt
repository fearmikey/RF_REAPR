package com.fearmikey.rf_reapr.domain.model

import com.google.gson.annotations.SerializedName

data class ShodanHostReport(
    val ip_str: String,
    val org: String?,
    val isp: String?,
    val asn: String?,
    val os: String?,
    val ports: List<Int>?,
    val hostnames: List<String>?,
    val domains: List<String>?,
    val city: String?,
    val country_name: String?,
    val latitude: Double?,
    val longitude: Double?,
    val data: List<ShodanServiceData>?
)

data class ShodanServiceData(
    val port: Int,
    val transport: String?,
    val data: String?,
    val timestamp: String?,
    @SerializedName("_shodan") val shodan: ShodanMetadata?
)

data class ShodanMetadata(
    val module: String?
)

data class ShodanSearchResponse(
    val total: Int,
    val matches: List<ShodanMatch>
)

data class ShodanMatch(
    val ip_str: String,
    val port: Int,
    val org: String?,
    val data: String?,
    val hostnames: List<String>?,
    val location: ShodanLocation?,
    val timestamp: String?
)

data class ShodanLocation(
    val city: String?,
    val country_name: String?,
    val latitude: Double?,
    val longitude: Double?
)
