package com.example.rf_reapr.ui.topology

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rf_reapr.domain.layout.TopologyLayoutEngine
import com.example.rf_reapr.domain.mapper.TopologyMapper
import com.example.rf_reapr.domain.model.*
import com.example.rf_reapr.domain.repository.*
import com.example.rf_reapr.domain.scanner.NetworkScanner
import com.example.rf_reapr.domain.service.DeviceIdentificationService
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TopologyViewModel(
    private val discoveryRepository: NetworkDiscoveryRepository,
    private val scanSessionRepository: ScanSessionRepository,
    private val portScannerRepository: PortScannerRepository,
    private val vulnerabilityRepository: VulnerabilityRepository,
    private val logRepository: LogRepository
) : ViewModel() {

    private val _mappedGraph = MutableStateFlow<MappedGraph?>(null)
    val mappedGraph: StateFlow<MappedGraph?> = _mappedGraph.asStateFlow()

    private val _discoveryState = MutableStateFlow<NetworkScanner.ScanResult<NetworkNode>>(NetworkScanner.ScanResult.Idle)
    val discoveryState: StateFlow<NetworkScanner.ScanResult<NetworkNode>> = _discoveryState.asStateFlow()

    private val _isAuditing = MutableStateFlow(false)
    val isAuditing: StateFlow<Boolean> = _isAuditing.asStateFlow()
    
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
        viewModelScope.launch {
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
                }
            }
        }
    }

    /**
     * Audits all current nodes for high-impact ports and vulnerabilities.
     */
    fun auditHighImpactPorts() {
        viewModelScope.launch {
            _isAuditing.value = true
            val nodes = _mappedGraph.value?.nodes?.map { it.node } ?: emptyList()
            nodes.forEach { node ->
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
