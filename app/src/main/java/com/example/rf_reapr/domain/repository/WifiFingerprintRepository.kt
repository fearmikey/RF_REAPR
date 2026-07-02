package com.example.rf_reapr.domain.repository

import com.example.rf_reapr.domain.model.WifiAccessPoint
import kotlinx.coroutines.flow.Flow

interface WifiFingerprintRepository {
    fun startWifiScan(): Flow<List<WifiAccessPoint>>
    fun stopWifiScan()
}
