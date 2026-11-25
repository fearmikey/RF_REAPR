package com.fearmikey.rf_reapr.ui.web

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.repository.FindingRepository
import com.fearmikey.rf_reapr.domain.repository.LogRepository
import com.fearmikey.rf_reapr.domain.repository.SettingsRepository
import com.fearmikey.rf_reapr.domain.repository.ThreatIntelRepository
import com.fearmikey.rf_reapr.domain.repository.ThreatIntelStatus
import com.fearmikey.rf_reapr.domain.service.PublicIpGuard
import com.fearmikey.rf_reapr.domain.service.ThreatAssessment
import com.fearmikey.rf_reapr.domain.service.ThreatIntelAnalyzer
import com.google.gson.Gson
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ThreatIntelViewModel(
    private val threatIntelRepository: ThreatIntelRepository,
    private val settingsRepository: SettingsRepository,
    private val logRepository: LogRepository? = null,
    private val findingRepository: FindingRepository? = null
) : ViewModel() {

    private val _ipAddress = MutableStateFlow("")
    val ipAddress: StateFlow<String> = _ipAddress.asStateFlow()

    private val _status = MutableStateFlow<ThreatIntelStatus>(ThreatIntelStatus.Idle)
    val status: StateFlow<ThreatIntelStatus> = _status.asStateFlow()

    private val _assessment = MutableStateFlow<ThreatAssessment?>(null)
    val assessment: StateFlow<ThreatAssessment?> = _assessment.asStateFlow()

    /** Findings recorded by the last lookup. */
    private val _recordedFindings = MutableStateFlow(0)
    val recordedFindings: StateFlow<Int> = _recordedFindings.asStateFlow()

    val abuseIpDbApiKey: StateFlow<String> = settingsRepository.abuseIpDbApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    /** Explains, before anything is sent, why the typed value can't be looked up. */
    val inputProblem: StateFlow<String?> = _ipAddress.map { input ->
        when (val verdict = PublicIpGuard.check(input)) {
            is PublicIpGuard.Verdict.NotRoutable -> "${verdict.reason}: not a public address"
            PublicIpGuard.Verdict.NotAnIp -> if (input.isBlank()) null else "Enter an IPv4 or IPv6 address"
            is PublicIpGuard.Verdict.Public -> null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private var job: Job? = null

    fun onIpAddressChanged(newIp: String) {
        _ipAddress.value = newIp
    }

    fun checkThreat() {
        val ip = _ipAddress.value.trim()
        if (ip.isEmpty() || _status.value is ThreatIntelStatus.Loading) return
        _status.value = ThreatIntelStatus.Loading
        _assessment.value = null
        _recordedFindings.value = 0
        job?.cancel()
        job = viewModelScope.launch {
            val result = threatIntelRepository.checkIp(ip, abuseIpDbApiKey.value)
            if (result is ThreatIntelStatus.Success) {
                val data = result.result
                _assessment.value = ThreatIntelAnalyzer.assess(data)
                val drafts = ThreatIntelAnalyzer.toDrafts(data)
                if (drafts.isNotEmpty()) {
                    findingRepository?.record(drafts)
                    _recordedFindings.value = drafts.size
                }
                logRepository?.saveLog(
                    type = "THREAT_INTEL",
                    summary = "AbuseIPDB: ${data.ipAddress} scored ${data.abuseConfidenceScore}%",
                    detailJson = Gson().toJson(
                        mapOf(
                            "ipAddress" to data.ipAddress,
                            "abuseConfidenceScore" to data.abuseConfidenceScore,
                            "totalReports" to data.totalReports,
                            "numDistinctUsers" to data.numDistinctUsers,
                            "countryCode" to data.countryCode,
                            "isp" to data.isp,
                            "usageType" to data.usageType,
                            "isTor" to data.isTor,
                            "isWhitelisted" to data.isWhitelisted,
                            "lastReportedAt" to data.lastReportedAt
                        )
                    )
                )
            }
            _status.value = result
        }
    }
}
