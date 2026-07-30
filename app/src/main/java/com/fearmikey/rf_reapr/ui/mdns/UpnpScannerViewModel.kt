package com.fearmikey.rf_reapr.ui.mdns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.repository.SettingsRepository
import com.fearmikey.rf_reapr.domain.repository.UpnpDevice
import com.fearmikey.rf_reapr.domain.repository.UpnpScannerRepository
import com.fearmikey.rf_reapr.domain.scanner.NetworkScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UpnpScannerViewModel(
    private val repository: UpnpScannerRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val isPassiveMode: StateFlow<Boolean> = settingsRepository.isPassiveMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _scanResult = MutableStateFlow<NetworkScanner.ScanResult<UpnpDevice>>(NetworkScanner.ScanResult.Idle)
    val scanResult: StateFlow<NetworkScanner.ScanResult<UpnpDevice>> = _scanResult.asStateFlow()

    fun startScan() {
        if (isPassiveMode.value) return
        viewModelScope.launch {
            repository.startScan().collect {
                _scanResult.value = it
            }
        }
    }

    fun stopScan() {
        repository.stopScan()
    }
}
