package com.fearmikey.rf_reapr.ui.web

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.model.ShodanHostReport
import com.fearmikey.rf_reapr.domain.model.ShodanSearchResponse
import com.fearmikey.rf_reapr.domain.repository.SettingsRepository
import com.fearmikey.rf_reapr.domain.repository.ShodanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShodanViewModel(
    private val repository: ShodanRepository,
    private val settingsRepository: SettingsRepository
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

    fun getHostInfo(ip: String) {
        if (ip.isBlank()) return

        viewModelScope.launch {
            val apiKey = settingsRepository.shodanApiKey.first()
            if (apiKey.isBlank()) {
                _uiState.value = ShodanUiState.Error("Please set your Shodan API Key in Settings first.")
                return@launch
            }

            _uiState.value = ShodanUiState.Loading
            
            val result = repository.getHostInfo(ip)
            
            if (result.isSuccess) {
                val report = result.getOrNull()
                if (report != null) {
                    _uiState.value = ShodanUiState.HostSuccess(report)
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
                } else {
                     _uiState.value = ShodanUiState.Error("Received empty response")
                }
            } else {
                _uiState.value = ShodanUiState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
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
    data class Error(val message: String) : ShodanUiState()
}
