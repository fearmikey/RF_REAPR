package com.fearmikey.rf_reapr.domain.repository

import kotlinx.coroutines.flow.Flow

data class DiscoveredService(
    val name: String,
    /** Normalized DNS-SD type, e.g. `_http._tcp`. */
    val type: String,
    /** Preferred (IPv4 if available) address. */
    val host: String?,
    val port: Int,
    val attributes: Map<String, String> = emptyMap(),
    /** Every address the service resolved to (API 34+ can return several). */
    val addresses: List<String> = listOfNotNull(host)
)

interface ServiceDiscoveryRepository {
    /**
     * Browses mDNS/DNS-SD for as long as the flow is collected, emitting the full list of
     * resolved services each time it changes.
     */
    fun startDiscovery(): Flow<List<DiscoveredService>>
    fun stopDiscovery()
}
