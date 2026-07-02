package com.example.rf_reapr.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rf_reapr.domain.model.BleDevice
import com.example.rf_reapr.domain.repository.BleScannerRepository
import com.example.rf_reapr.domain.scanner.NetworkScanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BleScannerViewModel(
    private val repository: BleScannerRepository
) : ViewModel() {

    private val _scanState = MutableStateFlow<NetworkScanner.ScanResult<BleDevice>>(NetworkScanner.ScanResult.Idle)
    val scanState: StateFlow<NetworkScanner.ScanResult<BleDevice>> = _scanState.asStateFlow()

    private var scanJob: Job? = null

    fun startScan() {
        stopScan()
        scanJob = viewModelScope.launch {
            repository.startScan().collect { result ->
                _scanState.value = result
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        _scanState.value = NetworkScanner.ScanResult.Idle
    }
}
