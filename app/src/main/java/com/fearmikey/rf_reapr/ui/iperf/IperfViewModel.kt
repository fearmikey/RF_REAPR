package com.fearmikey.rf_reapr.ui.iperf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.iperf.IperfRepository
import com.fearmikey.rf_reapr.iperf.NetworkInterfaceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class IperfViewModel : ViewModel() {

    private val repository = IperfRepository()

    private val _interfaces = MutableStateFlow<List<NetworkInterfaceInfo>>(emptyList())
    val interfaces: StateFlow<List<NetworkInterfaceInfo>> = _interfaces.asStateFlow()

    private val _selectedInterface = MutableStateFlow<NetworkInterfaceInfo?>(null)
    val selectedInterface: StateFlow<NetworkInterfaceInfo?> = _selectedInterface.asStateFlow()

    private val _serverIp = MutableStateFlow("")
    val serverIp: StateFlow<String> = _serverIp.asStateFlow()

    private val _testOutput = MutableStateFlow("")
    val testOutput: StateFlow<String> = _testOutput.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    init {
        loadInterfaces()
    }

    fun loadInterfaces() {
        viewModelScope.launch {
            val available = repository.getAvailableNetworkInterfaces()
            _interfaces.value = available
            
            // Auto-select eth0 if it exists
            val eth0 = available.find { it.name.startsWith("eth") }
            if (eth0 != null) {
                _selectedInterface.value = eth0
            } else if (available.isNotEmpty() && _selectedInterface.value == null) {
                _selectedInterface.value = available.first()
            }
        }
    }

    fun setServerIp(ip: String) {
        _serverIp.value = ip
    }

    fun setSelectedInterface(interfaceInfo: NetworkInterfaceInfo) {
        _selectedInterface.value = interfaceInfo
    }

    fun startTest() {
        if (_serverIp.value.isBlank()) {
            _testOutput.value = "Please enter a valid server IP address."
            return
        }

        viewModelScope.launch {
            _isRunning.value = true
            _testOutput.value = "Starting test to ${_serverIp.value}...\nBinding to interface: ${_selectedInterface.value?.name ?: "Default"}\n\n"
            
            val result = repository.runTest(
                serverIp = _serverIp.value,
                bindInterfaceIp = _selectedInterface.value?.ipv4Address
            )
            
            _testOutput.value += result
            _isRunning.value = false
        }
    }
}