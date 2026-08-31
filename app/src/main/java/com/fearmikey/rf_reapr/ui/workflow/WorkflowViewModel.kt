package com.fearmikey.rf_reapr.ui.workflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.model.AppMode
import com.fearmikey.rf_reapr.domain.model.NetworkNode
import com.fearmikey.rf_reapr.domain.model.WorkflowStep
import com.fearmikey.rf_reapr.domain.repository.DiscoveredService
import com.fearmikey.rf_reapr.domain.repository.*
import com.fearmikey.rf_reapr.domain.scanner.NetworkScanner
import com.fearmikey.rf_reapr.domain.service.ActiveTaskMonitor
import com.fearmikey.rf_reapr.domain.util.ScanTimeEstimator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

class WorkflowViewModel(
    val mode: AppMode,
    private val discoveryRepository: NetworkDiscoveryRepository,
    private val portScannerRepository: PortScannerRepository,
    private val serviceDiscoveryRepository: ServiceDiscoveryRepository,
    private val dnsAuditorRepository: DnsAuditorRepository,
    private val upnpScannerRepository: UpnpScannerRepository,
    private val subdomainFinderRepository: SubdomainFinderRepository,
    private val websiteInspectorRepository: WebsiteInspectorRepository,
    private val cloudAssetScannerRepository: CloudAssetScannerRepository,
    private val tlsCipherScannerRepository: TlsCipherScannerRepository,
    private val shodanRepository: ShodanRepository,
    private val hibpRepository: HibpRepository,
    private val logRepository: LogRepository,
    private val wifiRepository: WifiFingerprintRepository,
    private val bleRepository: BleScannerRepository,
    private val sdrRepository: SdrRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val _currentStepIndex = MutableStateFlow(0)
    val currentStepIndex: StateFlow<Int> = _currentStepIndex.asStateFlow()

    private val _stepStatuses = MutableStateFlow<Map<String, StepStatus>>(
        mode.steps.associateBy({ it.id }, { StepStatus.PENDING })
    )
    val stepStatuses: StateFlow<Map<String, StepStatus>> = _stepStatuses.asStateFlow()

    private val _selectedStepIds = MutableStateFlow<Set<String>>(
        mode.steps.map { it.id }.toSet()
    )
    val selectedStepIds: StateFlow<Set<String>> = _selectedStepIds.asStateFlow()

    private val _workflowState = MutableStateFlow(WorkflowState.SELECTION)
    val workflowState: StateFlow<WorkflowState> = _workflowState.asStateFlow()

    private val _executionLogs = MutableStateFlow<List<String>>(emptyList())
    val executionLogs: StateFlow<List<String>> = _executionLogs.asStateFlow()

    private val _overallProgress = MutableStateFlow(0f)
    val overallProgress: StateFlow<Float> = _overallProgress.asStateFlow()

    private val _targetDomain = MutableStateFlow("")
    val targetDomain: StateFlow<String> = _targetDomain.asStateFlow()

    private var workflowJob: Job? = null
    private var discoveredNodes: List<NetworkNode> = emptyList()
    
    private val _workflowStartTime = MutableStateFlow<Long?>(null)
    val workflowStartTime: StateFlow<Long?> = _workflowStartTime.asStateFlow()

    // --- Time estimation ---
    private val _hostCount = MutableStateFlow<Int?>(null)
    private val _deviceCount = MutableStateFlow<Int?>(null)

    /** Rough low/high second estimate per step id, refined once real data is known. */
    val stepEstimates: StateFlow<Map<String, ScanTimeEstimator.EstimateRange>> =
        combine(_hostCount, _deviceCount) { hosts, devices ->
            mode.steps.associate { it.id to ScanTimeEstimator.estimateFor(it.id, hosts, devices) }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            mode.steps.associate { it.id to ScanTimeEstimator.estimateFor(it.id) }
        )

    init {
        // If this workflow includes a local network scan, fetch the subnet size up front
        // so we can show a realistic time estimate before the user starts the scan.
        if (mode.steps.any { it.id == "net_disc" }) {
            viewModelScope.launch {
                try {
                    val info = discoveryRepository.getLocalNetworkInfo()
                    val hostBits = 32 - info.prefixLength
                    if (hostBits in 1..16) {
                        _hostCount.value = (1 shl hostBits) - 2
                    }
                } catch (e: Exception) {
                    // Leave the default estimate in place if network info isn't available.
                }
            }
        }

        // Allow the "Stop All Tasks" notification action to cancel a running workflow.
        viewModelScope.launch {
            ActiveTaskMonitor.stopAllSignal.collect {
                if (_workflowState.value == WorkflowState.EXECUTING) {
                    cancelWorkflow()
                }
            }
        }
    }

    fun updateTargetDomain(domain: String) {
        _targetDomain.value = domain
    }

    fun toggleStepSelection(stepId: String) {
        val current = _selectedStepIds.value.toMutableSet()
        if (current.contains(stepId)) {
            val step = mode.steps.find { it.id == stepId }
            if (step?.isMandatory == false) {
                current.remove(stepId)
            }
        } else {
            current.add(stepId)
        }
        _selectedStepIds.value = current
    }

    fun startWorkflow() {
        _workflowState.value = WorkflowState.EXECUTING
        _workflowStartTime.value = System.currentTimeMillis()
        _executionLogs.value = listOf("Initializing workflow: ${mode.title}")
        ActiveTaskMonitor.addTask(mode.title)
        workflowJob = viewModelScope.launch {
            try {
                runAutomatedSteps()
            } finally {
                ActiveTaskMonitor.removeTask(mode.title)
            }
        }
    }

    fun cancelWorkflow() {
        workflowJob?.cancel()
        workflowJob = null
        // Defensively stop any repositories that might still be running blocking native
        // calls (e.g. Process.exec("ping")) so cancellation takes effect immediately
        // instead of racing in the background.
        discoveryRepository.stopScan()
        portScannerRepository.stopScan()
        upnpScannerRepository.stopScan()
        ActiveTaskMonitor.removeTask(mode.title)
        _executionLogs.value = _executionLogs.value + "Workflow cancelled by user."
        _workflowState.value = WorkflowState.SELECTION
    }

    private suspend fun runAutomatedSteps() {
        val selectedSteps = mode.steps.filter { _selectedStepIds.value.contains(it.id) }
        val totalSteps = selectedSteps.size

        selectedSteps.forEachIndexed { index, step ->
            _currentStepIndex.value = mode.steps.indexOf(step)
            updateStepStatus(step.id, StepStatus.IN_PROGRESS)
            log("Starting: ${step.title}")
            ActiveTaskMonitor.updateStatus("${step.title} (${index + 1}/$totalSteps)")

            try {
                executeStep(step)
                updateStepStatus(step.id, StepStatus.COMPLETED)
                log("Finished: ${step.title}")
            } catch (e: CancellationException) {
                // Let cancellation propagate so the workflow actually stops instead of
                // treating it like a normal step failure and racing through the rest.
                throw e
            } catch (e: Exception) {
                updateStepStatus(step.id, StepStatus.FAILED)
                log("Error in ${step.title}: ${e.message}")
            }
            
            _overallProgress.value = (index + 1).toFloat() / totalSteps.toFloat()
        }
        
        log("All selected tests completed.")
        _workflowState.value = WorkflowState.SUMMARY
    }

    private suspend fun executeStep(step: WorkflowStep) {
        when (step.id) {
            "net_disc" -> runNetworkDiscovery()
            "port_scan" -> runPortScans()
            "svc_disc" -> runServiceDiscovery()
            "dns_audit" -> runDnsAudit()
            "upnp_audit" -> runUpnpAudit()
            
            // External Steps
            "subdomain_finder" -> runSubdomainFinder()
            "cloud_scanner" -> runCloudScanner()
            "website_inspector" -> runWebsiteInspector()
            "tls_scanner" -> runTlsScanner()
            "shodan_search" -> runShodanSearch()
            "hibp_audit" -> runHibpAudit()
            
            // Wireless Steps
            "wifi_scan" -> runWifiScan()
            "ble_scan" -> runBleScan()
            "sdr_sweep" -> runSdrSweep()
        }
    }

    // --- Internal Steps ---

    private suspend fun runNetworkDiscovery() {
        log("Scanning local network for devices...")
        discoveryRepository.discoverDevices().collect { result ->
            when (result) {
                is NetworkScanner.ScanResult.Finished -> {
                    discoveredNodes = result.foundData
                    _deviceCount.value = discoveredNodes.size
                    log("Discovered ${discoveredNodes.size} devices.")
                }
                is NetworkScanner.ScanResult.Error -> throw Exception(result.message)
                else -> Unit
            }
        }
    }

    private suspend fun runPortScans() {
        if (discoveredNodes.isEmpty()) {
            log("No devices found in discovery. Skipping port scans.")
            return
        }
        
        log("Scanning discovered devices for common ports...")
        discoveredNodes.forEach { node ->
            log("Scanning ${node.ipAddress}...")
            portScannerRepository.setConfig(node.ipAddress, listOf(80, 443, 22, 21, 23, 445, 3389, 8080))
            portScannerRepository.startScan().collect { result ->
                if (result is NetworkScanner.ScanResult.Finished) {
                    if (result.foundData.isNotEmpty()) {
                        log("Found ${result.foundData.size} open ports on ${node.ipAddress}")
                    }
                }
            }
        }
    }

    private suspend fun runServiceDiscovery() {
        log("Searching for mDNS/Bonjour services (15s window)...")
        var lastFound: List<DiscoveredService> = emptyList()
        
        withTimeoutOrNull(15.seconds) {
            serviceDiscoveryRepository.startDiscovery().collect { services ->
                lastFound = services
            }
        }
        
        if (lastFound.isNotEmpty()) {
            log("Found ${lastFound.size} mDNS services.")
        } else {
            log("No mDNS services found within the window.")
        }
        serviceDiscoveryRepository.stopDiscovery()
    }

    private suspend fun runDnsAudit() {
        log("Auditing DNS security...")
        val result = dnsAuditorRepository.auditDns()
        if (result.isHijacked) {
            log("WARNING: DNS Hijacking detected!")
        } else {
            log("DNS resolution appears normal.")
        }
    }

    private suspend fun runUpnpAudit() {
        log("Auditing UPnP mappings...")
        upnpScannerRepository.discoverDevices().collect { result ->
            if (result is NetworkScanner.ScanResult.Finished) {
                log("Found ${result.foundData.size} UPnP devices.")
            }
        }
    }

    // --- External Steps ---

    private suspend fun runSubdomainFinder() {
        val domain = _targetDomain.value
        if (domain.isBlank()) throw Exception("Target domain is missing.")
        
        log("Finding subdomains for $domain...")
        subdomainFinderRepository.findSubdomains(domain).collect { result ->
            if (result.isFinished) {
                log("Found ${result.subdomains.size} subdomains.")
                logRepository.saveLog(
                    type = "SUBDOMAIN",
                    summary = "Subdomain Scan: $domain",
                    detailJson = com.google.gson.Gson().toJson(result.subdomains)
                )
            }
        }
    }

    private suspend fun runCloudScanner() {
        val domain = _targetDomain.value
        log("Scanning for cloud assets associated with $domain...")
        cloudAssetScannerRepository.scanAssets(domain).collect { result ->
            if (result.isFinished) {
                val public = result.discoveredAssets.count { it.isPublic }
                log("Discovered ${result.discoveredAssets.size} cloud assets ($public public).")
                logRepository.saveLog(
                    type = "CLOUD",
                    summary = "Cloud Asset Scan: $domain",
                    detailJson = com.google.gson.Gson().toJson(result.discoveredAssets)
                )
            }
        }
    }

    private suspend fun runWebsiteInspector() {
        val domain = _targetDomain.value
        log("Inspecting website and infrastructure for $domain...")
        var finalStatus: WebsiteInspectorRepository.WebsiteInspectorStatus? = null
        
        websiteInspectorRepository.inspectWebsite(domain).collect { status ->
            finalStatus = status
        }
        
        log("Inspection complete.")
        finalStatus?.let { status ->
            logRepository.saveLog(
                type = "WEBSITE",
                summary = "Website Audit: $domain",
                detailJson = com.google.gson.Gson().toJson(status)
            )
        }
    }

    private suspend fun runTlsScanner() {
        val domain = _targetDomain.value
        log("Auditing TLS ciphers for $domain...")
        tlsCipherScannerRepository.scanCiphers(domain).collect { result ->
            if (result.isFinished) {
                log("TLS audit complete. Found ${result.supportedCiphers.size} supported ciphers.")
                logRepository.saveLog(
                    type = "TLS",
                    summary = "TLS Cipher Audit: $domain",
                    detailJson = com.google.gson.Gson().toJson(result.supportedCiphers)
                )
            }
        }
    }

    private suspend fun runShodanSearch() {
        val domain = _targetDomain.value
        log("Querying Shodan for $domain...")
        // Use a broader query to find subdomains and associated assets
        shodanRepository.search("hostname:.$domain").onSuccess { response ->
            log("Shodan found ${response.total} results for this domain.")
            logRepository.saveLog(
                type = "SHODAN",
                summary = "Shodan Recon: $domain",
                detailJson = com.google.gson.Gson().toJson(response)
            )
        }.onFailure { e ->
            log("Shodan query failed: ${e.message}")
        }
    }

    private suspend fun runHibpAudit() {
        val domain = _targetDomain.value
        log("Checking HIBP for breaches related to $domain...")
        hibpRepository.getBreachedAccounts(domain).onSuccess { breaches ->
            if (breaches.isNotEmpty()) {
                log("WARNING: Found ${breaches.size} potential data breaches related to this domain.")
            } else {
                log("No major breaches found for this domain on HIBP.")
            }
            logRepository.saveLog(
                type = "HIBP",
                summary = "HIBP Domain Check: $domain",
                detailJson = com.google.gson.Gson().toJson(breaches)
            )
        }.onFailure { e ->
            log("HIBP check failed: ${e.message}")
        }
    }

    // --- Wireless Steps ---

    private suspend fun runWifiScan() {
        log("Analyzing WiFi spectrum (20s scan)...")
        var results: List<com.fearmikey.rf_reapr.domain.model.WifiAccessPoint> = emptyList()
        withTimeoutOrNull(20.seconds) {
            wifiRepository.startWifiScan().collect { aps ->
                results = aps
            }
        }
        wifiRepository.stopWifiScan()
        
        if (results.isNotEmpty()) {
            log("Discovered ${results.size} access points.")
            logRepository.saveLog(
                type = "WIFI",
                summary = "WiFi Audit: ${results.size} APs",
                detailJson = com.google.gson.Gson().toJson(results)
            )
        } else {
            log("No WiFi access points found.")
        }
    }

    private suspend fun runBleScan() {
        log("Scanning Bluetooth proximity (20s scan)...")
        var results: List<com.fearmikey.rf_reapr.domain.model.BleDevice> = emptyList()
        withTimeoutOrNull(20.seconds) {
            bleRepository.startScan(false).collect { result ->
                if (result is NetworkScanner.ScanResult.Finished) {
                    results = result.foundData
                } else if (result is NetworkScanner.ScanResult.Progress) {
                    results = result.foundData
                }
            }
        }
        
        if (results.isNotEmpty()) {
            log("Discovered ${results.size} Bluetooth devices.")
            logRepository.saveLog(
                type = "BLE",
                summary = "Bluetooth Audit: ${results.size} Devices",
                detailJson = com.google.gson.Gson().toJson(results)
            )
        } else {
            log("No Bluetooth devices found.")
        }
    }

    private suspend fun runSdrSweep() {
        val sdrIp = settingsRepository.sdrIp.first()
        val sdrPort = settingsRepository.sdrPort.first()
        
        log("Attempting SDR connection to $sdrIp:$sdrPort...")
        
        // This is a simplified sweep for the workflow
        try {
            sdrRepository.connect(sdrIp, sdrPort)
            log("Connected to SDR. Performing 15s frequency sweep...")
            
            val frequencies = listOf(433920000L, 868000000L, 2400000000L)
            for (freq in frequencies) {
                log("Sweeping ${freq / 1000000.0} MHz...")
                sdrRepository.setFrequency(freq)
                kotlinx.coroutines.delay(5.seconds) // Watch each band for 5s
            }
            
            sdrRepository.disconnect()
            log("SDR sweep complete.")
            logRepository.saveLog(
                type = "SDR",
                summary = "SDR Frequency Sweep Complete",
                detailJson = "{ \"status\": \"success\", \"bands_swept\": 3 }"
            )
        } catch (e: Exception) {
            log("SDR Error: ${e.message}. Ensure hardware is connected and rtl_tcp is running.")
        }
    }

    private fun log(message: String) {
        _executionLogs.value = _executionLogs.value + message
    }

    private fun updateStepStatus(stepId: String, status: StepStatus) {
        val currentMap = _stepStatuses.value.toMutableMap()
        currentMap[stepId] = status
        _stepStatuses.value = currentMap
    }

    enum class StepStatus {
        PENDING, IN_PROGRESS, COMPLETED, SKIPPED, FAILED
    }

    enum class WorkflowState {
        SELECTION, EXECUTING, SUMMARY
    }
}
