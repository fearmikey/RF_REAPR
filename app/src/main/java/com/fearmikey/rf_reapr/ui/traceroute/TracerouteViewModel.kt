package com.fearmikey.rf_reapr.ui.traceroute

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.repository.LogRepository
import com.fearmikey.rf_reapr.domain.repository.SettingsRepository
import com.fearmikey.rf_reapr.domain.repository.TracerouteHop
import com.fearmikey.rf_reapr.domain.repository.TracerouteOptions
import com.fearmikey.rf_reapr.domain.repository.TracerouteRepository
import com.fearmikey.rf_reapr.domain.repository.TracerouteResult
import com.fearmikey.rf_reapr.domain.service.ActiveTaskMonitor
import com.fearmikey.rf_reapr.domain.service.HostValidator
import com.google.gson.Gson
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val TracerouteMaxHopPresets = listOf(15, 30, 64)

class TracerouteViewModel(
    private val repository: TracerouteRepository,
    private val logRepository: LogRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val isPassiveMode: StateFlow<Boolean> = settingsRepository.isPassiveMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _result = MutableStateFlow<TracerouteResult?>(null)
    val result: StateFlow<TracerouteResult?> = _result.asStateFlow()

    val hops: StateFlow<List<TracerouteHop>> = _result
        .map { it?.hops.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _maxHops = MutableStateFlow(30)
    val maxHops: StateFlow<Int> = _maxHops.asStateFlow()

    private var scanJob: Job? = null
    private val gson = Gson()

    init {
        viewModelScope.launch {
            ActiveTaskMonitor.stopAllSignal.collect {
                stopTraceroute()
            }
        }
    }

    fun setMaxHops(value: Int) {
        _maxHops.value = value
    }

    fun startTraceroute(input: String) {
        if (isPassiveMode.value || input.isBlank()) return
        val host = HostValidator.normalize(input)
        if (host == null) {
            _result.value = TracerouteResult(input, isComplete = true, error = "Enter a valid hostname or IP address")
            return
        }
        stopTraceroute()
        _isScanning.value = true
        _result.value = TracerouteResult(host)
        val taskName = "Traceroute ($host)"
        ActiveTaskMonitor.addTask(taskName)
        scanJob = viewModelScope.launch {
            try {
                repository.traceroute(TracerouteOptions(host = host, maxHops = _maxHops.value)).collect { update ->
                    _result.value = update
                    val last = update.hops.lastOrNull()
                    ActiveTaskMonitor.updateStatus("$taskName: hop ${last?.hopNumber ?: 0}")
                }
            } finally {
                // The flow completes when the destination is reached, the hop limit is hit,
                // or too many consecutive hops are silent. Finalize state here so the progress
                // indicator is always cleared, even when stopped manually.
                ActiveTaskMonitor.removeTask(taskName)
                _isScanning.value = false
                withContext(NonCancellable) { saveLog() }
            }
        }
    }

    private suspend fun saveLog() {
        val result = _result.value ?: return
        if (result.hops.isEmpty()) return
        logRepository.saveLog(
            "Traceroute",
            when {
                result.reachedDestination -> "Traceroute to ${result.target} completed in ${result.hops.size} hops"
                result.isComplete -> "Traceroute to ${result.target} incomplete (destination unreachable)"
                else -> "Traceroute to ${result.target} stopped after ${result.hops.size} hops"
            },
            gson.toJson(mapOf("result" to result, "report" to result.toReport()))
        )
    }

    fun stopTraceroute() {
        scanJob?.cancel()
        scanJob = null
        _isScanning.value = false
    }
}
