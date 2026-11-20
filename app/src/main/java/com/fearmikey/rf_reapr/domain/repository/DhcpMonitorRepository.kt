package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.model.MonitorStats
import com.fearmikey.rf_reapr.domain.model.MonitoredDevice
import com.fearmikey.rf_reapr.domain.model.NetworkEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface DhcpMonitorRepository {
    val activeDevices: StateFlow<List<MonitoredDevice>>
    /** Newest first. */
    val events: StateFlow<List<NetworkEvent>>
    val stats: StateFlow<MonitorStats>

    /** Sweeps the subnet every [intervalMs] for as long as the flow is collected. */
    fun startMonitoring(intervalMs: Long = DEFAULT_INTERVAL_MS): Flow<MonitoringStatus>
    fun stopMonitoring()
    fun acknowledgeNewDevices()

    /** Forgets all devices and events; the next sweep becomes the new baseline. */
    fun resetBaseline()

    sealed class MonitoringStatus {
        data object Idle : MonitoringStatus()
        data class Scanning(val progress: Float = 0f) : MonitoringStatus()
        data class Waiting(val nextSweepAt: Long) : MonitoringStatus()
        data class DeviceDetected(val device: MonitoredDevice, val reason: String) : MonitoringStatus()
        data class DeviceLeft(val device: MonitoredDevice) : MonitoringStatus()
        data class Error(val message: String) : MonitoringStatus()
    }

    companion object {
        const val DEFAULT_INTERVAL_MS = 30_000L
    }
}
