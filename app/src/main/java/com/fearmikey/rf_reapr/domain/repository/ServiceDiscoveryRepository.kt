package com.fearmikey.rf_reapr.domain.repository

import kotlinx.coroutines.flow.Flow

data class DiscoveredService(
    val name: String,
    val type: String,
    val host: String?,
    val port: Int,
    val attributes: Map<String, String> = emptyList<Pair<String, String>>().toMap()
)

interface ServiceDiscoveryRepository {
    fun startDiscovery(): Flow<List<DiscoveredService>>
    fun stopDiscovery()
}
