package com.fearmikey.rf_reapr.ui.arp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.repository.ArpDetectorRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class ArpDetectorViewModel(
    private val repository: ArpDetectorRepository
) : ViewModel() {

    val gatewayInfo = repository.gatewayInfo
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val alerts = repository.alerts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun startMonitoring() {
        repository.startMonitoring()
    }

    fun stopMonitoring() {
        repository.stopMonitoring()
    }

    fun clearAlerts() {
        repository.clearAlerts()
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopMonitoring()
    }
}
