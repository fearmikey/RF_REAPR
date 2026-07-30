package com.fearmikey.rf_reapr.ui.dns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.repository.DnsAuditResult
import com.fearmikey.rf_reapr.domain.repository.DnsAuditorRepository
import com.fearmikey.rf_reapr.domain.repository.LogRepository
import com.fearmikey.rf_reapr.domain.repository.SettingsRepository
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

    fun runAudit() {
        if (isPassiveMode.value) return
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.auditDns()
            _auditResult.value = result
            _isLoading.value = false
            
            if (result.isHijacked) {
                logRepository.saveLog("DNS", "Potential DNS Hijack Detected!", "Domain: ${result.testDomain}, System IP: ${result.systemIp}, Hostname: ${result.resolvedHostname}, Trusted IPs: ${result.trustedIps.joinToString()}, Private DNS: ${result.privateDnsMode ?: "unknown"}")
            } else if (result.isInconclusive) {
                logRepository.saveLog("DNS", "Audit Inconclusive", "Domain: ${result.testDomain}, System Error: ${result.systemError}, Trusted Error: ${result.trustedError}")
            } else if (result.isLocalRedirection) {
                val reason = if (result.isPrivateIp) "Private IP" else "Matches DNS Server"
                logRepository.saveLog("DNS", "Local DNS Redirection ($reason)", "Domain: ${result.testDomain}, Resolved to: ${result.systemIp}, Private DNS: ${result.privateDnsMode ?: "unknown"}")
            }
        }
    }
}
