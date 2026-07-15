package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.model.BleDevice
import com.fearmikey.rf_reapr.domain.scanner.NetworkScanner
import kotlinx.coroutines.flow.Flow

interface BleScannerRepository : NetworkScanner<BleDevice> {
    fun getDiscoveredDevices(): Flow<List<BleDevice>>
    fun startScan(active: Boolean): Flow<NetworkScanner.ScanResult<BleDevice>>
}
