package com.fearmikey.rf_reapr.ui.traceroute

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.repository.LogRepository
import com.fearmikey.rf_reapr.domain.repository.SettingsRepository
import com.fearmikey.rf_reapr.domain.repository.TracerouteHop
import com.fearmikey.rf_reapr.domain.repository.TracerouteRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TracerouteViewModel(
    private val repository: TracerouteRepository,
    private val logRepository: LogRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val isPassiveMode: StateFlow<Boolean> = settingsRepository.isPassiveMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _hops = MutableStateFlow<List<TracerouteHop>>(emptyList())
    val hops: StateFlow<List<TracerouteHop>> = _hops.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var scanJob: Job? = null

    fun startTraceroute(host: String) {
        if (isPassiveMode.value) return
        _isScanning.value = true
        _hops.value = emptyList()
        scanJob = viewModelScope.launch {
            repository.traceroute(host).collect {
                _hops.value = it
                if (it.lastOrNull()?.isFinal == true) {
                    _isScanning.value = false
                    logRepository.saveLog("Traceroute", "Traceroute completed for $host", "Hops: ${it.size}")
                }
            }
        }
    }

    fun stopTraceroute() {
        scanJob?.cancel()
        _isScanning.value = false
    }
}
