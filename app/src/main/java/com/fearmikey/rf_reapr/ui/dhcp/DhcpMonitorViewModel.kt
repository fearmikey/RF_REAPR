package com.fearmikey.rf_reapr.ui.dhcp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.model.MonitorStats
import com.fearmikey.rf_reapr.domain.model.MonitoredDevice
import com.fearmikey.rf_reapr.domain.model.NetworkEvent
import com.fearmikey.rf_reapr.domain.repository.DhcpMonitorRepository
import com.fearmikey.rf_reapr.domain.repository.DhcpMonitorRepository.MonitoringStatus
import com.fearmikey.rf_reapr.domain.repository.FindingRepository
import com.fearmikey.rf_reapr.domain.repository.KnownDeviceRepository
import com.fearmikey.rf_reapr.domain.repository.SettingsRepository
import com.fearmikey.rf_reapr.domain.service.KnownDeviceMatcher
import com.fearmikey.rf_reapr.domain.service.ObservedDevice
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import com.fearmikey.rf_reapr.domain.service.ActiveTaskMonitor
import com.fearmikey.rf_reapr.domain.service.NetworkAlertNotifier
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MonitorInterval(val label: String, val millis: Long) {
    SEC_30("30s", 30_000L),
    MIN_1("1m", 60_000L),
    MIN_5("5m", 300_000L),
    MIN_15("15m", 900_000L)
}

class DhcpMonitorViewModel(
    private val repository: DhcpMonitorRepository,
    settingsRepository: SettingsRepository,
    private val alertNotifier: NetworkAlertNotifier? = null,
    private val knownDevices: KnownDeviceRepository? = null,
    private val findingRepository: FindingRepository? = null
) : ViewModel() {

    /** MACs on the active project's known-device list; empty = the list isn't in use. */
    val knownMacs: StateFlow<Set<String>> =
        (knownDevices?.observeActive()?.map { KnownDeviceMatcher.macSet(it) } ?: flowOf(emptySet()))
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Unknown MACs already reported as findings, so every sweep doesn't re-record the same set. */
    private var reportedUnknown: Set<String> = emptySet()

    private fun MonitoredDevice.observed() = ObservedDevice(ipAddress, macAddress, hostname, manufacturer)

    fun markKnown(device: MonitoredDevice) = viewModelScope.launch {
        val added = knownDevices?.add(listOf(device.observed())) ?: 0
        _messages.trySend(if (added > 0) "${device.displayName} added to known devices" else "This device has no MAC address to match on")
    }

    fun unmarkKnown(device: MonitoredDevice) = viewModelScope.launch {
        device.macAddress?.let { knownDevices?.removeByMac(it) }
    }

    /** Adds every listed device that has a MAC to the known-device list. */
    fun markAllKnown() = viewModelScope.launch {
        val added = knownDevices?.add(devices.value.map { it.observed() }) ?: 0
        _messages.trySend(
            if (added > 0) "$added device${if (added == 1) "" else "s"} added to known devices"
            else "No new devices to add (devices without a MAC address can't be listed)"
        )
    }

    /** Records online devices that aren't on the known list as findings (only when the list is in use). */
    private fun reportUnknownDevices() {
        val known = knownMacs.value
        if (known.isEmpty() || findingRepository == null) return
        val online = devices.value.filter { it.isOnline }.map { it.observed() }
        val unknown = KnownDeviceMatcher.unknown(online, known).mapNotNull { KnownDeviceMatcher.normalize(it.mac) }.toSet()
        if (unknown == reportedUnknown) return
        reportedUnknown = unknown
        viewModelScope.launch { findingRepository.record(KnownDeviceMatcher.findings(online, known)) }
    }

    val isPassiveMode: StateFlow<Boolean> = settingsRepository.isPassiveMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Display state only: Idle / Scanning / Waiting / Error. Alerts go to [messages]. */
    private val _status = MutableStateFlow<MonitoringStatus>(MonitoringStatus.Idle)
    val status: StateFlow<MonitoringStatus> = _status.asStateFlow()

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _interval = MutableStateFlow(MonitorInterval.SEC_30)
    val interval: StateFlow<MonitorInterval> = _interval.asStateFlow()

    private val _notifyOnNewDevice = MutableStateFlow(true)
    val notifyOnNewDevice: StateFlow<Boolean> = _notifyOnNewDevice.asStateFlow()

    val devices: StateFlow<List<MonitoredDevice>> = repository.activeDevices
    val events: StateFlow<List<NetworkEvent>> = repository.events
    val stats: StateFlow<MonitorStats> = repository.stats

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private var monitorJob: Job? = null

    init {
        viewModelScope.launch {
            ActiveTaskMonitor.stopAllSignal.collect { stopMonitoring() }
        }
    }

    fun startMonitoring() {
        if (isPassiveMode.value || monitorJob?.isActive == true) return
        _isMonitoring.value = true
        ActiveTaskMonitor.addTask(TASK_NAME)
        monitorJob = viewModelScope.launch {
            try {
                repository.startMonitoring(_interval.value.millis).collect(::handleStatus)
            } finally {
                _isMonitoring.value = false
                _status.value = MonitoringStatus.Idle
                ActiveTaskMonitor.removeTask(TASK_NAME)
            }
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        repository.stopMonitoring()
    }

    /** Changes the sweep interval, restarting the monitor if it's running. */
    fun setInterval(interval: MonitorInterval) {
        if (_interval.value == interval) return
        _interval.value = interval
        if (monitorJob?.isActive == true) {
            viewModelScope.launch {
                monitorJob?.let {
                    it.cancel()
                    it.join()
                }
                startMonitoring()
            }
        }
    }

    fun setNotifyOnNewDevice(enabled: Boolean) {
        _notifyOnNewDevice.value = enabled
    }

    fun clearNotifications() {
        repository.acknowledgeNewDevices()
    }

    fun resetBaseline() {
        repository.resetBaseline()
        _messages.trySend(
            if (_isMonitoring.value) "Baseline reset; the next sweep becomes the new baseline"
            else "Baseline reset"
        )
    }

    private fun handleStatus(status: MonitoringStatus) {
        when (status) {
            is MonitoringStatus.DeviceDetected -> {
                _messages.trySend("${status.device.displayName} (${status.device.ipAddress}) ${status.reason}")
                if (_notifyOnNewDevice.value) alertNotifier?.notifyDeviceDetected(status.device, status.reason)
            }
            is MonitoringStatus.DeviceLeft -> Unit // shown in the events timeline
            is MonitoringStatus.Scanning -> {
                _status.value = status
                ActiveTaskMonitor.updateStatus("$TASK_NAME: sweeping ${(status.progress * 100).toInt()}%")
            }
            is MonitoringStatus.Waiting -> {
                _status.value = status
                val online = devices.value.count { it.isOnline }
                ActiveTaskMonitor.updateStatus("$TASK_NAME: $online devices online")
                reportUnknownDevices() // a sweep just finished
            }
            is MonitoringStatus.Error -> {
                _status.value = status
                _messages.trySend("Sweep failed: ${status.message}")
            }
            MonitoringStatus.Idle -> _status.value = status
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopMonitoring()
    }

    private companion object {
        const val TASK_NAME = "Network Monitor"
    }
}
