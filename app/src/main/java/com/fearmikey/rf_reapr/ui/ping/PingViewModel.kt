package com.fearmikey.rf_reapr.ui.ping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.repository.PingRepository
import com.fearmikey.rf_reapr.domain.repository.PingRepository.PingStatus
import com.fearmikey.rf_reapr.domain.repository.LogRepository
import com.fearmikey.rf_reapr.domain.service.ActiveTaskMonitor
import com.google.gson.Gson
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PingViewModel(
    private val repository: PingRepository,
    private val logRepository: LogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PingStatus>(PingStatus.Idle)
    val uiState: StateFlow<PingStatus> = _uiState.asStateFlow()

    private val _pingLines = MutableStateFlow<List<String>>(emptyList())
    val pingLines: StateFlow<List<String>> = _pingLines.asStateFlow()

    private var pingJob: Job? = null
    private val gson = Gson()
    private var currentHost: String = ""

    init {
        viewModelScope.launch {
            ActiveTaskMonitor.stopAllSignal.collect {
                cancelPing()
            }
        }
    }

    fun runPing(host: String, continuous: Boolean) {
        if (host.isBlank()) return
        
        cancelPing()
        _pingLines.value = emptyList()
        currentHost = host
        
        val taskName = "Ping ($host)"
        ActiveTaskMonitor.addTask(taskName)

        pingJob = viewModelScope.launch {
            try {
                val count = if (continuous) null else 4
                repository.ping(host, count).collect { status ->
                    _uiState.value = status
                    if (status is PingStatus.Progress) {
                        _pingLines.value = _pingLines.value + status.line
                    } else if (status is PingStatus.Success) {
                        logPingResults()
                    }
                }
            } finally {
                ActiveTaskMonitor.removeTask(taskName)
            }
        }
    }

    private fun logPingResults() {
        val lines = _pingLines.value
        if (lines.isNotEmpty()) {
            viewModelScope.launch {
                logRepository.saveLog(
                    type = "PING",
                    summary = "Pinged $currentHost, ${lines.size} replies",
                    detailJson = gson.toJson(mapOf("host" to currentHost, "lines" to lines))
                )
            }
        }
    }

    fun cancelPing() {
        if (pingJob != null) {
            logPingResults()
            pingJob?.cancel()
            pingJob = null
        }
        _uiState.value = PingStatus.Idle
    }

    fun reset() {
        cancelPing()
        _pingLines.value = emptyList()
    }
}
