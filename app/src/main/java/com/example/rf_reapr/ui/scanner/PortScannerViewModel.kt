package com.example.rf_reapr.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rf_reapr.domain.model.OpenPort
import com.example.rf_reapr.domain.repository.PortScannerRepository
import com.example.rf_reapr.domain.repository.VulnerabilityRepository
import com.example.rf_reapr.domain.scanner.NetworkScanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PortScannerViewModel(
    private val portScannerRepository: PortScannerRepository,
    private val vulnerabilityRepository: VulnerabilityRepository
) : ViewModel() {

    private val _scanState = MutableStateFlow<NetworkScanner.ScanResult<OpenPort>>(NetworkScanner.ScanResult.Idle)
    val scanState: StateFlow<NetworkScanner.ScanResult<OpenPort>> = _scanState.asStateFlow()

    private var scanJob: Job? = null

    companion object {
        val COMMON_PORTS = listOf(
            1, 7, 20, 21, 22, 23, 25, 42, 43, 53, 69, 79, 80, 88, 106, 110, 111, 113, 115, 119, 123, 135, 137, 138, 139, 
            143, 161, 177, 194, 311, 379, 389, 427, 443, 445, 464, 465, 497, 514, 515, 532, 548, 554, 587, 600, 625, 631, 
            636, 660, 687, 749, 985, 993, 995, 1080, 1085, 1194, 1099, 1220, 1433, 1434, 1521, 1522, 1525, 1529, 1640, 
            1649, 1723, 1990, 1998, 2049, 2195, 2196, 2336, 3004, 3031, 3128, 3283, 3306, 3389, 3689, 4111, 4488, 5000, 
            5001, 5003, 5009, 5010, 5060, 5100, 5190, 5200, 5222, 5223, 5269, 5298, 5432, 5500, 5632, 5800, 5900, 5988, 
            6000, 7070, 7777, 8005, 8008, 8043, 8080, 8085, 8086, 8087, 8088, 8089, 8096, 8170, 8171, 8175, 8200, 8443, 
            8800, 8821, 8826, 8843, 8880, 8891, 9006, 9100, 10000, 10001, 10002, 10010, 20005
        )
    }

    fun startScan(ipAddress: String, fromPort: Int, toPort: Int) {
        stopScan() // Cancel any existing scan
        
        portScannerRepository.setConfig(ipAddress, fromPort..toPort)
        initiateScan()
    }

    fun startCommonPortsScan(ipAddress: String) {
        stopScan()
        portScannerRepository.setConfig(ipAddress, COMMON_PORTS)
        initiateScan()
    }

    private fun initiateScan() {
        scanJob = viewModelScope.launch {
            portScannerRepository.startScan().collect { result ->
                when (result) {
                    is NetworkScanner.ScanResult.Finished -> {
                        performVulnerabilityAudit(result.foundData)
                    }
                    else -> {
                        _scanState.value = result
                    }
                }
            }
        }
    }

    private suspend fun performVulnerabilityAudit(ports: List<OpenPort>) {
        val auditedPorts = ports.map { port ->
            val vulns = vulnerabilityRepository.lookupVulnerabilities(port.serviceName, port.banner)
            port.copy(vulnerabilities = vulns)
        }
        _scanState.value = NetworkScanner.ScanResult.Finished(auditedPorts)
    }

    fun stopScan() {
        scanJob?.cancel()
        portScannerRepository.stopScan()
        if (_scanState.value is NetworkScanner.ScanResult.Progress) {
            val currentProgress = (_scanState.value as NetworkScanner.ScanResult.Progress).foundData
            _scanState.value = NetworkScanner.ScanResult.Finished(currentProgress)
        }
    }
}
