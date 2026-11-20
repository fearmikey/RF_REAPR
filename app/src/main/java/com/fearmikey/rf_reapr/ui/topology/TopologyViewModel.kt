package com.fearmikey.rf_reapr.ui.topology

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.layout.TopologyLayoutEngine
import com.fearmikey.rf_reapr.domain.mapper.TopologyMapper
import com.fearmikey.rf_reapr.domain.model.*
import com.fearmikey.rf_reapr.domain.repository.*
import com.fearmikey.rf_reapr.domain.scanner.NetworkScanner
import com.fearmikey.rf_reapr.domain.service.ActiveTaskMonitor
import com.fearmikey.rf_reapr.domain.service.DeviceIdentificationService
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The kinds of per-device scans that can be triggered from the device detail sheet. */
enum class NodeScanKind { PORTS, SNMP, RECON }

/** Progress of a network-wide deep scan (ports + SNMP across every node). */
data class AuditProgress(
    val completed: Int,
    val total: Int,
    val currentIp: String?
) {
    val fraction: Float get() = if (total == 0) 0f else completed.toFloat() / total
}

class TopologyViewModel(
    private val discoveryRepository: NetworkDiscoveryRepository,
    private val scanSessionRepository: ScanSessionRepository,
    private val portScannerRepository: PortScannerRepository,
    private val internetDbRepository: InternetDbRepository,
    private val vulnerabilityRepository: VulnerabilityRepository,
    private val snmpRepository: SnmpRepository,
    private val logRepository: LogRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    val isApiKeySet: StateFlow<Boolean> = settingsRepository.vulnerabilityApiKey
        .map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initialValue = true)

    val isPassiveMode: StateFlow<Boolean> = settingsRepository.isPassiveMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _mappedGraph = MutableStateFlow<MappedGraph?>(null)
    val mappedGraph: StateFlow<MappedGraph?> = _mappedGraph.asStateFlow()

    private val _discoveryState = MutableStateFlow<NetworkScanner.ScanResult<NetworkNode>>(NetworkScanner.ScanResult.Idle)
    val discoveryState: StateFlow<NetworkScanner.ScanResult<NetworkNode>> = _discoveryState.asStateFlow()

    private val _isAuditing = MutableStateFlow(false)
    val isAuditing: StateFlow<Boolean> = _isAuditing.asStateFlow()

    private val _auditProgress = MutableStateFlow<AuditProgress?>(null)
    val auditProgress: StateFlow<AuditProgress?> = _auditProgress.asStateFlow()

    // True once a network-wide deep scan has completed for the current discovery session,
    // used to hide the "Run Deep Scan" call-to-action so it stops covering the map.
    private val _deepScanCompleted = MutableStateFlow(false)
    val deepScanCompleted: StateFlow<Boolean> = _deepScanCompleted.asStateFlow()

    // Per-device scans currently running, keyed by node id.
    private val _activeNodeScans = MutableStateFlow<Map<String, Set<NodeScanKind>>>(emptyMap())
    val activeNodeScans: StateFlow<Map<String, Set<NodeScanKind>>> = _activeNodeScans.asStateFlow()

    private val _showNetworkMismatchDialog = MutableStateFlow(false)
    val showNetworkMismatchDialog: StateFlow<Boolean> = _showNetworkMismatchDialog.asStateFlow()

    // One-shot, user-facing messages (scan results, errors) shown as snackbars.
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    // Latest per-device scan result text, keyed by node id. Shown inside the device sheet,
    // since snackbars are hidden behind the modal sheet while it is open.
    private val _nodeResults = MutableStateFlow<Map<String, String>>(emptyMap())
    val nodeResults: StateFlow<Map<String, String>> = _nodeResults.asStateFlow()

    // IP address of the phone/tablet running the scan, used to highlight "my device" on
    // the map and list views.
    private val _localDeviceIp = MutableStateFlow<String?>(null)
    val localDeviceIp: StateFlow<String?> = _localDeviceIp.asStateFlow()

    private val gson = Gson()

    init {
        loadPersistedNodes()

        // Allow the "Stop All Tasks" notification action to cancel an active discovery/audit.
        viewModelScope.launch {
            ActiveTaskMonitor.stopAllSignal.collect {
                discoveryRepository.stopScan()
                portScannerRepository.stopScan()
            }
        }
    }

    private fun loadPersistedNodes() {
        viewModelScope.launch {
            scanSessionRepository.getAllPersistedNodes().collect { nodes ->
                if (nodes.isNotEmpty()) {
                    val networkInfo = discoveryRepository.getLocalNetworkInfo()
                    _localDeviceIp.value = networkInfo.localIp
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
        val taskName = "Network Discovery"
        viewModelScope.launch {
            ActiveTaskMonitor.addTask(taskName)
            try {
                // Clear previous scan data before starting a new discovery session
                scanSessionRepository.clearAllData()
                _mappedGraph.value = null
                _deepScanCompleted.value = false
                _nodeResults.value = emptyMap()

                val networkInfo = discoveryRepository.getLocalNetworkInfo()
                _localDeviceIp.value = networkInfo.localIp
                discoveryRepository.discoverDevices().collect { result ->
                    _discoveryState.value = result
                    when (result) {
                        is NetworkScanner.ScanResult.Progress -> {
                            val percent = (result.progress * 100).toInt()
                            ActiveTaskMonitor.updateStatus("$taskName: $percent%")
                        }
                        is NetworkScanner.ScanResult.Error -> {
                            _messages.trySend("Discovery failed: ${result.message}")
                        }
                        is NetworkScanner.ScanResult.Finished -> {
                            var nodes = result.foundData.map { node ->
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

                            // The scanning device itself doesn't always respond to its own
                            // liveness probes (no open ports, pinging self can behave
                            // differently across devices) - ensure it's always represented
                            // on the map so it can be highlighted as "my device".
                            val localIp = networkInfo.localIp
                            if (nodes.none { it.ipAddress == localIp }) {
                                nodes = nodes + NetworkNode(
                                    id = localIp,
                                    ipAddress = localIp,
                                    deviceType = DeviceType.MOBILE
                                )
                            }

                            scanSessionRepository.saveDiscoveredNodes(
                                nodes = nodes,
                                networkName = "Local Network",
                                gatewayIp = networkInfo.gatewayIp
                            )
                            _messages.trySend("Discovery complete: ${nodes.size} devices found")
                        }
                        else -> Unit
                    }
                }
            } finally {
                ActiveTaskMonitor.removeTask(taskName)
            }
        }
    }

    private suspend fun querySnmpForNode(node: NetworkNode): Boolean {
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
        return result.isSuccess
    }

    /**
     * Checks if current network matches the one from the last scan.
     * If not, prompts for rescan. Otherwise, starts audit.
     */
    fun auditHighImpactPorts() {
        if (isPassiveMode.value || _isAuditing.value) return
        if (isPortScannerBusy) {
            _messages.trySend("Wait for the current device port scan to finish")
            return
        }
        viewModelScope.launch {
            val currentNetwork = discoveryRepository.getLocalNetworkInfo()
            val lastGatewayIp = scanSessionRepository.getLastSessionGatewayIp()

            if ((lastGatewayIp != null && currentNetwork.gatewayIp != lastGatewayIp)) {
                _showNetworkMismatchDialog.value = true
                return@launch
            }

            val taskName = "Deep Scan (Ports & SNMP)"
            ActiveTaskMonitor.addTask(taskName)
            _isAuditing.value = true
            try {
                val nodes = _mappedGraph.value?.nodes?.map { it.node } ?: emptyList()
                _auditProgress.value = AuditProgress(0, nodes.size, null)
                nodes.forEachIndexed { index, node ->
                    _auditProgress.value = AuditProgress(index, nodes.size, node.ipAddress)
                    ActiveTaskMonitor.updateStatus("$taskName: ${index + 1}/${nodes.size} (${node.ipAddress})")
                    scanSingleNode(node)
                    querySnmpForNode(node)
                }
                _auditProgress.value = AuditProgress(nodes.size, nodes.size, null)
                _deepScanCompleted.value = true

                // Log the audit completion
                _mappedGraph.value?.let { currentGraph ->
                    val atRisk = currentGraph.nodes.count { it.node.riskLevel >= RiskLevel.HIGH }
                    logRepository.saveLog(
                        type = "TOPOLOGY",
                        summary = "Completed audit (Ports & SNMP) of ${currentGraph.nodes.size} network nodes",
                        detailJson = gson.toJson(currentGraph)
                    )
                    _messages.trySend(
                        "Deep scan complete: ${currentGraph.nodes.size} devices, $atRisk high/critical risk"
                    )
                }
            } finally {
                _isAuditing.value = false
                _auditProgress.value = null
                ActiveTaskMonitor.removeTask(taskName)
            }
        }
    }

    private fun setNodeScanActive(nodeId: String, kind: NodeScanKind, active: Boolean) {
        _activeNodeScans.update { current ->
            val kinds = current[nodeId].orEmpty().let { if (active) it + kind else it - kind }
            if (kinds.isEmpty()) current - nodeId else current + (nodeId to kinds)
        }
    }

    private fun reportNodeResult(node: NetworkNode, message: String) {
        _nodeResults.update { it + (node.id to message) }
        _messages.trySend(message)
    }

    /** True while the shared port scanner is busy (it only supports one target at a time). */
    private val isPortScannerBusy: Boolean
        get() = _isAuditing.value || _activeNodeScans.value.values.any { NodeScanKind.PORTS in it }

    fun scanSingleNodeTrigger(node: NetworkNode) {
        if (isPassiveMode.value) return
        if (isPortScannerBusy) {
            _messages.trySend("A port scan is already running")
            return
        }
        viewModelScope.launch {
            setNodeScanActive(node.id, NodeScanKind.PORTS, true)
            try {
                val portCount = scanSingleNode(node)
                reportNodeResult(node, "${node.ipAddress}: $portCount open port${if (portCount == 1) "" else "s"} found")
                logRepository.saveLog(
                    type = "TOPOLOGY",
                    summary = "Completed individual port audit for ${node.ipAddress}",
                    detailJson = gson.toJson(scanSessionRepository.getNodeByIp(node.ipAddress) ?: node)
                )
            } finally {
                setNodeScanActive(node.id, NodeScanKind.PORTS, false)
            }
        }
    }

    fun scanSnmpSingleNodeTrigger(node: NetworkNode) {
        if (isPassiveMode.value) return
        if (_activeNodeScans.value[node.id]?.contains(NodeScanKind.SNMP) == true) return
        viewModelScope.launch {
            setNodeScanActive(node.id, NodeScanKind.SNMP, true)
            try {
                val responded = querySnmpForNode(node)
                reportNodeResult(
                    node,
                    if (responded) "${node.ipAddress}: SNMP responded (community 'public')"
                    else "${node.ipAddress}: no SNMP response"
                )
                logRepository.saveLog(
                    type = "TOPOLOGY",
                    summary = "Completed individual SNMP audit for ${node.ipAddress}",
                    detailJson = gson.toJson(scanSessionRepository.getNodeByIp(node.ipAddress) ?: node)
                )
            } finally {
                setNodeScanActive(node.id, NodeScanKind.SNMP, false)
            }
        }
    }

    fun checkExternalExposure(node: NetworkNode) {
        if (_activeNodeScans.value[node.id]?.contains(NodeScanKind.RECON) == true) return
        viewModelScope.launch {
            setNodeScanActive(node.id, NodeScanKind.RECON, true)
            try {
                internetDbRepository.getIpInfo(node.ipAddress)
                    .onSuccess { response ->
                        // Gson can leave these null despite the Kotlin defaults.
                        val ports = response.ports.orEmpty()
                        val vulns = response.vulns.orEmpty()
                        logRepository.saveLog(
                            type = "TOPOLOGY",
                            summary = "External exposure check for ${node.ipAddress} via InternetDB",
                            detailJson = gson.toJson(response)
                        )
                        reportNodeResult(
                            node,
                            if (ports.isEmpty() && vulns.isEmpty()) {
                                "${node.ipAddress}: no public exposure indexed by InternetDB"
                            } else {
                                "${node.ipAddress}: exposed ports ${ports.joinToString()}" +
                                    if (vulns.isNotEmpty()) " · ${vulns.size} known CVEs" else ""
                            }
                        )
                        if (vulns.isNotEmpty()) {
                            val latestNode = scanSessionRepository.getNodeByIp(node.ipAddress) ?: node
                            if (latestNode.riskLevel < RiskLevel.HIGH) {
                                scanSessionRepository.updateNodeDetails(latestNode.copy(riskLevel = RiskLevel.HIGH))
                            }
                        }
                    }
                    .onFailure { error ->
                        reportNodeResult(node, "Quick recon failed: ${error.message ?: "unknown error"}")
                    }
            } finally {
                setNodeScanActive(node.id, NodeScanKind.RECON, false)
            }
        }
    }

    /** Scans [node] for high-impact ports, persists the result, and returns the open-port count. */
    private suspend fun scanSingleNode(node: NetworkNode): Int {
        var openPortCount = 0
        portScannerRepository.scan(node.ipAddress, NetworkNode.HIGH_IMPACT_PORTS).collect { result ->
            if (result is NetworkScanner.ScanResult.Finished) {
                // 1. Fetch latest state to avoid overwriting manual changes
                val latestNode = scanSessionRepository.getNodeByIp(node.ipAddress) ?: node
                
                // 2. Audit found ports for vulnerabilities
                val auditedPorts = result.foundData.map { port ->
                    val vulns = vulnerabilityRepository.lookupVulnerabilities(port.serviceName, port.banner)
                    port.copy(vulnerabilities = vulns)
                }
                openPortCount = auditedPorts.size

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
        return openPortCount
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
            _deepScanCompleted.value = false
            _nodeResults.value = emptyMap()
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
