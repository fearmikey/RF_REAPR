package com.fearmikey.rf_reapr.ui.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.repository.CaptivePortalRepository
import com.fearmikey.rf_reapr.domain.repository.FindingRepository
import com.fearmikey.rf_reapr.domain.repository.LogRepository
import com.fearmikey.rf_reapr.domain.service.FindingAdapters
import com.fearmikey.rf_reapr.domain.service.PortalProbeResult
import com.fearmikey.rf_reapr.domain.service.PortalVerdict
import com.fearmikey.rf_reapr.domain.service.SystemNetworkState
import com.google.gson.Gson
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CaptivePortalUiState(
    val isTesting: Boolean = false,
    val system: SystemNetworkState? = null,
    val results: List<PortalProbeResult> = emptyList(),
    val verdict: PortalVerdict? = null
)

class CaptivePortalViewModel(
    private val repository: CaptivePortalRepository,
    private val logRepository: LogRepository? = null,
    private val findingRepository: FindingRepository? = null
) : ViewModel() {

    private val _state = MutableStateFlow(CaptivePortalUiState())
    val state: StateFlow<CaptivePortalUiState> = _state.asStateFlow()

    private var job: Job? = null
    private val gson = Gson()

    init {
        _state.value = CaptivePortalUiState(system = repository.systemState())
    }

    fun runTest() {
        if (_state.value.isTesting) return
        _state.value = _state.value.copy(isTesting = true, results = emptyList(), verdict = null)
        job = viewModelScope.launch {
            val check = repository.check()
            val verdict = check.verdict
            _state.value = CaptivePortalUiState(isTesting = false, system = check.system, results = check.results, verdict = verdict)
            findingRepository?.record(FindingAdapters.captivePortal(check.system?.transport, verdict))
            logRepository?.saveLog(
                type = "WEB",
                summary = "Captive portal check: ${verdict.title}",
                detailJson = gson.toJson(mapOf("tool" to "Captive Portal Detector", "verdict" to verdict, "system" to check.system, "probes" to check.results))
            )
        }
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }
}
