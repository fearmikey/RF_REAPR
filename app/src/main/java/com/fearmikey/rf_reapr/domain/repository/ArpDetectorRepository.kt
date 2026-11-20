package com.fearmikey.rf_reapr.domain.repository

import kotlinx.coroutines.flow.Flow

interface ArpDetectorRepository {
    val gatewayInfo: Flow<GatewayInfo>
    val alerts: Flow<List<ArpAlert>>
    val isMonitoring: Flow<Boolean>

    fun startMonitoring()
    fun stopMonitoring()
    fun clearAlerts()
}

data class GatewayInfo(
    val ipAddress: String,
    val macAddress: String,
    val ssid: String,
    val isSecure: Boolean = true,
    val bssid: String? = null,
    val dnsServers: List<String> = emptyList(),
    /** Whether the kernel ARP table is readable (blocked for apps on Android 10+). */
    val arpTableAvailable: Boolean = false,
    val neighborCount: Int = 0,
    val firstHop: String? = null,
    val lastCheck: Long = 0,
    val checks: Int = 0
)

data class ArpAlert(
    val timestamp: Long,
    val message: String,
    val oldMac: String,
    val newMac: String,
    val severity: AlertSeverity,
    val detail: String = ""
)

enum class AlertSeverity {
    INFO, WARNING, CRITICAL
}
