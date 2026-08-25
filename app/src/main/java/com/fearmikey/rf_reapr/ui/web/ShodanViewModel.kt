package com.fearmikey.rf_reapr.ui.web

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.model.InternetDbResponse
import com.fearmikey.rf_reapr.domain.model.ShodanHostReport
import com.fearmikey.rf_reapr.domain.model.ShodanSearchResponse
import com.fearmikey.rf_reapr.domain.repository.InternetDbRepository
import com.fearmikey.rf_reapr.domain.repository.SettingsRepository
import com.fearmikey.rf_reapr.domain.repository.ShodanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShodanViewModel(
    private val repository: ShodanRepository,
    private val internetDbRepository: InternetDbRepository,
    private val settingsRepository: SettingsRepository,
    private val logRepository: com.fearmikey.rf_reapr.domain.repository.LogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ShodanUiState>(ShodanUiState.Idle)
    val uiState: StateFlow<ShodanUiState> = _uiState.asStateFlow()

    val isApiKeyMissing: StateFlow<Boolean> = settingsRepository.shodanApiKey
        .map { it.isBlank() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    fun getHostInfo(input: String) {
        if (input.isBlank()) return

        viewModelScope.launch {
            val apiKey = settingsRepository.shodanApiKey.first()
            if (apiKey.isBlank()) {
                _uiState.value = ShodanUiState.Error("Please set your Shodan API Key in Settings first.")
                return@launch
            }

            _uiState.value = ShodanUiState.Loading
            
            val ip = resolveToIp(input)
            if (ip == null) {
                _uiState.value = ShodanUiState.Error("Could not resolve host: $input")
                return@launch
            }
            
            val result = repository.getHostInfo(ip)
            
            if (result.isSuccess) {
                val report = result.getOrNull()
                if (report != null) {
                    _uiState.value = ShodanUiState.HostSuccess(report)
                    // Log manual check
                    logRepository.saveLog(
                        type = "SHODAN",
                        summary = "Shodan Host Recon: $ip",
                        detailJson = com.google.gson.Gson().toJson(report)
                    )
                } else {
                     _uiState.value = ShodanUiState.Error("Received empty response")
                }
            } else {
                _uiState.value = ShodanUiState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }

    fun search(query: String, page: Int = 1) {
        if (query.isBlank()) return

        viewModelScope.launch {
            val apiKey = settingsRepository.shodanApiKey.first()
            if (apiKey.isBlank()) {
                _uiState.value = ShodanUiState.Error("Please set your Shodan API Key in Settings first.")
                return@launch
            }

            _uiState.value = ShodanUiState.Loading
            
            val result = repository.search(query, page)
            
            if (result.isSuccess) {
                val response = result.getOrNull()
                if (response != null) {
                    _uiState.value = ShodanUiState.SearchSuccess(response)
                    // Log manual check
                    logRepository.saveLog(
                        type = "SHODAN",
                        summary = "Shodan Search: $query",
                        detailJson = com.google.gson.Gson().toJson(response)
                    )
                } else {
                     _uiState.value = ShodanUiState.Error("Received empty response")
                }
            } else {
                _uiState.value = ShodanUiState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }

    fun getInternetDbInfo(input: String) {
        if (input.isBlank()) return

        viewModelScope.launch {
            _uiState.value = ShodanUiState.Loading
            
            val ip = resolveToIp(input)
            if (ip == null) {
                _uiState.value = ShodanUiState.Error("Could not resolve host: $input")
                return@launch
            }

            val result = internetDbRepository.getIpInfo(ip)
            
            if (result.isSuccess) {
                val response = result.getOrNull()
                if (response != null) {
                    _uiState.value = ShodanUiState.InternetDbSuccess(response)
                    // Log manual check (Quick Recon)
                    logRepository.saveLog(
                        type = "SHODAN",
                        summary = "InternetDB Quick Recon: $ip",
                        detailJson = com.google.gson.Gson().toJson(
                            mapOf(
                                "type" to "InternetDB",
                                "ip_str" to ip,
                                "org" to "N/A",
                                "ports" to response.ports,
                                "hostnames" to response.hostnames,
                                "tags" to response.tags,
                                "vulns" to response.vulns
                            )
                        )
                    )
                } else {
                    _uiState.value = ShodanUiState.Error("Received empty response from InternetDB")
                }
            } else {
                _uiState.value = ShodanUiState.Error(result.exceptionOrNull()?.message ?: "InternetDB lookup failed")
            }
        }
    }

    private suspend fun resolveToIp(input: String): String? = withContext(Dispatchers.IO) {
        try {
            java.net.InetAddress.getByName(input).hostAddress
        } catch (e: Exception) {
            null
        }
    }

    fun reset() {
        _uiState.value = ShodanUiState.Idle
    }
}

sealed class ShodanUiState {
    data object Idle : ShodanUiState()
    data object Loading : ShodanUiState()
    data class HostSuccess(val report: ShodanHostReport) : ShodanUiState()
    data class SearchSuccess(val response: ShodanSearchResponse) : ShodanUiState()
    data class InternetDbSuccess(val data: InternetDbResponse) : ShodanUiState()
    data class Error(val message: String) : ShodanUiState()
}
