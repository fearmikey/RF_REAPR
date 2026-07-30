package com.fearmikey.rf_reapr.ui.web

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.repository.CloudAssetScannerRepository
import com.fearmikey.rf_reapr.domain.repository.CloudAssetScannerRepository.CloudScanResult
import com.fearmikey.rf_reapr.domain.repository.LogRepository
import com.fearmikey.rf_reapr.domain.repository.SettingsRepository
import com.fearmikey.rf_reapr.domain.service.ActiveTaskMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CloudAssetScannerViewModel(
    private val repository: CloudAssetScannerRepository,
    private val logRepository: LogRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val isPassiveMode: StateFlow<Boolean> = settingsRepository.isPassiveMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _uiState = MutableStateFlow(CloudScanResult())
    val uiState: StateFlow<CloudScanResult> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            ActiveTaskMonitor.stopAllSignal.collect {
                _uiState.value = CloudScanResult(isFinished = true)
            }
        }
    }

    fun scanAssets(domain: String) {
        if (domain.isBlank() || isPassiveMode.value) return
        
        val taskName = "Cloud Discovery ($domain)"
        ActiveTaskMonitor.addTask(taskName)

        viewModelScope.launch {
            try {
                _uiState.value = CloudScanResult(progress = 0f)
                repository.scanAssets(domain).collect { result ->
                    _uiState.value = result
                    
                    if (result.isFinished) {
                        logRepository.saveLog(
                            type = "WEB",
                            summary = "Cloud asset scan for $domain",
                            detailJson = "Found ${result.discoveredAssets.size} potential assets."
                        )
                    }
                }
            } finally {
                ActiveTaskMonitor.removeTask(taskName)
            }
        }
    }
}
