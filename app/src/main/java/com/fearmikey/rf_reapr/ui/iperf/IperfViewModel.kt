package com.fearmikey.rf_reapr.ui.iperf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.iperf.IperfRepository
import com.fearmikey.rf_reapr.iperf.NetworkInterfaceInfo
import com.fearmikey.rf_reapr.domain.repository.LogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class IperfViewModel(
    private val logRepository: LogRepository
) : ViewModel() {

    private val repository = IperfRepository()

    private val _interfaces = MutableStateFlow<List<NetworkInterfaceInfo>>(emptyList())
    val interfaces: StateFlow<List<NetworkInterfaceInfo>> = _interfaces.asStateFlow()

    private val _selectedInterface = MutableStateFlow<NetworkInterfaceInfo?>(null)
    val selectedInterface: StateFlow<NetworkInterfaceInfo?> = _selectedInterface.asStateFlow()

    private val _serverIp = MutableStateFlow("")
    val serverIp: StateFlow<String> = _serverIp.asStateFlow()

    private val _isServerMode = MutableStateFlow(false)
    val isServerMode: StateFlow<Boolean> = _isServerMode.asStateFlow()

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

            // If the currently selected interface still exists (matched by name), refresh it
            // with the latest data (e.g. a new IPv4 address after switching Wi-Fi networks)
            // instead of leaving the stale, previously-selected entry in place.
            val currentName = _selectedInterface.value?.name
            val refreshedSelection = currentName?.let { name -> available.find { it.name == name } }

            _selectedInterface.value = when {
                refreshedSelection != null -> refreshedSelection
                else -> {
                    // Previously selected interface is gone (or nothing was selected yet).
                    // Auto-select eth0 if it exists, otherwise fall back to the first interface.
                    available.find { it.name.startsWith("eth") } ?: available.firstOrNull()
                }
            }
        }
    }

    fun setServerIp(ip: String) {
        _serverIp.value = ip
    }

    fun setServerMode(isServer: Boolean) {
        _isServerMode.value = isServer
    }

    fun setSelectedInterface(interfaceInfo: NetworkInterfaceInfo) {
        _selectedInterface.value = interfaceInfo
    }

    fun stopTest() {
        repository.stopTest()
        _testOutput.value += "\n\n[ABORTED BY USER]\n"
    }

    fun startTest() {
        val isServer = _isServerMode.value
        if (!isServer && _serverIp.value.isBlank()) {
            _testOutput.value = "Please enter a valid server IP address."
            return
        }

        viewModelScope.launch {
            _isRunning.value = true
            val modeLabel = if (isServer) "Server Mode" else "Client Mode to ${_serverIp.value}"
            _testOutput.value = "Starting iPerf3 $modeLabel...\nBinding to interface: ${_selectedInterface.value?.name ?: "Default"}\n\n"
            
            val result = repository.runTest(
                serverIp = _serverIp.value,
                isServerMode = isServer,
                bindInterfaceIp = _selectedInterface.value?.ipv4Address
            )
            
            _testOutput.value += result
            _isRunning.value = false

            // Save to logs if successful (assuming any non-error result from JNI is "success" for logging)
            if (!result.contains("Error executing iPerf")) {
                logRepository.saveLog(
                    type = "IPERF",
                    summary = "iPerf $modeLabel",
                    detailJson = result
                )
            }
        }
    }
}