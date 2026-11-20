package com.fearmikey.rf_reapr.ui.arp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.repository.AlertSeverity
import com.fearmikey.rf_reapr.domain.repository.ArpDetectorRepository
import com.fearmikey.rf_reapr.domain.repository.FindingRepository
import com.fearmikey.rf_reapr.domain.repository.LogRepository
import com.fearmikey.rf_reapr.domain.service.ActiveTaskMonitor
import com.fearmikey.rf_reapr.domain.service.FindingAdapters
import com.google.gson.Gson
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ArpDetectorViewModel(
    private val repository: ArpDetectorRepository,
    private val logRepository: LogRepository? = null,
    private val findingRepository: FindingRepository? = null
) : ViewModel() {

    val gatewayInfo = repository.gatewayInfo
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val alerts = repository.alerts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isMonitoring = repository.isMonitoring
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val gson = Gson()
    private val loggedAlerts = HashSet<String>()

    init {
        viewModelScope.launch { ActiveTaskMonitor.stopAllSignal.collect { stopMonitoring() } }
        // Persist warnings/critical alerts so they show up in reports even after the screen closes.
        viewModelScope.launch {
            repository.alerts.drop(1).collect { list ->
                val newAlerts = list.filter { it.severity != AlertSeverity.INFO && loggedAlerts.add("${it.timestamp}|${it.message}") }
                findingRepository?.record(newAlerts.mapNotNull { FindingAdapters.arp(gatewayInfo.value?.ipAddress, it) })
                val logger = logRepository ?: return@collect
                newAlerts.forEach { alert ->
                    logger.saveLog(
                        type = "TOPOLOGY",
                        summary = "ARP monitor: ${alert.message}",
                        detailJson = gson.toJson(mapOf("tool" to "ARP Spoofing Detector", "alert" to alert))
                    )
                }
            }
        }
    }

    fun startMonitoring() = repository.startMonitoring()

    fun stopMonitoring() = repository.stopMonitoring()

    fun clearAlerts() = repository.clearAlerts()

    override fun onCleared() {
        super.onCleared()
        repository.stopMonitoring()
    }
}
