package com.fearmikey.rf_reapr.ui.web

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.repository.LogRepository
import com.fearmikey.rf_reapr.domain.repository.SubdomainFinderRepository
import com.fearmikey.rf_reapr.domain.repository.SubdomainFinderRepository.SubdomainResult
import com.fearmikey.rf_reapr.domain.service.ActiveTaskMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SubdomainFinderViewModel(
    private val repository: SubdomainFinderRepository,
    private val logRepository: LogRepository
) : ViewModel() {

    private val _subdomains = MutableStateFlow(SubdomainFinderRepository.SubdomainListWrapper())
    val subdomains: StateFlow<SubdomainFinderRepository.SubdomainListWrapper> = _subdomains.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _isFinished = MutableStateFlow(false)
    val isFinished: StateFlow<Boolean> = _isFinished.asStateFlow()

    init {
        viewModelScope.launch {
            ActiveTaskMonitor.stopAllSignal.collect {
                // If the signal is received, we should ensure any running scan is stopped.
                // In this simplified implementation, we rely on the launch job being cancelled 
                // if we had a reference to it, or we just reset state.
                _isFinished.value = true
                _progress.value = 0f
            }
        }
    }

    fun startSearch(domain: String) {
        if (domain.isBlank()) return
        
        viewModelScope.launch {
            _subdomains.value = SubdomainFinderRepository.SubdomainListWrapper(emptyList())
            _progress.value = 0f
            _isFinished.value = false
            
            val taskName = "Subdomain Enumeration ($domain)"
            ActiveTaskMonitor.addTask(taskName)

            try {
                repository.findSubdomains(domain).collect { result ->
                    _subdomains.value = SubdomainFinderRepository.SubdomainListWrapper(result.subdomains)
                    _progress.value = result.progress
                    _isFinished.value = result.isFinished
                    
                    if (result.isFinished) {
                        logRepository.saveLog(
                            type = "WEB",
                            summary = "Subdomain scan for $domain",
                            detailJson = "Found ${result.subdomains.size} subdomains: ${result.subdomains.joinToString { it.hostname }}"
                        )
                    }
                }
            } finally {
                ActiveTaskMonitor.removeTask(taskName)
            }
        }
    }
}
