package com.fearmikey.rf_reapr.ui.web

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.repository.CloudAssetScannerRepository
import com.fearmikey.rf_reapr.domain.repository.CloudAssetScannerRepository.CloudScanOptions
import com.fearmikey.rf_reapr.domain.repository.CloudAssetScannerRepository.CloudScanResult
import com.fearmikey.rf_reapr.domain.repository.FindingRepository
import com.fearmikey.rf_reapr.domain.repository.LogRepository
import com.fearmikey.rf_reapr.domain.repository.SettingsRepository
import com.fearmikey.rf_reapr.domain.service.ActiveTaskMonitor
import com.fearmikey.rf_reapr.domain.service.CloudBucketPlanner
import com.fearmikey.rf_reapr.domain.service.CloudProvider
import com.google.gson.Gson
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CloudAssetScannerViewModel(
    private val repository: CloudAssetScannerRepository,
    private val logRepository: LogRepository,
    private val settingsRepository: SettingsRepository,
    private val findingRepository: FindingRepository? = null
) : ViewModel() {

    private val gson = Gson()

    val isPassiveMode: StateFlow<Boolean> = settingsRepository.isPassiveMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _uiState = MutableStateFlow(CloudScanResult())
    val uiState: StateFlow<CloudScanResult> = _uiState.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _providers = MutableStateFlow(CloudScanOptions().providers)
    val providers: StateFlow<Set<CloudProvider>> = _providers.asStateFlow()

    private val _recordedFindings = MutableStateFlow(0)
    val recordedFindings: StateFlow<Int> = _recordedFindings.asStateFlow()

    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            ActiveTaskMonitor.stopAllSignal.collect { stopScan() }
        }
    }

    fun toggleProvider(provider: CloudProvider) {
        if (_isScanning.value) return
        _providers.update { if (provider in it) it - provider else it + provider }
    }

    /** How many HTTP requests a scan with the current settings will make, so the user sees the cost first. */
    fun plannedRequests(domain: String, extraKeywords: String): Int {
        val target = CloudBucketPlanner.normalizeDomain(domain) ?: return 0
        val keywords = (CloudBucketPlanner.defaultKeywords + CloudBucketPlanner.parseKeywords(extraKeywords)).distinct()
        return CloudBucketPlanner.probes(CloudBucketPlanner.candidateNames(target, keywords), _providers.value).size
    }

    fun scanAssets(domain: String, extraKeywords: String = "") {
        if (domain.isBlank() || isPassiveMode.value || _isScanning.value) return
        val target = CloudBucketPlanner.normalizeDomain(domain)
        if (target == null) {
            _uiState.value = CloudScanResult(isFinished = true, error = "Enter a domain such as example.com.")
            return
        }

        val taskName = "Cloud Discovery ($target)"
        val options = CloudScanOptions(_providers.value, CloudBucketPlanner.parseKeywords(extraKeywords))
        _isScanning.value = true
        _recordedFindings.value = 0
        _uiState.value = CloudScanResult()
        ActiveTaskMonitor.addTask(taskName)

        scanJob = viewModelScope.launch {
            try {
                repository.scanAssets(target, options).collect { result ->
                    _uiState.value = result
                    if (result.isFinished && result.error == null) {
                        val drafts = result.discoveredAssets.mapNotNull { it.draft }
                        if (drafts.isNotEmpty()) {
                            findingRepository?.record(drafts)
                            _recordedFindings.value = drafts.size
                        }
                        logRepository.saveLog(
                            type = "WEB_CLOUD",
                            summary = "Cloud asset scan for $target (${result.discoveredAssets.size} found, " +
                                "${result.discoveredAssets.count { it.isPublic }} public)",
                            detailJson = gson.toJson(mapOf("domain" to target, "assets" to result.discoveredAssets))
                        )
                    }
                }
            } finally {
                _isScanning.value = false
                ActiveTaskMonitor.removeTask(taskName)
            }
        }
    }

    /** Stops the scan and keeps whatever was found so far on screen. */
    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.update { if (it.total > 0 && !it.isFinished) it.copy(isFinished = true) else it }
    }
}
