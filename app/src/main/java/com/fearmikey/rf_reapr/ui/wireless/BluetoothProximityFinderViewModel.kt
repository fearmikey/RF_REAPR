package com.fearmikey.rf_reapr.ui.wireless

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.model.BleDevice
import com.fearmikey.rf_reapr.domain.repository.BleProximityRepository
import com.fearmikey.rf_reapr.domain.repository.BleScannerRepository
import com.fearmikey.rf_reapr.domain.repository.LogRepository
import com.fearmikey.rf_reapr.domain.scanner.NetworkScanner
import com.fearmikey.rf_reapr.domain.service.BleAdvertisementAnalyzer
import com.fearmikey.rf_reapr.domain.service.NetworkAlertNotifier
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class BleSort(val label: String) { SIGNAL("Signal"), NAME("Name"), RECENT("First seen") }

enum class TrackTrend { CLOSER, FARTHER, STEADY }

class BluetoothProximityFinderViewModel(
    private val proximityRepository: BleProximityRepository,
    private val scannerRepository: BleScannerRepository,
    private val logRepository: LogRepository? = null,
    private val notifier: NetworkAlertNotifier? = null
) : ViewModel() {

    private val _targetAddress = MutableStateFlow<String?>(null)
    val targetAddress = _targetAddress.asStateFlow()

    private val _targetName = MutableStateFlow<String?>(null)
    val targetName = _targetName.asStateFlow()

    private val _currentRssi = MutableStateFlow<Int?>(null)
    val currentRssi = _currentRssi.asStateFlow()

    private val _rssiHistory = MutableStateFlow<List<Int>>(emptyList())
    val rssiHistory = _rssiHistory.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking = _isTracking.asStateFlow()

    private val _isDiscoveryActive = MutableStateFlow(false)
    val isDiscoveryActive = _isDiscoveryActive.asStateFlow()

    private val _isActiveMode = MutableStateFlow(false)
    val isActiveMode = _isActiveMode.asStateFlow()

    private val _scanError = MutableStateFlow<String?>(null)
    val scanError = _scanError.asStateFlow()

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _sort = MutableStateFlow(BleSort.SIGNAL)
    val sort = _sort.asStateFlow()

    private val _trackersOnly = MutableStateFlow(false)
    val trackersOnly = _trackersOnly.asStateFlow()

    /** Milliseconds since the target was last heard, or null when tracking isn't running. */
    private val _targetSilenceMs = MutableStateFlow<Long?>(null)
    val targetSilenceMs = _targetSilenceMs.asStateFlow()

    private val _trend = MutableStateFlow(TrackTrend.STEADY)
    val trend = _trend.asStateFlow()

    private val _vibrationEnabled = MutableStateFlow(true)
    val vibrationEnabled = _vibrationEnabled.asStateFlow()

    private val _allDevices = MutableStateFlow<List<BleDevice>>(emptyList())

    /** Every device seen this session (survives aging-out in the live list) for the log. */
    private val sessionDevices = LinkedHashMap<String, BleDevice>()
    private val notifiedFollowers = HashSet<String>()

    val recentlySeen: StateFlow<List<BleDevice>> = combine(_allDevices, _query, _sort, _trackersOnly) { devices, q, sort, trackers ->
        devices
            .filter { !trackers || it.isTracker }
            .filter { q.isBlank() || it.name?.contains(q, true) == true || it.address.contains(q, true) || it.manufacturer?.contains(q, true) == true }
            .let { list ->
                when (sort) {
                    BleSort.SIGNAL -> list
                    BleSort.NAME -> list.sortedBy { it.name?.lowercase() ?: "~" }
                    BleSort.RECENT -> list.sortedByDescending { it.firstSeen }
                }
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Trackers seen continuously for 10+ minutes. */
    val possibleFollowers: StateFlow<List<BleDevice>> = _allDevices.map { list ->
        val now = System.currentTimeMillis()
        list.filter { BleAdvertisementAnalyzer.isPossiblyFollowing(it.category, it.firstSeen, it.lastSeen, now) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var trackingJob: Job? = null
    private var discoveryJob: Job? = null
    private var feedbackJob: Job? = null
    private var watchdogJob: Job? = null
    private var discoveryStartedAt = 0L

    private var smoothedRssi: Float? = null
    private val alpha = 0.3f // EMA smoothing factor; lower is smoother.
    private var lastPacketAt = 0L
    private val gson = Gson()

    init {
        viewModelScope.launch {
            scannerRepository.getDiscoveredDevices().collect { devices ->
                _allDevices.value = devices
                devices.forEach { d ->
                    val prev = sessionDevices[d.address]
                    sessionDevices[d.address] = if (prev == null) d else d.copy(firstSeen = minOf(prev.firstSeen, d.firstSeen))
                }
                checkFollowers(devices)
            }
        }
    }

    private fun checkFollowers(devices: List<BleDevice>) {
        val now = System.currentTimeMillis()
        devices.filter { BleAdvertisementAnalyzer.isPossiblyFollowing(it.category, it.firstSeen, it.lastSeen, now) && notifiedFollowers.add(it.address) }
            .forEach { d ->
                val minutes = (d.lastSeen - d.firstSeen) / 60_000
                notifier?.notifySecurityAlert(
                    "Tracker nearby for $minutes min",
                    "${d.category.label} (${d.address}) has been near you for $minutes minutes. If you don't own it, use the Proximity Finder to locate it.",
                    key = "ble:${d.address}"
                )
            }
    }

    fun setQuery(q: String) { _query.value = q }
    fun setSort(s: BleSort) { _sort.value = s }
    fun setTrackersOnly(v: Boolean) { _trackersOnly.value = v }
    fun setVibrationEnabled(v: Boolean) { _vibrationEnabled.value = v }

    fun toggleActiveMode(active: Boolean) {
        _isActiveMode.value = active
        if (_isDiscoveryActive.value) startDiscovery()
    }

    fun startDiscovery() {
        stopDiscovery(log = false)
        _isDiscoveryActive.value = true
        _scanError.value = null
        if (discoveryStartedAt == 0L) discoveryStartedAt = System.currentTimeMillis()
        discoveryJob = viewModelScope.launch {
            scannerRepository.startScan(_isActiveMode.value)
                .catch { e -> _scanError.value = e.message ?: "Scan failed" }
                .collect { result ->
                    if (result is NetworkScanner.ScanResult.Error) {
                        _scanError.value = result.message
                        _isDiscoveryActive.value = false
                    }
                }
        }
    }

    fun stopDiscovery(log: Boolean = true) {
        discoveryJob?.cancel()
        discoveryJob = null
        _isDiscoveryActive.value = false
        if (log) logSession()
    }

    /** Writes a BLE log entry (read by the Bluetooth Logs screen and the report builder). */
    private fun logSession() {
        val logger = logRepository ?: return
        if (sessionDevices.isEmpty()) return
        val devices = sessionDevices.values.sortedByDescending { it.rssi }
        val trackers = devices.count { it.isTracker }
        val durationSec = if (discoveryStartedAt > 0) (System.currentTimeMillis() - discoveryStartedAt) / 1000 else 0
        sessionDevices.clear()
        discoveryStartedAt = 0
        // Detached scope: this also runs from onCleared(), after viewModelScope is cancelled.
        CoroutineScope(Dispatchers.IO).launch {
            logger.saveLog(
                type = "BLE",
                summary = "Bluetooth scan: ${devices.size} devices" + (if (trackers > 0) ", $trackers trackers" else "") + " (${durationSec}s)",
                detailJson = gson.toJson(devices)
            )
        }
    }

    fun setTarget(address: String, name: String? = null) {
        if (address.isBlank()) {
            _targetAddress.value = null
            _targetName.value = null
            stopTracking()
            startDiscovery()
            return
        }
        _targetAddress.value = address.trim().uppercase()
        _targetName.value = name
        stopDiscovery(log = false)
        // Start tracking right away; that's why the user picked a device.
        startTracking()
    }

    fun toggleTracking() {
        if (_isTracking.value) stopTracking() else startTracking()
    }

    private fun startTracking() {
        val address = _targetAddress.value ?: return
        stopTracking()
        _isTracking.value = true
        lastPacketAt = System.currentTimeMillis()

        trackingJob = viewModelScope.launch {
            proximityRepository.trackDevice(address)
                .catch { e -> _scanError.value = e.message ?: "Tracking failed" }
                .collect { rssi ->
                    lastPacketAt = System.currentTimeMillis()
                    val prev = smoothedRssi
                    val newSmoothed = if (prev == null) rssi.toFloat() else prev + alpha * (rssi - prev)
                    smoothedRssi = newSmoothed
                    val finalRssi = newSmoothed.toInt()
                    _currentRssi.value = finalRssi
                    _rssiHistory.update { (it + finalRssi).takeLast(60) }
                    updateTrend()
                }
        }

        // Detects a lost target (no advertisements) so the UI can say so instead of freezing.
        watchdogJob = viewModelScope.launch {
            while (isActive) {
                _targetSilenceMs.value = System.currentTimeMillis() - lastPacketAt
                delay(500)
            }
        }

        // Haptic feedback: faster pulses as the signal gets stronger.
        feedbackJob = viewModelScope.launch {
            while (isActive) {
                val rssi = _currentRssi.value
                val silent = (_targetSilenceMs.value ?: 0) > LOST_AFTER_MS
                if (rssi != null && !silent && _vibrationEnabled.value) {
                    val clamped = rssi.coerceIn(-100, -30)
                    val delayMs = ((clamped + 100) * (100 - 1000) / 70 + 1000).toLong()
                    _vibrationTrigger.emit(Unit)
                    delay(delayMs.coerceAtLeast(100))
                } else {
                    delay(1000)
                }
            }
        }
    }

    private fun updateTrend() {
        val h = _rssiHistory.value
        if (h.size < 10) return
        val recent = h.takeLast(5).average()
        val earlier = h.takeLast(10).take(5).average()
        _trend.value = when {
            recent - earlier >= 3 -> TrackTrend.CLOSER
            earlier - recent >= 3 -> TrackTrend.FARTHER
            else -> TrackTrend.STEADY
        }
    }

    private fun stopTracking() {
        trackingJob?.cancel()
        feedbackJob?.cancel()
        watchdogJob?.cancel()
        _isTracking.value = false
        _currentRssi.value = null
        _rssiHistory.value = emptyList()
        _targetSilenceMs.value = null
        _trend.value = TrackTrend.STEADY
        smoothedRssi = null
    }

    /** TX power for the current target if we saw it during discovery (improves distance). */
    fun targetTxPower(): Int? = _targetAddress.value?.let { addr -> sessionDevices[addr]?.txPower ?: _allDevices.value.firstOrNull { it.address == addr }?.txPower }

    private val _vibrationTrigger = MutableSharedFlow<Unit>()
    val vibrationTrigger = _vibrationTrigger.asSharedFlow()

    override fun onCleared() {
        stopTracking()
        stopDiscovery()
        super.onCleared()
    }

    companion object {
        const val LOST_AFTER_MS = 5_000L
    }
}
