package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.scanner.NetworkScanner
import kotlinx.coroutines.flow.Flow

interface UpnpScannerRepository : NetworkScanner<UpnpDevice> {
    fun discoverDevices(): Flow<NetworkScanner.ScanResult<UpnpDevice>>
}

data class UpnpDevice(
    val ipAddress: String,
    val friendlyName: String,
    val manufacturer: String,
    val modelName: String,
    val services: List<UpnpService> = emptyList(),
    val location: String
)

data class UpnpService(
    val serviceType: String,
    val serviceId: String,
    val controlUrl: String,
    val eventSubUrl: String,
    val scpdUrl: String
)
