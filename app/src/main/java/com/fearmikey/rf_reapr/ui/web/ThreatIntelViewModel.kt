package com.fearmikey.rf_reapr.ui.web

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.repository.SettingsRepository
import com.fearmikey.rf_reapr.domain.repository.ThreatIntelRepository
import com.fearmikey.rf_reapr.domain.repository.ThreatIntelStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ThreatIntelViewModel(
    private val threatIntelRepository: ThreatIntelRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _ipAddress = MutableStateFlow("")
    val ipAddress: StateFlow<String> = _ipAddress.asStateFlow()

    private val _status = MutableStateFlow<ThreatIntelStatus>(ThreatIntelStatus.Idle)
    val status: StateFlow<ThreatIntelStatus> = _status.asStateFlow()

    val abuseIpDbApiKey: StateFlow<String> = settingsRepository.abuseIpDbApiKey
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    fun onIpAddressChanged(newIp: String) {
        _ipAddress.value = newIp
    }

    fun checkThreat() {
        val ip = _ipAddress.value.trim()
        val key = abuseIpDbApiKey.value.trim()

        _status.value = ThreatIntelStatus.Loading
        viewModelScope.launch {
            _status.value = threatIntelRepository.checkIp(ip, key)
        }
    }
}
