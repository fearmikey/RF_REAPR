package com.fearmikey.rf_reapr.ui.iperf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.repository.LogRepository
import com.fearmikey.rf_reapr.domain.service.ActiveTaskMonitor
import com.fearmikey.rf_reapr.domain.service.HostValidator
import com.fearmikey.rf_reapr.domain.service.IperfResultParser
import com.fearmikey.rf_reapr.domain.service.IperfSummary
import com.fearmikey.rf_reapr.iperf.IperfConfig
import com.fearmikey.rf_reapr.iperf.IperfRepository
import com.fearmikey.rf_reapr.iperf.NetworkInterfaceInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

val IperfDurationPresets = listOf(5, 10, 30, 60)
val IperfStreamPresets = listOf(1, 2, 4, 8)
val IperfUdpBitratePresets = listOf(10, 100, 500, 1000)

class IperfViewModel(
    private val logRepository: LogRepository
) : ViewModel() {

    private val repository = IperfRepository()

    private val _interfaces = MutableStateFlow<List<NetworkInterfaceInfo>>(emptyList())
    val interfaces: StateFlow<List<NetworkInterfaceInfo>> = _interfaces.asStateFlow()

    private val _selectedInterface = MutableStateFlow<NetworkInterfaceInfo?>(null)
    val selectedInterface: StateFlow<NetworkInterfaceInfo?> = _selectedInterface.asStateFlow()

    private val _config = MutableStateFlow(IperfConfig())
    val config: StateFlow<IperfConfig> = _config.asStateFlow()

    private val _testOutput = MutableStateFlow("")
    val testOutput: StateFlow<String> = _testOutput.asStateFlow()

    private val _result = MutableStateFlow<IperfSummary?>(null)
    val result: StateFlow<IperfSummary?> = _result.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    /** Seconds since the test started (the native call only returns output at the end). */
    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    private var stoppedByUser = false

    init {
        loadInterfaces()
        viewModelScope.launch {
            ActiveTaskMonitor.stopAllSignal.collect { if (_isRunning.value) stopTest() }
        }
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

    fun updateConfig(transform: (IperfConfig) -> IperfConfig) {
        if (_isRunning.value) return
        _config.update(transform)
    }

    fun setSelectedInterface(interfaceInfo: NetworkInterfaceInfo) {
        _selectedInterface.value = interfaceInfo
    }

    fun stopTest() {
        stoppedByUser = true
        repository.stopTest()
    }

    fun startTest() {
        if (_isRunning.value) return
        val base = _config.value
        val config = if (base.isServerMode) {
            base
        } else {
            val host = HostValidator.normalize(base.serverHost)
            if (host == null) {
                _error.value = "Enter a valid iperf3 server hostname or IP address."
                return
            }
            base.copy(serverHost = host)
        }.copy(bindInterfaceIp = _selectedInterface.value?.ipv4Address)

        stoppedByUser = false
        _isRunning.value = true
        _error.value = null
        _result.value = null
        _elapsedSeconds.value = 0
        _testOutput.value = ""
        val modeLabel = if (config.isServerMode) "Server mode (port ${config.port})"
        else "${if (config.udp) "UDP" else "TCP"} ${if (config.reverse) "download from" else "upload to"} ${config.serverHost}"
        val taskName = "iPerf Test"
        ActiveTaskMonitor.addTask(taskName)

        viewModelScope.launch {
            val ticker: Job = launch {
                while (true) {
                    delay(1_000)
                    _elapsedSeconds.update { it + 1 }
                    ActiveTaskMonitor.updateStatus("iPerf: $modeLabel (${_elapsedSeconds.value}s)")
                }
            }
            try {
                val output = repository.runTest(config)
                _testOutput.value = output
                val summary = IperfResultParser.parse(output)
                _result.value = summary?.takeIf { it.headlineBps != null || it.intervals.isNotEmpty() }
                _error.value = when {
                    stoppedByUser && summary?.headlineBps == null -> "Test stopped by user."
                    summary?.error != null -> summary.error
                    summary == null -> output.lineSequence().lastOrNull { it.isNotBlank() } ?: "iperf3 returned no output."
                    else -> null
                }

                val headline = summary?.headlineBps
                if (headline != null) {
                    logRepository.saveLog(
                        type = "IPERF",
                        summary = "iPerf $modeLabel: ${IperfResultParser.formatBitrate(headline)}",
                        detailJson = output
                    )
                }
            } finally {
                ticker.cancel()
                _isRunning.value = false
                ActiveTaskMonitor.removeTask(taskName)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // The native call blocks an IO thread; make sure leaving the screen ends the test.
        if (_isRunning.value) repository.stopTest()
    }
}
