package com.fearmikey.rf_reapr.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.model.OpenPort
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.domain.repository.LogRepository
import com.fearmikey.rf_reapr.domain.repository.PortScannerRepository
import com.fearmikey.rf_reapr.domain.repository.ScanSessionRepository
import com.fearmikey.rf_reapr.domain.repository.SettingsRepository
import com.fearmikey.rf_reapr.domain.repository.VulnerabilityRepository
import com.fearmikey.rf_reapr.domain.scanner.NetworkScanner
import com.fearmikey.rf_reapr.domain.service.ActiveTaskMonitor
import com.google.gson.Gson
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import java.util.Locale

class PortScannerViewModel(
    private val portScannerRepository: PortScannerRepository,
    private val vulnerabilityRepository: VulnerabilityRepository,
    private val scanSessionRepository: ScanSessionRepository,
    private val logRepository: LogRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val isApiKeySet: StateFlow<Boolean> = settingsRepository.vulnerabilityApiKey
        .map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isPassiveMode: StateFlow<Boolean> = settingsRepository.isPassiveMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _foundPorts = MutableStateFlow<List<OpenPort>>(emptyList())
    val foundPorts: StateFlow<List<OpenPort>> = _foundPorts.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _eta = MutableStateFlow<String?>(null)
    val eta: StateFlow<String?> = _eta.asStateFlow()

    private var scanJob: Job? = null
    private var startTime: Long = 0
    private val gson = Gson()

    init {
        viewModelScope.launch {
            ActiveTaskMonitor.stopAllSignal.collect {
                stopScan()
            }
        }
    }

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

    private var currentTargetIp: String = ""

    fun startScan(ipAddress: String, fromPort: Int, toPort: Int) {
        stopScan() // Cancel any existing scan
        currentTargetIp = ipAddress
        portScannerRepository.setConfig(ipAddress, fromPort..toPort)
        initiateScan()
    }

    fun startCommonPortsScan(ipAddress: String) {
        stopScan()
        currentTargetIp = ipAddress
        portScannerRepository.setConfig(ipAddress, COMMON_PORTS)
        initiateScan()
    }

    private fun initiateScan() {
        if (isPassiveMode.value) {
            _isScanning.value = false
            return
        }
        startTime = System.currentTimeMillis()
        _eta.value = "Calculating..."
        _isScanning.value = true
        _progress.value = 0f
        _foundPorts.value = emptyList()
        
        val taskName = "Port Scan ($currentTargetIp)"
        ActiveTaskMonitor.addTask(taskName)

        scanJob = viewModelScope.launch {
            try {
                portScannerRepository.startScan().collect { result ->
                    when (result) {
                        is NetworkScanner.ScanResult.Progress -> {
                            calculateEta(result.progress)
                            _progress.value = result.progress
                            _foundPorts.value = result.foundData
                        }
                        is NetworkScanner.ScanResult.Finished -> {
                            _eta.value = null
                            _isScanning.value = false
                            _progress.value = 1f
                            performVulnerabilityAudit(currentTargetIp, result.foundData)
                        }
                        is NetworkScanner.ScanResult.Error -> {
                            _eta.value = null
                            _isScanning.value = false
                            // Error handling could be improved but keeping current behavior
                        }
                        else -> {}
                    }
                }
            } finally {
                ActiveTaskMonitor.removeTask(taskName)
            }
        }
    }

    private fun calculateEta(progress: Float) {
        if (progress <= 0f) return
        val elapsed = System.currentTimeMillis() - startTime
        val totalEstimated = (elapsed / progress).toLong()
        val remaining = totalEstimated - elapsed
        
        val seconds = (remaining / 1000) % 60
        val minutes = (remaining / (1000 * 60)) % 60
        _eta.value = String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    private suspend fun performVulnerabilityAudit(ipAddress: String, ports: List<OpenPort>) {
        val auditedPorts = ports.map { port ->
            val vulns = vulnerabilityRepository.lookupVulnerabilities(port.serviceName, port.banner)
            port.copy(vulnerabilities = vulns)
        }
        _foundPorts.value = auditedPorts
        
        // Log results
        viewModelScope.launch {
            logRepository.saveLog(
                type = "PORT",
                summary = "Scanned $ipAddress, found ${auditedPorts.size} open ports",
                detailJson = gson.toJson(mapOf("ip" to ipAddress, "ports" to auditedPorts))
            )
        }
        
        // Update persisted node info
        val existingNode = scanSessionRepository.getNodeByIp(ipAddress)
        if (existingNode != null) {
            val updatedNode = existingNode.copy(
                openPorts = auditedPorts,
                riskLevel = calculateAggregatedRisk(auditedPorts)
            )
            scanSessionRepository.updateNodeDetails(updatedNode)
        }
    }

    private fun calculateAggregatedRisk(ports: List<OpenPort>): RiskLevel {
        val allVulns = ports.flatMap { it.vulnerabilities }
        return when {
            allVulns.any { it.severity == RiskLevel.CRITICAL } -> RiskLevel.CRITICAL
            allVulns.any { it.severity == RiskLevel.HIGH } -> RiskLevel.HIGH
            allVulns.any { it.severity == RiskLevel.MEDIUM } -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        portScannerRepository.stopScan()
        _isScanning.value = false
        _eta.value = null
    }

    fun clearResults() {
        stopScan()
        currentTargetIp = ""
        _foundPorts.value = emptyList()
        _progress.value = 0f
    }
}
