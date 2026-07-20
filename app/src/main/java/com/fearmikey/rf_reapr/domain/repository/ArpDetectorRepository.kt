package com.fearmikey.rf_reapr.domain.repository

import kotlinx.coroutines.flow.Flow

interface ArpDetectorRepository {
    val gatewayInfo: Flow<GatewayInfo>
    val alerts: Flow<List<ArpAlert>>
    
    fun startMonitoring()
    fun stopMonitoring()
    fun clearAlerts()
}

data class GatewayInfo(
    val ipAddress: String,
    val macAddress: String,
    val ssid: String,
    val isSecure: Boolean = true
)

data class ArpAlert(
    val timestamp: Long,
    val message: String,
    val oldMac: String,
    val newMac: String,
    val severity: AlertSeverity
)

enum class AlertSeverity {
    INFO, WARNING, CRITICAL
}
