package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.domain.model.MonitoredDevice
import com.fearmikey.rf_reapr.domain.repository.NetworkDiscoveryRepository
import com.fearmikey.rf_reapr.domain.repository.DhcpMonitorRepository
import com.fearmikey.rf_reapr.domain.scanner.NetworkScanner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.awaitClose
import java.util.Collections

class DhcpMonitorRepositoryImpl(
    private val discoveryRepository: NetworkDiscoveryRepository
) : DhcpMonitorRepository {

    private val _activeDevices = MutableStateFlow<List<MonitoredDevice>>(emptyList())
    override val activeDevices: StateFlow<List<MonitoredDevice>> = _activeDevices.asStateFlow()

    private val knownIps = Collections.synchronizedSet(mutableSetOf<String>())
    private var monitorJob: Job? = null
    private var isInitialScan = true

    override fun startMonitoring(): Flow<DhcpMonitorRepository.MonitoringStatus> = callbackFlow {
        monitorJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                trySend(DhcpMonitorRepository.MonitoringStatus.Scanning)
                discoveryRepository.discoverDevices().collect { result ->
                    if (result is NetworkScanner.ScanResult.Finished) {
                        result.foundData.forEach { node ->
                            if (!knownIps.contains(node.ipAddress)) {
                                val newDevice = MonitoredDevice(
                                    ipAddress = node.ipAddress,
                                    hostname = node.hostname,
                                    isNew = !isInitialScan
                                )
                                knownIps.add(node.ipAddress)
                                updateDeviceList(newDevice)
                                if (!isInitialScan) {
                                    trySend(DhcpMonitorRepository.MonitoringStatus.DeviceDetected(newDevice))
                                }
                            }
                        }
                        isInitialScan = false
                    }
                }
                // Wait before next scan
                delay(30000) 
            }
        }
        
        awaitClose { stopMonitoring() }
    }

    override fun stopMonitoring() {
        monitorJob?.cancel()
    }

    override fun acknowledgeNewDevices() {
        _activeDevices.value = _activeDevices.value.map { it.copy(isNew = false) }
    }

    private fun updateDeviceList(device: MonitoredDevice) {
        val current = _activeDevices.value.toMutableList()
        val index = current.indexOfFirst { it.ipAddress == device.ipAddress }
        if (index != -1) {
            current[index] = device
        } else {
            current.add(device)
        }
        _activeDevices.value = current.sortedBy { it.ipAddress }
    }
}
