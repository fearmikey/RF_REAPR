package com.fearmikey.rf_reapr.ui.web

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.repository.WebsiteInspectorRepository
import com.fearmikey.rf_reapr.domain.repository.WebsiteInspectorRepository.WebsiteInspectorStatus
import com.fearmikey.rf_reapr.domain.repository.WebsiteInspectorRepository.InspectorStatus
import com.fearmikey.rf_reapr.domain.repository.LogRepository
import com.fearmikey.rf_reapr.domain.service.ActiveTaskMonitor
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WebsiteInspectorViewModel(
    private val repository: WebsiteInspectorRepository,
    private val logRepository: LogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WebsiteInspectorStatus())
    val uiState: StateFlow<WebsiteInspectorStatus> = _uiState.asStateFlow()
    private val gson = Gson()

    init {
        viewModelScope.launch {
            ActiveTaskMonitor.stopAllSignal.collect {
                reset()
            }
        }
    }

    fun inspectWebsite(url: String) {
        if (url.isBlank()) return
        
        val taskName = "Website Audit ($url)"
        ActiveTaskMonitor.addTask(taskName)

        viewModelScope.launch {
            try {
                repository.inspectWebsite(url).collect { status ->
                    _uiState.value = status
                    
                    // Log when all sections are finished
                    if (status.httpStatus is InspectorStatus.Success &&
                        status.tlsStatus is InspectorStatus.Success &&
                        status.dnsStatus is InspectorStatus.Success &&
                        status.rdapStatus is InspectorStatus.Success) {
                        
                        logRepository.saveLog(
                            type = "WEB",
                            summary = "Inspected $url",
                            detailJson = gson.toJson(status)
                        )
                    }
                }
            } finally {
                ActiveTaskMonitor.removeTask(taskName)
            }
        }
    }

    fun reset() {
        _uiState.value = WebsiteInspectorStatus()
    }
}
