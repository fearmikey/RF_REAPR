package com.fearmikey.rf_reapr.ui.mdns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.repository.UpnpDevice
import com.fearmikey.rf_reapr.domain.repository.UpnpScannerRepository
import com.fearmikey.rf_reapr.domain.scanner.NetworkScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UpnpScannerViewModel(
    private val repository: UpnpScannerRepository
) : ViewModel() {

    private val _scanResult = MutableStateFlow<NetworkScanner.ScanResult<UpnpDevice>>(NetworkScanner.ScanResult.Idle)
    val scanResult: StateFlow<NetworkScanner.ScanResult<UpnpDevice>> = _scanResult.asStateFlow()

    fun startScan() {
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
