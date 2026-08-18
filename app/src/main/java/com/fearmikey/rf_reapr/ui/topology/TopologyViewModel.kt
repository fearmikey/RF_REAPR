package com.fearmikey.rf_reapr.ui.topology

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.layout.TopologyLayoutEngine
import com.fearmikey.rf_reapr.domain.mapper.TopologyMapper
import com.fearmikey.rf_reapr.domain.model.*
import com.fearmikey.rf_reapr.domain.repository.*
import com.fearmikey.rf_reapr.domain.scanner.NetworkScanner
import com.fearmikey.rf_reapr.domain.service.DeviceIdentificationService
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TopologyViewModel(
    private val discoveryRepository: NetworkDiscoveryRepository,
    private val scanSessionRepository: ScanSessionRepository,
    private val portScannerRepository: PortScannerRepository,
    private val vulnerabilityRepository: VulnerabilityRepository,
    private val snmpRepository: SnmpRepository,
    private val logRepository: LogRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val isApiKeySet: StateFlow<Boolean> = settingsRepository.vulnerabilityApiKey
        .map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isPassiveMode: StateFlow<Boolean> = settingsRepository.isPassiveMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _mappedGraph = MutableStateFlow<MappedGraph?>(null)
    val mappedGraph: StateFlow<MappedGraph?> = _mappedGraph.asStateFlow()

    private val _discoveryState = MutableStateFlow<NetworkScanner.ScanResult<NetworkNode>>(NetworkScanner.ScanResult.Idle)
    val discoveryState: StateFlow<NetworkScanner.ScanResult<NetworkNode>> = _discoveryState.asStateFlow()

    private val _isAuditing = MutableStateFlow(false)
    val isAuditing: StateFlow<Boolean> = _isAuditing.asStateFlow()

    private val _autoPortScan = MutableStateFlow(true)
    val autoPortScan: StateFlow<Boolean> = _autoPortScan.asStateFlow()

    private val _autoSnmpDiscovery = MutableStateFlow(true)
    val autoSnmpDiscovery: StateFlow<Boolean> = _autoSnmpDiscovery.asStateFlow()

    private val _showNetworkMismatchDialog = MutableStateFlow(false)
    val showNetworkMismatchDialog: StateFlow<Boolean> = _showNetworkMismatchDialog.asStateFlow()
    
    private val gson = Gson()

    init {
        loadPersistedNodes()
    }

    private fun loadPersistedNodes() {
        viewModelScope.launch {
            scanSessionRepository.getAllPersistedNodes().collect { nodes ->
                if (nodes.isNotEmpty()) {
                    val networkInfo = discoveryRepository.getLocalNetworkInfo()
                    val mapped = withContext(Dispatchers.Default) {
                        val graph = TopologyMapper.mapDiscoveredNodesToGraph(
                            nodes = nodes,
                            gatewayIp = networkInfo.gatewayIp
                        )
                        TopologyLayoutEngine.calculateRadialLayout(
                            graph = graph,
                            gatewayIp = networkInfo.gatewayIp
                        )
                    }
                    _mappedGraph.value = mapped
                } else {
                    _mappedGraph.value = null
                }
            }
        }
    }

    /**
     * Starts a full network discovery and maps the topology.
     */
    fun startNetworkDiscovery() {
        if (isPassiveMode.value) return
        viewModelScope.launch {
            // Clear previous scan data before starting a new discovery session
            scanSessionRepository.clearAllData()
            _mappedGraph.value = null
            
            val networkInfo = discoveryRepository.getLocalNetworkInfo()
            discoveryRepository.discoverDevices().collect { result ->
                _discoveryState.value = result
                if (result is NetworkScanner.ScanResult.Finished) {
                    val nodes = result.foundData.map { node ->
                        node.copy(
                            deviceType = DeviceIdentificationService.identifyDevice(
                                ipAddress = node.ipAddress,
                                macAddress = node.macAddress,
                                hostname = node.hostname,
                                gatewayIp = networkInfo.gatewayIp
                            ),
                            manufacturer = DeviceIdentificationService.getManufacturer(node.macAddress)
                        )
                    }
                    scanSessionRepository.saveDiscoveredNodes(
                        nodes = nodes,
                        networkName = "Local Network",
                        gatewayIp = networkInfo.gatewayIp
                    )

                    // After discovery, perform auto actions if enabled
                    if (autoPortScan.value || autoSnmpDiscovery.value) {
                        performAutoDiscoveryActions(nodes)
                    }
                }
            }
        }
    }

    private fun performAutoDiscoveryActions(nodes: List<NetworkNode>) {
        viewModelScope.launch {
            _isAuditing.value = true
            nodes.forEach { node ->
                if (autoPortScan.value) {
                    scanSingleNode(node)
                }
                if (autoSnmpDiscovery.value) {
                    querySnmpForNode(node)
                }
            }
            _isAuditing.value = false
            
            // Log the completion of discovery + auto audit
            val currentGraph = _mappedGraph.value
            if (currentGraph != null) {
                logRepository.saveLog(
                    type = "TOPOLOGY",
                    summary = "Completed network discovery and auto-audit of ${currentGraph.nodes.size} devices",
                    detailJson = gson.toJson(currentGraph)
                )
            }
        }
    }

    private suspend fun querySnmpForNode(node: NetworkNode) {
        // Simple SNMP V2c public community query as discovery step
        val result = snmpRepository.queryDevice(node.ipAddress, "public", 1)
        result.onSuccess { snmpResult ->
            val latestNode = scanSessionRepository.getNodeByIp(node.ipAddress) ?: node
            val updatedNode = latestNode.copy(
                snmpData = snmpResult,
                // If SNMP responds, it's likely a Network Device or Server
                deviceType = if (latestNode.deviceType == DeviceType.UNKNOWN) DeviceType.NETWORK_DEVICE else latestNode.deviceType
            )
            scanSessionRepository.updateNodeDetails(updatedNode)
        }
    }

    fun toggleAutoPortScan() {
        _autoPortScan.value = !_autoPortScan.value
    }

    fun toggleAutoSnmpDiscovery() {
        _autoSnmpDiscovery.value = !_autoSnmpDiscovery.value
    }

    /**
     * Checks if current network matches the one from the last scan.
     * If not, prompts for rescan. Otherwise, starts audit.
     */
    fun auditHighImpactPorts() {
        if (isPassiveMode.value) return
        viewModelScope.launch {
            val currentNetwork = discoveryRepository.getLocalNetworkInfo()
            val lastGatewayIp = scanSessionRepository.getLastSessionGatewayIp()

            if (lastGatewayIp != null && currentNetwork.gatewayIp != lastGatewayIp) {
                _showNetworkMismatchDialog.value = true
            } else {
                viewModelScope.launch {
                    _isAuditing.value = true
                    val nodes = _mappedGraph.value?.nodes?.map { it.node } ?: emptyList()
                    nodes.forEach { node ->
                        scanSingleNode(node)
                    }
                    _isAuditing.value = false
                    
                    // Log the audit completion
                    val currentGraph = _mappedGraph.value
                    if (currentGraph != null) {
                        logRepository.saveLog(
                            type = "TOPOLOGY",
                            summary = "Completed audit of ${currentGraph.nodes.size} network nodes",
                            detailJson = gson.toJson(currentGraph)
                        )
                    }
                }
            }
        }
    }

    fun scanSingleNodeTrigger(node: NetworkNode) {
        if (isPassiveMode.value) return
        viewModelScope.launch {
            _isAuditing.value = true
            scanSingleNode(node)
            _isAuditing.value = false
            
            logRepository.saveLog(
                type = "TOPOLOGY",
                summary = "Completed individual audit for ${node.ipAddress}",
                detailJson = gson.toJson(node)
            )
        }
    }

    private suspend fun scanSingleNode(node: NetworkNode) {
        portScannerRepository.setConfig(node.ipAddress, NetworkNode.HIGH_IMPACT_PORTS)
        portScannerRepository.startScan().collect { result ->
            if (result is NetworkScanner.ScanResult.Finished) {
                // 1. Fetch latest state to avoid overwriting manual changes
                val latestNode = scanSessionRepository.getNodeByIp(node.ipAddress) ?: node
                
                // 2. Audit found ports for vulnerabilities
                val auditedPorts = result.foundData.map { port ->
                    val vulns = vulnerabilityRepository.lookupVulnerabilities(port.serviceName, port.banner)
                    port.copy(vulnerabilities = vulns)
                }
                
                // 3. Only auto-identify if type is currently UNKNOWN
                val newDeviceType = if (latestNode.deviceType == DeviceType.UNKNOWN) {
                    DeviceIdentificationService.identifyDevice(
                        ipAddress = latestNode.ipAddress,
                        macAddress = latestNode.macAddress,
                        hostname = latestNode.hostname,
                        openPorts = auditedPorts
                    )
                } else {
                    latestNode.deviceType
                }
                
                val updatedNode = latestNode.copy(
                    openPorts = auditedPorts,
                    riskLevel = calculateHighestRisk(auditedPorts),
                    deviceType = newDeviceType,
                    manufacturer = latestNode.manufacturer ?: DeviceIdentificationService.getManufacturer(latestNode.macAddress)
                )
                scanSessionRepository.updateNodeDetails(updatedNode)
            }
        }
    }

    fun dismissNetworkMismatchDialog() {
        _showNetworkMismatchDialog.value = false
    }

    fun confirmNetworkMismatchRescan() {
        _showNetworkMismatchDialog.value = false
        startNetworkDiscovery()
    }

    fun clearData() {
        viewModelScope.launch {
            scanSessionRepository.clearAllData()
            _mappedGraph.value = null
        }
    }

    fun updateDeviceType(node: NetworkNode, newType: DeviceType) {
        viewModelScope.launch {
            scanSessionRepository.updateNodeDetails(node.copy(deviceType = newType))
        }
    }

    fun updateParent(node: NetworkNode, parentId: String?) {
        viewModelScope.launch {
            scanSessionRepository.updateNodeDetails(node.copy(parentId = parentId))
        }
    }

    private fun calculateHighestRisk(ports: List<OpenPort>): RiskLevel {
        val allVulnerabilities = ports.flatMap { it.vulnerabilities }
        if (allVulnerabilities.isEmpty()) return RiskLevel.LOW
        
        return allVulnerabilities
            .asSequence()
            .map { it.severity }
            .maxByOrNull { it.ordinal } ?: RiskLevel.LOW
    }
}
