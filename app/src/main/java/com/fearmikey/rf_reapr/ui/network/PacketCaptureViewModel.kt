package com.fearmikey.rf_reapr.ui.network

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.service.CaptureMode
import com.fearmikey.rf_reapr.domain.service.CaptureState
import com.fearmikey.rf_reapr.domain.service.PacketCaptureSession
import com.fearmikey.rf_reapr.system.PcapVpnService
import com.fearmikey.rf_reapr.system.RootPcapCapture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PacketCaptureViewModel(application: Application) : AndroidViewModel(application) {

    private val rootCapture = RootPcapCapture.get(application)

    // Start/stop must outlive this ViewModel: previously stopRootCapture() was launched in
    // viewModelScope from onCleared(), which is already cancelled, leaving tcpdump running.
    private val captureScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Real capture state, published by the VPN service / root runner. */
    val captureState: StateFlow<CaptureState> = PacketCaptureSession.state

    val isCapturing: StateFlow<Boolean> = PacketCaptureSession.state
        .map { it.isRunning }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PacketCaptureSession.state.value.isRunning)

    private val _isRootMode = MutableStateFlow(PacketCaptureSession.state.value.mode == CaptureMode.ROOT_TCPDUMP)
    val isRootMode: StateFlow<Boolean> = _isRootMode.asStateFlow()

    private val _hasRoot = MutableStateFlow(value = false)
    val hasRoot: StateFlow<Boolean> = _hasRoot.asStateFlow()

    private val _hasTcpdump = MutableStateFlow(value = false)
    val hasTcpdump: StateFlow<Boolean> = _hasTcpdump.asStateFlow()

    private val _isCheckingRoot = MutableStateFlow(value = true)
    val isCheckingRoot: StateFlow<Boolean> = _isCheckingRoot.asStateFlow()

    private val _rootFilter = MutableStateFlow("")
    val rootFilter: StateFlow<String> = _rootFilter.asStateFlow()

    private val _localError = MutableStateFlow<String?>(null)

    val errorMessage: StateFlow<String?> = combine(_localError, PacketCaptureSession.state) { local, state ->
        local ?: state.error
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        checkRootCapabilities()
    }

    private fun checkRootCapabilities() {
        viewModelScope.launch(Dispatchers.IO) {
            _hasRoot.value = rootCapture.isRootAvailable()
            _hasTcpdump.value = _hasRoot.value && rootCapture.isTcpdumpAvailable()
            _isCheckingRoot.value = false
        }
    }

    fun toggleMode(useRoot: Boolean) {
        if (isCapturing.value) return // Don't allow changing mode while running
        _isRootMode.value = useRoot
    }

    fun setRootFilter(value: String) {
        _rootFilter.value = value
    }

    val isRootFilterValid: Boolean
        get() = RootPcapCapture.isValidFilter(_rootFilter.value.trim())

    fun clearError() {
        _localError.value = null
        PacketCaptureSession.clearError()
    }

    fun startStandardCapture() {
        if (isCapturing.value) return
        clearError()
        try {
            val intent = Intent(getApplication(), PcapVpnService::class.java).apply {
                action = PcapVpnService.ACTION_START
            }
            getApplication<Application>().startService(intent)
        } catch (e: Exception) {
            _localError.value = "Failed to start VPN Service: ${e.message}"
        }
    }

    fun stopStandardCapture() {
        val intent = Intent(getApplication(), PcapVpnService::class.java).apply {
            action = PcapVpnService.ACTION_STOP
        }
        getApplication<Application>().startService(intent)
    }

    fun startRootCapture() {
        if (isCapturing.value) return
        clearError()
        when {
            !_hasRoot.value -> _localError.value = "Root access is not available."
            !_hasTcpdump.value -> _localError.value = "tcpdump binary not found on device."
            !isRootFilterValid -> _localError.value = "Invalid capture filter. Use BPF syntax, e.g. \"port 53\" or \"host 192.168.1.10\"."
            else -> captureScope.launch {
                if (rootCapture.startCapture(_rootFilter.value) == null && PacketCaptureSession.state.value.error == null) {
                    _localError.value = "Failed to start root capture (tcpdump error)"
                }
            }
        }
    }

    fun stopRootCapture() {
        captureScope.launch { rootCapture.stopCapture() }
    }

    fun stopCapture() {
        when (PacketCaptureSession.state.value.mode) {
            CaptureMode.ROOT_TCPDUMP -> stopRootCapture()
            else -> stopStandardCapture()
        }
    }
    // No onCleared() auto-stop: captures keep running in the background (the VPN service
    // has a notification with a Stop action; root mode is tracked by ActiveTaskMonitor), and
    // the screen re-attaches to the live state when reopened.
}
