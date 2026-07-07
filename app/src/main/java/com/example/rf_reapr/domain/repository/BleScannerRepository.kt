package com.example.rf_reapr.domain.repository

import com.example.rf_reapr.domain.model.BleDevice
import com.example.rf_reapr.domain.scanner.NetworkScanner
import kotlinx.coroutines.flow.Flow

interface BleScannerRepository : NetworkScanner<BleDevice> {
    fun getDiscoveredDevices(): Flow<List<BleDevice>>
    fun startScan(active: Boolean): Flow<NetworkScanner.ScanResult<BleDevice>>
}
