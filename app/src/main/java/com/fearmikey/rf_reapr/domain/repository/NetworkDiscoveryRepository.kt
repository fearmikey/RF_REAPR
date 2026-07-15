package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.model.NetworkNode
import com.fearmikey.rf_reapr.domain.scanner.NetworkScanner
import kotlinx.coroutines.flow.Flow

interface NetworkDiscoveryRepository : NetworkScanner<NetworkNode> {
    /**
     * Discovers devices on the current subnet.
     */
    fun discoverDevices(): Flow<NetworkScanner.ScanResult<NetworkNode>>
    
    /**
     * Gets the local network information (Local IP, Gateway, etc.)
     */
    suspend fun getLocalNetworkInfo(): LocalNetworkInfo
}

data class LocalNetworkInfo(
    val localIp: String,
    val gatewayIp: String?,
    val subnetMask: String,
    val prefixLength: Int
)
