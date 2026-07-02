package com.example.rf_reapr.ui.dhcp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rf_reapr.domain.model.MonitoredDevice
import com.example.rf_reapr.domain.repository.DhcpMonitorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DhcpMonitorViewModel(
    private val repository: DhcpMonitorRepository
) : ViewModel() {

    private val _status = MutableStateFlow<DhcpMonitorRepository.MonitoringStatus>(DhcpMonitorRepository.MonitoringStatus.Idle)
    val status: StateFlow<DhcpMonitorRepository.MonitoringStatus> = _status.asStateFlow()

    val devices: StateFlow<List<MonitoredDevice>> = repository.activeDevices as StateFlow<List<MonitoredDevice>>

    fun startMonitoring() {
        viewModelScope.launch {
            repository.startMonitoring().collect {
                _status.value = it
            }
        }
    }

    fun stopMonitoring() {
        repository.stopMonitoring()
        _status.value = DhcpMonitorRepository.MonitoringStatus.Idle
    }

    fun clearNotifications() {
        repository.acknowledgeNewDevices()
    }
}
