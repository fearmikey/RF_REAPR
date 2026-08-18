package com.fearmikey.rf_reapr.ui.snmp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.model.SnmpResult
import com.fearmikey.rf_reapr.domain.repository.LogRepository
import com.fearmikey.rf_reapr.domain.repository.SnmpRepository
import com.fearmikey.rf_reapr.ui.settings.SettingsViewModel
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SnmpViewModel(
    private val snmpRepository: SnmpRepository,
    private val logRepository: LogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SnmpUiState())
    val uiState: StateFlow<SnmpUiState> = _uiState.asStateFlow()

    private val gson = Gson()

    fun updateTarget(target: String) {
        _uiState.update { it.copy(target = target) }
    }

    fun updateCommunity(community: String) {
        _uiState.update { it.copy(community = community) }
    }

    fun updateVersion(version: Int) {
        _uiState.update { it.copy(version = version) }
    }

    fun queryDevice() {
        val state = _uiState.value
        if (state.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null, result = null) }

        viewModelScope.launch {
            snmpRepository.queryDevice(state.target, state.community, state.version)
                .onSuccess { result ->
                    _uiState.update { it.copy(isLoading = false, result = result) }
                    saveToLogs(result)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message ?: "Unknown error") }
                }
        }
    }

    private suspend fun saveToLogs(result: SnmpResult) {
        val summary = "SNMP Query: ${result.sysName ?: result.targetIp} (${result.interfaces.size} interfaces)"
        val detailJson = gson.toJson(result)
        logRepository.saveLog("SNMP", summary, detailJson)
    }
}

data class SnmpUiState(
    val target: String = "",
    val community: String = "public",
    val version: Int = 1, // 0 for V1, 1 for V2c
    val isLoading: Boolean = false,
    val result: SnmpResult? = null,
    val error: String? = null
)
