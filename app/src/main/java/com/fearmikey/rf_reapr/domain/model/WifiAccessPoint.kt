package com.fearmikey.rf_reapr.domain.model

import com.fearmikey.rf_reapr.domain.service.WifiSecurityAnalyzer

data class WifiAccessPoint(
    val ssid: String,
    val bssid: String,
    val signalLevel: Int, // RSSI
    val frequency: Int,
    val bandwidth: Int, // in MHz
    val capabilities: String, // Encryption etc.
    val isRogueSuspect: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val vendor: String? = null,
    val rogueReason: String? = null,
    /** Wi-Fi generation from ScanResult.wifiStandard (e.g. "Wi-Fi 6"), API 30+. */
    val standard: String? = null,
    val isConnected: Boolean = false
) {
    fun getChannel(): Int = WifiSecurityAnalyzer.channel(frequency)
}
