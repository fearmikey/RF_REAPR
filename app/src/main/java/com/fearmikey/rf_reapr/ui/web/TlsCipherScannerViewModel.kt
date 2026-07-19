package com.fearmikey.rf_reapr.ui.web

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.repository.LogRepository
import com.fearmikey.rf_reapr.domain.repository.TlsCipherScannerRepository
import com.fearmikey.rf_reapr.domain.repository.TlsCipherScannerRepository.TlsScanResult
import com.fearmikey.rf_reapr.domain.service.ActiveTaskMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TlsCipherScannerViewModel(
    private val repository: TlsCipherScannerRepository,
    private val logRepository: LogRepository
) : ViewModel() {

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
        if (url.isBlank()) return
        
        val taskName = "TLS Audit ($url)"
        ActiveTaskMonitor.addTask(taskName)

        viewModelScope.launch {
            try {
                _uiState.value = TlsScanResult(progress = 0f)
                repository.scanCiphers(url).collect { result ->
                    _uiState.value = result
                    
                    if (result.isFinished) {
                        logRepository.saveLog(
                            type = "WEB",
                            summary = "TLS Cipher scan for $url",
                            detailJson = "Found ${result.supportedCiphers.size} supported ciphers."
                        )
                    }
                }
            } finally {
                ActiveTaskMonitor.removeTask(taskName)
            }
        }
    }
}
