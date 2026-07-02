package com.example.rf_reapr.data.repository

import com.example.rf_reapr.domain.model.MonitoredDevice
import com.example.rf_reapr.domain.repository.NetworkDiscoveryRepository
import com.example.rf_reapr.domain.repository.DhcpMonitorRepository
import com.example.rf_reapr.domain.scanner.NetworkScanner
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

    override fun startMonitoring(): Flow<DhcpMonitorRepository.MonitoringStatus> = callbackFlow {
        monitorJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                trySend(DhcpMonitorRepository.MonitoringStatus.Scanning)
                discoveryRepository.discoverDevices().collect { result ->
                    if (result is NetworkScanner.ScanResult.Finished) {
                        val currentScanIps = result.foundData.map { it.ipAddress }.toSet()
                        
                        result.foundData.forEach { node ->
                            if (!knownIps.contains(node.ipAddress)) {
                                val newDevice = MonitoredDevice(
                                    ipAddress = node.ipAddress,
                                    hostname = node.hostname,
                                    isNew = true
                                )
                                knownIps.add(node.ipAddress)
                                updateDeviceList(newDevice)
                                trySend(DhcpMonitorRepository.MonitoringStatus.DeviceDetected(newDevice))
                            }
                        }
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
