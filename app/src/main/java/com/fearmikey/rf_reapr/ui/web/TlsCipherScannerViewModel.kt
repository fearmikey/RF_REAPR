package com.fearmikey.rf_reapr.ui.web

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.repository.LogRepository
import com.fearmikey.rf_reapr.domain.repository.SettingsRepository
import com.fearmikey.rf_reapr.domain.repository.TlsCipherScannerRepository
import com.fearmikey.rf_reapr.domain.repository.TlsCipherScannerRepository.TlsScanResult
import com.fearmikey.rf_reapr.domain.service.ActiveTaskMonitor
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TlsCipherScannerViewModel(
    private val repository: TlsCipherScannerRepository,
    private val logRepository: LogRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val gson = Gson()

    val isPassiveMode: StateFlow<Boolean> = settingsRepository.isPassiveMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _uiState = MutableStateFlow(TlsScanResult())
    val uiState: StateFlow<TlsScanResult> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            ActiveTaskMonitor.stopAllSignal.collect {
                _uiState.value = TlsScanResult(isFinished = true)
            }
        }
    }

    fun startScan(url: String) {
        if (url.isBlank() || isPassiveMode.value) return
        
        val taskName = "TLS Audit ($url)"
        ActiveTaskMonitor.addTask(taskName)

        viewModelScope.launch {
            try {
                _uiState.value = TlsScanResult(progress = 0f)
                repository.scanCiphers(url).collect { result ->
                    _uiState.value = result
                    
                    if (result.isFinished) {
                        logRepository.saveLog(
                            type = "WEB_TLS",
                            summary = "TLS Cipher scan for $url",
                            detailJson = gson.toJson(mapOf(
                                "url" to url,
                                "ciphers" to result.supportedCiphers
                            ))
                        )
                    }
                }
            } finally {
                ActiveTaskMonitor.removeTask(taskName)
            }
        }
    }
}
