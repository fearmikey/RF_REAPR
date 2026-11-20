package com.fearmikey.rf_reapr.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.model.OpenPort
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.domain.repository.FindingRepository
import com.fearmikey.rf_reapr.domain.repository.LogRepository
import com.fearmikey.rf_reapr.domain.repository.PortScanOptions
import com.fearmikey.rf_reapr.domain.repository.PortScannerRepository
import com.fearmikey.rf_reapr.domain.repository.ScanSessionRepository
import com.fearmikey.rf_reapr.domain.repository.SettingsRepository
import com.fearmikey.rf_reapr.domain.repository.VulnerabilityRepository
import com.fearmikey.rf_reapr.domain.scanner.NetworkScanner
import com.fearmikey.rf_reapr.domain.service.ActiveTaskMonitor
import com.fearmikey.rf_reapr.domain.service.FindingAdapters
import com.fearmikey.rf_reapr.domain.service.HostValidator
import com.fearmikey.rf_reapr.domain.service.PortCatalog
import com.google.gson.Gson
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

enum class PortPreset(val label: String) {
    COMMON("Common (~170)"),
    TOP_1024("1–1024"),
    ALL("All 65535"),
    CUSTOM("Custom")
}

enum class ScanSpeed(val label: String, val timeoutMs: Int, val concurrency: Int) {
    GENTLE("Gentle", 1500, 16),
    NORMAL("Normal", 600, 64),
    FAST("Fast", 300, 160)
}

enum class ScanPhase { IDLE, SCANNING, AUDITING, DONE, STOPPED, ERROR }

data class PortScanUiState(
    val target: String = "",
    val preset: PortPreset = PortPreset.COMMON,
    val customSpec: String = "22,80,443,8000-8100",
    val speed: ScanSpeed = ScanSpeed.NORMAL,
    val phase: ScanPhase = ScanPhase.IDLE,
    val progress: Float = 0f,
    val portsTotal: Int = 0,
    val eta: String? = null,
    val elapsedMs: Long = 0,
    val scannedTarget: String = "",
    val error: String? = null
) {
    val isBusy: Boolean get() = phase == ScanPhase.SCANNING || phase == ScanPhase.AUDITING
}

class PortScannerViewModel(
    private val portScannerRepository: PortScannerRepository,
    private val vulnerabilityRepository: VulnerabilityRepository,
    private val scanSessionRepository: ScanSessionRepository,
    private val logRepository: LogRepository,
    private val settingsRepository: SettingsRepository,
    private val findingRepository: FindingRepository? = null
) : ViewModel() {

    val isApiKeySet: StateFlow<Boolean> = settingsRepository.vulnerabilityApiKey
        .map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isPassiveMode: StateFlow<Boolean> = settingsRepository.isPassiveMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _state = MutableStateFlow(PortScanUiState())
    val state: StateFlow<PortScanUiState> = _state.asStateFlow()

    private val _foundPorts = MutableStateFlow<List<OpenPort>>(emptyList())
    val foundPorts: StateFlow<List<OpenPort>> = _foundPorts.asStateFlow()

    private var scanJob: Job? = null
    private var initialTargetApplied = false
    private val gson = Gson()

    init {
        viewModelScope.launch {
            ActiveTaskMonitor.stopAllSignal.collect { stopScan() }
        }
    }

    companion object {
        val COMMON_PORTS: List<Int> get() = PortCatalog.COMMON_PORTS
    }

    fun setInitialTarget(ip: String?) {
        if (initialTargetApplied) return
        initialTargetApplied = true
        val value = ip?.takeUnless { it.isBlank() || it.startsWith("{") } ?: return
        _state.update { it.copy(target = value) }
    }

    fun updateTarget(value: String) = _state.update { it.copy(target = value, error = null) }
    fun updatePreset(preset: PortPreset) = _state.update { it.copy(preset = preset, error = null) }
    fun updateCustomSpec(spec: String) = _state.update { it.copy(customSpec = spec, error = null) }
    fun updateSpeed(speed: ScanSpeed) = _state.update { it.copy(speed = speed) }

    /** Validation error for the current target/ports, or null if a scan can start. */
    fun validate(): String? {
        val s = _state.value
        if (HostValidator.normalize(s.target) == null) return "Enter a valid IP address or hostname"
        return when (val ports = resolvePorts(s)) {
            is PortCatalog.ParseResult.Invalid -> ports.message
            is PortCatalog.ParseResult.Ok -> null
        }
    }

    private fun resolvePorts(s: PortScanUiState): PortCatalog.ParseResult = when (s.preset) {
        PortPreset.COMMON -> PortCatalog.ParseResult.Ok(PortCatalog.COMMON_PORTS)
        PortPreset.TOP_1024 -> PortCatalog.ParseResult.Ok((1..1024).toList())
        PortPreset.ALL -> PortCatalog.ParseResult.Ok((1..65535).toList())
        PortPreset.CUSTOM -> PortCatalog.parsePortSpec(s.customSpec)
    }

    /** Port-count / time estimate shown before starting. */
    fun estimateLabel(): String {
        val s = _state.value
        val count = (resolvePorts(s) as? PortCatalog.ParseResult.Ok)?.ports?.size ?: return ""
        // Worst case: every port filtered and waits the full timeout.
        val worstSec = (count.toLong() * s.speed.timeoutMs) / s.speed.concurrency / 1000
        return "$count ports · up to ${formatDuration(worstSec * 1000)}"
    }

    fun startScan() {
        if (isPassiveMode.value) return
        val s = _state.value
        val host = HostValidator.normalize(s.target)
        val ports = (resolvePorts(s) as? PortCatalog.ParseResult.Ok)?.ports
        if (host == null || ports == null) {
            _state.update { it.copy(error = validate()) }
            return
        }
        scanJob?.cancel()
        _foundPorts.value = emptyList()
        _state.update {
            it.copy(
                target = host, phase = ScanPhase.SCANNING, progress = 0f, portsTotal = ports.size,
                eta = null, elapsedMs = 0, scannedTarget = host, error = null
            )
        }
        val options = PortScanOptions(connectTimeoutMs = s.speed.timeoutMs, concurrency = s.speed.concurrency)
        val taskName = "Port Scan ($host)"
        ActiveTaskMonitor.addTask(taskName)
        val startTime = System.currentTimeMillis()

        scanJob = viewModelScope.launch {
            try {
                portScannerRepository.scan(host, ports, options).collect { result ->
                    val elapsed = System.currentTimeMillis() - startTime
                    when (result) {
                        is NetworkScanner.ScanResult.Progress -> {
                            _foundPorts.value = result.foundData
                            _state.update {
                                it.copy(progress = result.progress, elapsedMs = elapsed, eta = eta(elapsed, result.progress))
                            }
                            ActiveTaskMonitor.updateStatus("Port scan $host: ${(result.progress * 100).toInt()}% · ${result.foundData.size} open")
                        }
                        is NetworkScanner.ScanResult.Finished -> {
                            _foundPorts.value = result.foundData
                            _state.update { it.copy(progress = 1f, elapsedMs = elapsed, eta = null, phase = ScanPhase.AUDITING) }
                            performVulnerabilityAudit(host, result.foundData, ports.size, elapsed)
                            _state.update { it.copy(phase = ScanPhase.DONE) }
                        }
                        is NetworkScanner.ScanResult.Error -> {
                            _state.update { it.copy(phase = ScanPhase.ERROR, error = result.message, eta = null) }
                        }
                        else -> Unit
                    }
                }
            } finally {
                ActiveTaskMonitor.removeTask(taskName)
                _state.update { if (it.isBusy) it.copy(phase = ScanPhase.STOPPED, eta = null) else it }
            }
        }
    }

    private fun eta(elapsed: Long, progress: Float): String? {
        if (progress <= 0.02f || elapsed < 1500) return null
        val remaining = (elapsed / progress).toLong() - elapsed
        return formatDuration(remaining)
    }

    private suspend fun performVulnerabilityAudit(host: String, ports: List<OpenPort>, portsScanned: Int, elapsedMs: Long) {
        val auditedPorts = ports.map { port ->
            val vulns = runCatching { vulnerabilityRepository.lookupVulnerabilities(port.serviceName, port.banner) }
                .getOrDefault(emptyList())
            port.copy(vulnerabilities = vulns)
        }
        _foundPorts.value = auditedPorts

        logRepository.saveLog(
            type = "PORT",
            summary = "Scanned $host: ${auditedPorts.size} open of $portsScanned ports",
            detailJson = gson.toJson(
                mapOf(
                    "ip" to host,
                    "portsScanned" to portsScanned,
                    "durationMs" to elapsedMs,
                    "ports" to auditedPorts
                )
            )
        )

        findingRepository?.record(FindingAdapters.openPorts(host, auditedPorts))

        scanSessionRepository.getNodeByIp(host)?.let { existingNode ->
            scanSessionRepository.updateNodeDetails(
                existingNode.copy(openPorts = auditedPorts, riskLevel = calculateAggregatedRisk(auditedPorts))
            )
        }
    }

    private fun calculateAggregatedRisk(ports: List<OpenPort>): RiskLevel {
        val vulnRisk = ports.flatMap { it.vulnerabilities }.maxOfOrNull { it.severity.ordinal } ?: -1
        val exposure = PortCatalog.exposureRisk(ports.map { it.port })?.ordinal ?: -1
        val combined = maxOf(vulnRisk, exposure)
        return if (combined < 0) RiskLevel.LOW else RiskLevel.entries[combined]
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
    }

    fun clearResults() {
        stopScan()
        _foundPorts.value = emptyList()
        _state.update { it.copy(phase = ScanPhase.IDLE, progress = 0f, eta = null, error = null, scannedTarget = "") }
    }

    /** Plain-text report for the clipboard / share sheet. */
    fun reportText(): String {
        val s = _state.value
        val ports = _foundPorts.value
        return buildString {
            appendLine("RF-REAPR port scan: ${s.scannedTarget}")
            appendLine("${ports.size} open of ${s.portsTotal} ports scanned in ${formatDuration(s.elapsedMs)}")
            appendLine()
            ports.forEach { p ->
                append("${p.port}/tcp  ${p.serviceName}")
                p.banner?.let { append("  [$it]") }
                appendLine()
                PortCatalog.info(p.port)?.exposureNote?.let { appendLine("    ! $it") }
                p.vulnerabilities.forEach { v ->
                    appendLine("    ${v.cveId} (${v.severity}${if (v.isConfirmed) "" else ", potential"})")
                }
            }
        }
    }

    override fun onCleared() {
        stopScan()
        super.onCleared()
    }
}

internal fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val sec = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.US, "%02d:%02d", m, sec)
}
