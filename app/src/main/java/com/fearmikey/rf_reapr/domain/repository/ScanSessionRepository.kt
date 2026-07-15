package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.model.NetworkNode
import kotlinx.coroutines.flow.Flow

interface ScanSessionRepository {
    fun getAllPersistedNodes(): Flow<List<NetworkNode>>
    suspend fun saveDiscoveredNodes(nodes: List<NetworkNode>, networkName: String?, gatewayIp: String?)
    suspend fun updateNodeDetails(node: NetworkNode)
    suspend fun getNodeByIp(ip: String): NetworkNode?
    suspend fun clearAllData()
}
