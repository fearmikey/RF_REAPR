package com.fearmikey.rf_reapr.ui.network

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.system.PcapVpnService
import com.fearmikey.rf_reapr.system.RootPcapCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PacketCaptureViewModel(application: Application) : AndroidViewModel(application) {

    private val rootCapture = RootPcapCapture(application)

    private val _isCapturing = MutableStateFlow(value = false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _isRootMode = MutableStateFlow(value = false)
    val isRootMode: StateFlow<Boolean> = _isRootMode.asStateFlow()
    
    private val _hasRoot = MutableStateFlow(value = false)
    val hasRoot: StateFlow<Boolean> = _hasRoot.asStateFlow()
    
    private val _hasTcpdump = MutableStateFlow(value = false)
    val hasTcpdump: StateFlow<Boolean> = _hasTcpdump.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _activeFilePath = MutableStateFlow<String?>(null)
    val activeFilePath: StateFlow<String?> = _activeFilePath.asStateFlow()

    init {
        checkRootCapabilities()
    }

    private fun checkRootCapabilities() {
        viewModelScope.launch(Dispatchers.IO) {
            _hasRoot.value = rootCapture.isRootAvailable()
            _hasTcpdump.value = rootCapture.isTcpdumpAvailable()
        }
    }

    fun toggleMode(useRoot: Boolean) {
        if (_isCapturing.value) return // Don't allow changing mode while running
        _isRootMode.value = useRoot
    }
    
    fun clearError() {
        _errorMessage.value = null
    }

    fun startStandardCapture() {
        if (_isCapturing.value) return
        
        try {
            val intent = Intent(getApplication(), PcapVpnService::class.java).apply {
                action = PcapVpnService.ACTION_START
            }
            getApplication<Application>().startService(intent)
            _isCapturing.value = true
        } catch (e: Exception) {
            _errorMessage.value = "Failed to start VPN Service: ${e.message}"
        }
    }
    
    fun stopStandardCapture() {
        if (!_isCapturing.value || _isRootMode.value) return
        
        val intent = Intent(getApplication(), PcapVpnService::class.java).apply {
            action = PcapVpnService.ACTION_STOP
        }
        getApplication<Application>().startService(intent)
        _isCapturing.value = false
    }

    fun startRootCapture() {
        if (_isCapturing.value) return
        
        if (!_hasRoot.value) {
            _errorMessage.value = "Root access is not available."
            return
        }
        if (!_hasTcpdump.value) {
            _errorMessage.value = "tcpdump binary not found on device."
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            val path = rootCapture.startCapture()
            if (path != null) {
                _activeFilePath.value = path
                _isCapturing.value = true
            } else {
                _errorMessage.value = "Failed to start root capture (tcpdump error)"
            }
        }
    }

    fun stopRootCapture() {
        if (!_isCapturing.value || !_isRootMode.value) return
        
        viewModelScope.launch(Dispatchers.IO) {
            rootCapture.stopCapture()
            _isCapturing.value = false
            _activeFilePath.value = null
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        if (_isCapturing.value) {
            if (_isRootMode.value) {
                stopRootCapture()
            } else {
                stopStandardCapture()
            }
        }
    }
}
