package com.example.rf_reapr.ui.wireless

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rf_reapr.domain.model.BleDevice
import com.example.rf_reapr.domain.repository.BleProximityRepository
import com.example.rf_reapr.domain.repository.BleScannerRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class BluetoothProximityFinderViewModel(
    private val proximityRepository: BleProximityRepository,
    private val scannerRepository: BleScannerRepository
) : ViewModel() {

    private val _targetAddress = MutableStateFlow<String?>(null)
    val targetAddress = _targetAddress.asStateFlow()

    private val _targetName = MutableStateFlow<String?>(null)
    val targetName = _targetName.asStateFlow()

    private val _currentRssi = MutableStateFlow<Int?>(null)
    val currentRssi = _currentRssi.asStateFlow()

    private val _rssiHistory = MutableStateFlow<List<Int>>(emptyList())
    val rssiHistory = _rssiHistory.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking = _isTracking.asStateFlow()

    private val _isDiscoveryActive = MutableStateFlow(false)
    val isDiscoveryActive = _isDiscoveryActive.asStateFlow()

    private val _isActiveMode = MutableStateFlow(false)
    val isActiveMode = _isActiveMode.asStateFlow()

    private val _recentlySeen = MutableStateFlow<List<BleDevice>>(emptyList())
    val recentlySeen = _recentlySeen.asStateFlow()

    private var trackingJob: Job? = null
    private var discoveryJob: Job? = null
    private var feedbackJob: Job? = null
    
    private var smoothedRssi: Float? = null
    private val alpha = 0.3f // Smoothing factor (0.0 to 1.0). Lower is smoother.

    init {
        // Observe discovered devices regardless of whether discovery is active
        viewModelScope.launch {
            scannerRepository.getDiscoveredDevices().collect {
                _recentlySeen.value = it
            }
        }
    }

    fun toggleActiveMode(active: Boolean) {
        _isActiveMode.value = active
        if (_isDiscoveryActive.value) {
            startDiscovery() // Restart discovery with new mode
        }
    }

    fun startDiscovery() {
        stopDiscovery()
        _isDiscoveryActive.value = true
        discoveryJob = viewModelScope.launch {
            scannerRepository.startScan(_isActiveMode.value).collect { result ->
                // The repository updates getDiscoveredDevices() internally
            }
        }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        _isDiscoveryActive.value = false
    }

    fun setTarget(address: String, name: String? = null) {
        if (address.isBlank()) {
            _targetAddress.value = null
            _targetName.value = null
            stopTracking()
            startDiscovery()
            return
        }
        _targetAddress.value = address
        _targetName.value = name
        stopDiscovery()
        if (_isTracking.value) {
            startTracking()
        }
    }

    fun toggleTracking() {
        if (_isTracking.value) {
            stopTracking()
        } else {
            startTracking()
        }
    }

    private fun startTracking() {
        val address = _targetAddress.value ?: return
        stopTracking()
        _isTracking.value = true
        
        trackingJob = viewModelScope.launch {
            proximityRepository.trackDevice(address)
                .catch { /* Handle error */ }
                .collect { rssi ->
                    // Apply Exponential Moving Average (EMA) for smoothing
                    val newSmoothed = if (smoothedRssi == null) {
                        rssi.toFloat()
                    } else {
                        smoothedRssi!! + alpha * (rssi - smoothedRssi!!)
                    }
                    smoothedRssi = newSmoothed
                    val finalRssi = newSmoothed.toInt()

                    _currentRssi.value = finalRssi
                    _rssiHistory.update { history ->
                        (history + finalRssi).takeLast(60) // Keep last 60 readings
                    }
                }
        }

        // Feedback loop: vibration frequency depends on RSSI
        feedbackJob = viewModelScope.launch {
            while (true) {
                val rssi = _currentRssi.value
                if (rssi != null) {
                    // Map RSSI (-100 to -30) to delay (1000ms to 100ms)
                    val clampedRssi = rssi.coerceIn(-100, -30)
                    val delayMs = ((clampedRssi + 100) * (100 - 1000) / ( -30 + 100) + 1000).toLong()
                    
                    _vibrationTrigger.emit(Unit)
                    delay(delayMs.coerceAtLeast(100))
                } else {
                    delay(1000)
                }
            }
        }
    }

    private fun stopTracking() {
        trackingJob?.cancel()
        feedbackJob?.cancel()
        _isTracking.value = false
        _currentRssi.value = null
        _rssiHistory.value = emptyList()
        smoothedRssi = null
    }

    private val _vibrationTrigger = MutableSharedFlow<Unit>()
    val vibrationTrigger = _vibrationTrigger.asSharedFlow()
}
