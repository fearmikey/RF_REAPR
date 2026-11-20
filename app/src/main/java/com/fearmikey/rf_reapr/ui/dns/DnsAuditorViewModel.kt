package com.fearmikey.rf_reapr.ui.dns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.repository.DnsAuditResult
import com.fearmikey.rf_reapr.domain.repository.DnsAuditorRepository
import com.fearmikey.rf_reapr.domain.repository.LogRepository
import com.fearmikey.rf_reapr.domain.repository.SettingsRepository
import com.fearmikey.rf_reapr.domain.service.CheckStatus
import com.google.gson.Gson
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DnsAuditorViewModel(
    private val repository: DnsAuditorRepository,
    private val logRepository: LogRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val isPassiveMode: StateFlow<Boolean> = settingsRepository.isPassiveMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _auditResult = MutableStateFlow<DnsAuditResult?>(null)
    val auditResult: StateFlow<DnsAuditResult?> = _auditResult.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var job: Job? = null
    private val gson = Gson()

    fun runAudit() {
        if (isPassiveMode.value || _isLoading.value) return
        job = viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = repository.auditDns()
                _auditResult.value = result
                val problems = result.checks.filter { it.status == CheckStatus.FAIL || it.status == CheckStatus.WARN }
                logRepository.saveLog(
                    type = "DNS",
                    summary = "DNS audit: ${result.overall.name}" +
                        if (problems.isNotEmpty()) " (${problems.joinToString { it.title }})" else "",
                    detailJson = gson.toJson(mapOf("checks" to result.checks, "dnsServers" to result.systemDnsServers))
                )
            } catch (e: Exception) {
                _error.value = e.message ?: "Audit failed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun reportText(): String {
        val r = _auditResult.value ?: return ""
        return buildString {
            appendLine("RF-REAPR DNS security audit: ${r.overall}")
            r.checks.forEach { c ->
                appendLine("[${c.status}] ${c.title}: ${c.summary}")
                if (c.detail.isNotBlank()) appendLine("    ${c.detail.replace("\n", "\n    ")}")
            }
        }
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }
}
