package com.fearmikey.rf_reapr.ui.mdns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.repository.LogRepository
import com.fearmikey.rf_reapr.domain.repository.SettingsRepository
import com.fearmikey.rf_reapr.domain.repository.UpnpAuditResult
import com.fearmikey.rf_reapr.domain.repository.UpnpScannerRepository
import com.fearmikey.rf_reapr.domain.service.ActiveTaskMonitor
import com.google.gson.Gson
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UpnpScannerViewModel(
    private val repository: UpnpScannerRepository,
    private val settingsRepository: SettingsRepository,
    private val logRepository: LogRepository? = null
) : ViewModel() {

    val isPassiveMode: StateFlow<Boolean> = settingsRepository.isPassiveMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _result = MutableStateFlow<UpnpAuditResult?>(null)
    val result: StateFlow<UpnpAuditResult?> = _result.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var job: Job? = null
    private val gson = Gson()

    init {
        viewModelScope.launch { ActiveTaskMonitor.stopAllSignal.collect { stopScan() } }
    }

    fun startScan() {
        if (isPassiveMode.value || _isScanning.value) return
        job?.cancel()
        _isScanning.value = true
        val task = "UPnP Audit"
        ActiveTaskMonitor.addTask(task)
        job = viewModelScope.launch {
            try {
                repository.audit().collect { snapshot ->
                    _result.value = snapshot
                    if (snapshot.isComplete && snapshot.error == null) log(snapshot)
                }
            } finally {
                ActiveTaskMonitor.removeTask(task)
                _isScanning.value = false
            }
        }
    }

    private suspend fun log(result: UpnpAuditResult) {
        val logger = logRepository ?: return
        val mappings = result.devices.sumOf { it.portMappings.size }
        logger.saveLog(
            type = "TOPOLOGY",
            summary = "UPnP audit: ${result.devices.size} devices, $mappings port mappings, ${result.findings.size} findings",
            detailJson = gson.toJson(
                mapOf(
                    "tool" to "UPnP/NAT-PMP Auditor",
                    "gateway" to result.gatewayIp,
                    "natPmp" to result.natPmp,
                    "findings" to result.findings,
                    "devices" to result.devices
                )
            )
        )
    }

    fun stopScan() {
        job?.cancel()
        job = null
    }

    fun reportText(): String {
        val r = _result.value ?: return ""
        return buildString {
            appendLine("RF-REAPR UPnP / NAT-PMP audit")
            r.gatewayIp?.let { appendLine("Gateway: $it") }
            appendLine("NAT-PMP: ${if (r.natPmp?.resultCode == 0) "enabled (external ${r.natPmp.externalIp})" else "not detected"}")
            appendLine()
            if (r.findings.isNotEmpty()) {
                appendLine("Findings:")
                r.findings.forEach { appendLine("[${it.severity}] ${it.title}\n    ${it.detail}") }
                appendLine()
            }
            appendLine("Devices:")
            r.devices.forEach { d ->
                appendLine("- ${d.friendlyName} (${d.ipAddress}) ${d.manufacturer} ${d.modelName}")
                d.server?.let { appendLine("    Server: $it") }
                d.externalIp?.let { appendLine("    External IP: $it") }
                d.portMappings.forEach { m ->
                    appendLine("    ${m.protocol} ${m.externalPort} -> ${m.internalClient}:${m.internalPort} ${m.description}")
                }
            }
        }
    }

    override fun onCleared() {
        stopScan()
        super.onCleared()
    }
}
