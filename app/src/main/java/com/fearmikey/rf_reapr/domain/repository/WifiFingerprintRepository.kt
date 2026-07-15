package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.model.WifiAccessPoint
import kotlinx.coroutines.flow.Flow

interface WifiFingerprintRepository {
    fun startWifiScan(): Flow<List<WifiAccessPoint>>
    fun stopWifiScan()
}
