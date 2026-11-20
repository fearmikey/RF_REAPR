package com.fearmikey.rf_reapr.ui.snmp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.model.SnmpResult
import com.fearmikey.rf_reapr.domain.model.SnmpVarBind
import com.fearmikey.rf_reapr.domain.repository.FindingRepository
import com.fearmikey.rf_reapr.domain.repository.LogRepository
import com.fearmikey.rf_reapr.domain.repository.SnmpRepository
import com.fearmikey.rf_reapr.domain.service.ActiveTaskMonitor
import com.fearmikey.rf_reapr.domain.service.FindingAdapters
import com.fearmikey.rf_reapr.domain.service.HostValidator
import com.fearmikey.rf_reapr.domain.service.SnmpFinding
import com.fearmikey.rf_reapr.domain.service.SnmpTools
import com.google.gson.Gson
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SnmpViewModel(
    private val snmpRepository: SnmpRepository,
    private val logRepository: LogRepository,
    private val findingRepository: FindingRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(SnmpUiState())
    val uiState: StateFlow<SnmpUiState> = _uiState.asStateFlow()

    private val gson = Gson()
    private var job: Job? = null

    init {
        viewModelScope.launch { ActiveTaskMonitor.stopAllSignal.collect { cancel() } }
    }

    fun updateTarget(target: String) = _uiState.update { it.copy(target = target, error = null) }
    fun updateCommunity(community: String) = _uiState.update { it.copy(community = community, error = null) }
    fun updateVersion(version: Int) = _uiState.update { it.copy(version = version) }
    fun updateWalkOid(oid: String) = _uiState.update { it.copy(walkOid = oid) }

    private fun validatedHost(): String? {
        val host = HostValidator.normalize(_uiState.value.target)
        if (host == null) _uiState.update { it.copy(error = "Enter a valid IP address or hostname") }
        return host
    }

    fun queryDevice() {
        val state = _uiState.value
        if (state.isBusy) return
        val host = validatedHost() ?: return
        if (state.community.isEmpty()) {
            _uiState.update { it.copy(error = "Enter a community string") }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null, result = null, findings = emptyList(), walkResults = emptyList(), target = host) }
        job = viewModelScope.launch {
            try {
                snmpRepository.queryDevice(host, state.community, state.version)
                    .onSuccess { result ->
                        val findings = SnmpTools.findings(result, _uiState.value.workingCommunities)
                        _uiState.update { it.copy(result = result, findings = findings) }
                        saveToLogs(result, findings)
                        findingRepository?.record(FindingAdapters.snmp(host, findings))
                    }
                    .onFailure { error ->
                        _uiState.update { it.copy(error = error.message ?: "Unknown error") }
                    }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /** Tries the built-in list of default communities; stops nothing on the device. */
    fun testDefaultCommunities() {
        val state = _uiState.value
        if (state.isBusy) return
        val host = validatedHost() ?: return
        val task = "SNMP community check ($host)"
        ActiveTaskMonitor.addTask(task)
        _uiState.update { it.copy(isTestingCommunities = true, communityProgress = 0, workingCommunities = emptyList(), communitiesTested = false, error = null) }
        job = viewModelScope.launch {
            try {
                val working = mutableListOf<String>()
                SnmpTools.DEFAULT_COMMUNITIES.forEachIndexed { i, c ->
                    if (snmpRepository.probeCommunity(host, c, state.version)) working += c
                    _uiState.update { it.copy(communityProgress = i + 1, workingCommunities = working.toList()) }
                }
                _uiState.update { s ->
                    s.copy(
                        communitiesTested = true,
                        community = working.firstOrNull() ?: s.community,
                        findings = s.result?.let { SnmpTools.findings(it, working) } ?: s.findings
                    )
                }
                findingRepository?.record(FindingAdapters.snmp(host, _uiState.value.findings))
                if (working.isNotEmpty()) {
                    logRepository.saveLog("SNMP", "Default SNMP communities accepted by $host: ${working.joinToString()}", gson.toJson(mapOf("host" to host, "communities" to working)))
                }
            } finally {
                ActiveTaskMonitor.removeTask(task)
                _uiState.update { it.copy(isTestingCommunities = false) }
            }
        }
    }

    fun walk(oid: String = _uiState.value.walkOid) {
        val state = _uiState.value
        if (state.isBusy) return
        val host = validatedHost() ?: return
        _uiState.update { it.copy(isWalking = true, walkOid = oid, walkError = null, walkResults = emptyList()) }
        job = viewModelScope.launch {
            try {
                snmpRepository.walk(host, state.community, state.version, oid)
                    .onSuccess { binds -> _uiState.update { it.copy(walkResults = binds) } }
                    .onFailure { e -> _uiState.update { it.copy(walkError = e.message ?: "Walk failed") } }
            } finally {
                _uiState.update { it.copy(isWalking = false) }
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    private suspend fun saveToLogs(result: SnmpResult, findings: List<SnmpFinding>) {
        val summary = "SNMP: ${result.sysName ?: result.targetIp} (${result.interfaces.size} interfaces, ${result.arpTable.size} ARP entries)"
        logRepository.saveLog("SNMP", summary, gson.toJson(mapOf("result" to result, "findings" to findings)))
    }

    fun reportText(): String {
        val s = _uiState.value
        val r = s.result ?: return ""
        return buildString {
            appendLine("RF-REAPR SNMP report: ${r.targetIp} (community \"${r.community}\", v${if (r.version == 0) "1" else "2c"})")
            appendLine("Name: ${r.sysName}\nDescription: ${r.sysDescr}\nUptime: ${r.sysUptime}\nContact: ${r.sysContact}\nLocation: ${r.sysLocation}")
            SnmpTools.vendorFromObjectId(r.sysObjectId)?.let { appendLine("Vendor: $it (${r.sysObjectId})") }
            if (s.findings.isNotEmpty()) {
                appendLine("\nFindings:")
                s.findings.forEach { appendLine("[${it.severity}] ${it.title}") }
            }
            appendLine("\nInterfaces:")
            r.interfaces.forEach {
                appendLine("  ${it.index} ${it.name} ${it.type} ${it.operStatus} ${SnmpTools.formatSpeed(it.speed)} ${it.physAddress}")
            }
            if (r.ipAddresses.isNotEmpty()) {
                appendLine("\nIP addresses:")
                r.ipAddresses.forEach { appendLine("  ${it.address}/${it.netmask} (if ${it.ifIndex})") }
            }
            if (r.arpTable.isNotEmpty()) {
                appendLine("\nARP table:")
                r.arpTable.forEach { appendLine("  ${it.ipAddress}  ${it.macAddress}") }
            }
        }
    }

    override fun onCleared() {
        cancel()
        super.onCleared()
    }
}

data class SnmpUiState(
    val target: String = "",
    val community: String = "public",
    val version: Int = 1, // 0 for V1, 1 for V2c
    val isLoading: Boolean = false,
    val result: SnmpResult? = null,
    val findings: List<SnmpFinding> = emptyList(),
    val error: String? = null,
    val isTestingCommunities: Boolean = false,
    val communityProgress: Int = 0,
    val communitiesTested: Boolean = false,
    val workingCommunities: List<String> = emptyList(),
    val walkOid: String = "1.3.6.1.2.1.1",
    val isWalking: Boolean = false,
    val walkResults: List<SnmpVarBind> = emptyList(),
    val walkError: String? = null
) {
    val isBusy: Boolean get() = isLoading || isTestingCommunities || isWalking
}
