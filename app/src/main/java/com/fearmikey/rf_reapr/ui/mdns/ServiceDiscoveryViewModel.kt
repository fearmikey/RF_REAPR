package com.fearmikey.rf_reapr.ui.mdns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.repository.DiscoveredService
import com.fearmikey.rf_reapr.domain.repository.LogRepository
import com.fearmikey.rf_reapr.domain.repository.ServiceDiscoveryRepository
import com.fearmikey.rf_reapr.domain.repository.SettingsRepository
import com.fearmikey.rf_reapr.domain.service.ActiveTaskMonitor
import com.fearmikey.rf_reapr.domain.service.MdnsServiceCatalog
import com.google.gson.Gson
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class ServiceDiscoveryViewModel(
    private val repository: ServiceDiscoveryRepository,
    private val logRepository: LogRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val isPassiveMode: StateFlow<Boolean> = settingsRepository.isPassiveMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _services = MutableStateFlow<List<DiscoveredService>>(emptyList())
    val services: StateFlow<List<DiscoveredService>> = _services.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    /** Seconds left before the current browse auto-stops; null when idle. */
    private val _secondsRemaining = MutableStateFlow<Int?>(null)
    val secondsRemaining: StateFlow<Int?> = _secondsRemaining.asStateFlow()

    /** True once at least one browse has completed (distinguishes "none found" from "not run"). */
    private val _hasCompletedScan = MutableStateFlow(false)
    val hasCompletedScan: StateFlow<Boolean> = _hasCompletedScan.asStateFlow()

    private var scanJob: Job? = null
    private var hasAutoStarted = false
    private val gson = Gson()

    init {
        viewModelScope.launch {
            ActiveTaskMonitor.stopAllSignal.collect { stopDiscovery() }
        }
    }

    /** Auto-start once per ViewModel, so rotation or returning to the screen doesn't restart. */
    fun startIfIdle() {
        if (hasAutoStarted) return
        hasAutoStarted = true
        viewModelScope.launch {
            // Read the persisted setting rather than the StateFlow's initial `false`, so we
            // never auto-start an active browse while Passive Mode is on.
            if (!settingsRepository.isPassiveMode.first()) startDiscovery()
        }
    }

    fun startDiscovery() {
        if (isPassiveMode.value || scanJob?.isActive == true) return
        _isScanning.value = true
        _services.value = emptyList()
        ActiveTaskMonitor.addTask(TASK_NAME)

        scanJob = viewModelScope.launch {
            val countdown = launch {
                for (remaining in SCAN_DURATION_SECONDS downTo 1) {
                    _secondsRemaining.value = remaining
                    ActiveTaskMonitor.updateStatus("$TASK_NAME: ${_services.value.size} services")
                    delay(1_000)
                }
            }
            try {
                withTimeoutOrNull(SCAN_DURATION_SECONDS * 1_000L) {
                    repository.startDiscovery().collect { _services.value = it }
                }
            } finally {
                countdown.cancel()
                _isScanning.value = false
                _secondsRemaining.value = null
                _hasCompletedScan.value = true
                ActiveTaskMonitor.removeTask(TASK_NAME)
                withContext(NonCancellable) { saveLog(_services.value) }
            }
        }
    }

    fun stopDiscovery() {
        scanJob?.cancel()
    }

    private suspend fun saveLog(found: List<DiscoveredService>) {
        if (found.isEmpty()) return
        val hosts = MdnsServiceCatalog.groupByHost(found)
        val flagged = hosts.sumOf { it.securityNotes.size }
        logRepository.saveLog(
            type = "TOPOLOGY",
            summary = "mDNS service discovery: ${found.size} services on ${hosts.size} hosts" +
                if (flagged > 0) " ($flagged flagged)" else "",
            detailJson = gson.toJson(found)
        )
    }

    override fun onCleared() {
        super.onCleared()
        stopDiscovery()
    }

    companion object {
        private const val TASK_NAME = "Service Discovery"
        const val SCAN_DURATION_SECONDS = 60
    }
}
