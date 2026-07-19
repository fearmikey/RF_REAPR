package com.fearmikey.rf_reapr.ui.mdns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.repository.DiscoveredService
import com.fearmikey.rf_reapr.domain.repository.LogRepository
import com.fearmikey.rf_reapr.domain.repository.ServiceDiscoveryRepository
import com.fearmikey.rf_reapr.domain.service.ActiveTaskMonitor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ServiceDiscoveryViewModel(
    private val repository: ServiceDiscoveryRepository,
    private val logRepository: LogRepository
) : ViewModel() {

    private val _services = MutableStateFlow<List<DiscoveredService>>(emptyList())
    val services: StateFlow<List<DiscoveredService>> = _services.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            ActiveTaskMonitor.stopAllSignal.collect {
                stopDiscovery()
            }
        }
    }

    fun startDiscovery() {
        _isScanning.value = true
        val taskName = "Service Discovery"
        ActiveTaskMonitor.addTask(taskName)
        
        scanJob = viewModelScope.launch {
            try {
                repository.startDiscovery().collect {
                    _services.value = it
                }
            } finally {
                ActiveTaskMonitor.removeTask(taskName)
            }
        }
    }

    fun stopDiscovery() {
        scanJob?.cancel()
        _isScanning.value = false
    }

    override fun onCleared() {
        super.onCleared()
        stopDiscovery()
    }
}
