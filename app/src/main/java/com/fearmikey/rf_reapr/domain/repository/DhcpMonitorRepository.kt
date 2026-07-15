package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.model.MonitoredDevice
import kotlinx.coroutines.flow.Flow

interface DhcpMonitorRepository {
    val activeDevices: Flow<List<MonitoredDevice>>
    fun startMonitoring(): Flow<MonitoringStatus>
    fun stopMonitoring()
    fun acknowledgeNewDevices()

    sealed class MonitoringStatus {
        object Idle : MonitoringStatus()
        object Scanning : MonitoringStatus()
        data class DeviceDetected(val device: MonitoredDevice) : MonitoringStatus()
    }
}
