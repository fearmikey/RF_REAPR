package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.model.WifiAccessPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class WifiScanStatus(
    val wifiEnabled: Boolean = true,
    /** False when Android throttled the request (4 scans / 2 min) and served cached results. */
    val lastRequestAccepted: Boolean = true,
    val lastRequestAt: Long = 0,
    val lastResultsAt: Long = 0,
    val resultCount: Int = 0
)

interface WifiFingerprintRepository {
    val status: StateFlow<WifiScanStatus>
    fun startWifiScan(): Flow<List<WifiAccessPoint>>
    fun stopWifiScan()
}
