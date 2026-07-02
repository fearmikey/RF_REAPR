package com.example.rf_reapr.ui.wifi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rf_reapr.domain.model.WifiAccessPoint
import com.example.rf_reapr.domain.repository.WifiFingerprintRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WifiFingerprintViewModel(
    private val repository: WifiFingerprintRepository
) : ViewModel() {

    private val _accessPoints = MutableStateFlow<List<WifiAccessPoint>>(emptyList())
    val accessPoints: StateFlow<List<WifiAccessPoint>> = _accessPoints

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private var scanJob: Job? = null

    fun startScan() {
        stopScan()
        _isScanning.value = true
        scanJob = viewModelScope.launch {
            repository.startWifiScan().collect {
                _accessPoints.value = it
            }
        }
    }

    fun stopScan() {
        _isScanning.value = false
        scanJob?.cancel()
        repository.stopWifiScan()
    }
}
