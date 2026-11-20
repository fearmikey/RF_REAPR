package com.fearmikey.rf_reapr.ui.wifi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.model.WifiAccessPoint
import com.fearmikey.rf_reapr.domain.repository.LogRepository
import com.fearmikey.rf_reapr.domain.repository.WifiFingerprintRepository
import com.fearmikey.rf_reapr.domain.repository.WifiScanStatus
import com.fearmikey.rf_reapr.domain.service.WifiBand
import com.fearmikey.rf_reapr.domain.service.WifiSecurityAnalyzer
import com.google.gson.Gson
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WifiFingerprintViewModel(
    private val repository: WifiFingerprintRepository,
    private val logRepository: LogRepository,
) : ViewModel() {

    enum class FrequencyRange(val band: WifiBand) {
        FREQ_2_4GHZ(WifiBand.GHZ_2_4), FREQ_5GHZ(WifiBand.GHZ_5), FREQ_6GHZ(WifiBand.GHZ_6)
    }

    private val _accessPoints = MutableStateFlow<List<WifiAccessPoint>>(emptyList())
    val accessPoints: StateFlow<List<WifiAccessPoint>> = _accessPoints

    private val _isScanning = MutableStateFlow(value = false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _selectedRange = MutableStateFlow(FrequencyRange.FREQ_2_4GHZ)
    val selectedRange: StateFlow<FrequencyRange> = _selectedRange

    private val _hiddenBssids = MutableStateFlow<Set<String>>(emptySet())
    val hiddenBssids: StateFlow<Set<String>> = _hiddenBssids

    // When enabled (default), WifiChannelGraph collapses duplicate/mesh SSIDs and caps the
    // number of fully-labeled signals so dense environments stay readable.
    private val _simplifyGraph = MutableStateFlow(true)
    val simplifyGraph: StateFlow<Boolean> = _simplifyGraph

    fun toggleSimplifyGraph() {
        _simplifyGraph.value = !_simplifyGraph.value
    }

    val scanStatus: StateFlow<WifiScanStatus> = repository.status

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query
    fun setQuery(q: String) { _query.value = q }

    val filteredAccessPoints = combine(accessPoints, _selectedRange, _query) { aps, range, q ->
        aps.filter { ap ->
            WifiSecurityAnalyzer.band(ap.frequency) == range.band &&
                (q.isBlank() || ap.ssid.contains(q, true) || ap.bssid.contains(q, true) || ap.vendor?.contains(q, true) == true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Least congested channel for the selected band. */
    val channelAdvice = combine(accessPoints, _selectedRange) { aps, range ->
        WifiSecurityAnalyzer.recommendChannel(aps, range.band)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setSelectedRange(range: FrequencyRange) {
        _selectedRange.value = range
    }

    fun toggleBssidVisibility(bssid: String) {
        val current = _hiddenBssids.value
        if (current.contains(bssid)) {
            _hiddenBssids.value = current - bssid
        } else {
            _hiddenBssids.value = current + bssid
        }
    }

    private var scanJob: Job? = null
    private val gson = Gson()
    private val sessionAps = LinkedHashMap<String, WifiAccessPoint>()

    fun setImportedData(jsonData: String) {
        stopScan()
        try {
            android.util.Log.d("WifiViewModel", "Importing data length: ${jsonData.length}")
            
            // The input might be a plain text log with a JSON block after "Details: "
            val jsonToParse = when {
                jsonData.contains("Details: ") -> {
                    jsonData.substringAfter("Details: ").trim()
                }
                jsonData.trim().startsWith("[") -> {
                    jsonData.trim()
                }
                jsonData.contains("[") -> {
                    // Try to find the start of a JSON array
                    jsonData.substring(jsonData.indexOf("[")).trim()
                }
                else -> jsonData.trim()
            }

            android.util.Log.d("WifiViewModel", "Extracted JSON: ${jsonToParse.take(100)}...")

            // Now handle the extracted JSON
            val importedAps: List<WifiAccessPoint> = if (jsonToParse.startsWith("{")) {
                val jsonObject = com.google.gson.JsonParser.parseString(jsonToParse).asJsonObject
                val detailJson = if (jsonObject.has("detailJson")) {
                    jsonObject["detailJson"].asString
                } else {
                    jsonToParse
                }
                val type = object : com.google.gson.reflect.TypeToken<List<WifiAccessPoint>>() {}.type
                gson.fromJson(detailJson, type)
            } else {
                val type = object : com.google.gson.reflect.TypeToken<List<WifiAccessPoint>>() {}.type
                gson.fromJson(jsonToParse, type)
            }
            
            android.util.Log.d("WifiViewModel", "Successfully parsed ${importedAps.size} APs")
            _accessPoints.value = importedAps
        } catch (e: Exception) {
            android.util.Log.e("WifiViewModel", "Failed to parse WiFi data", e)
        }
    }

    fun startScan() {
        stopScan()
        _isScanning.value = true
        scanJob = viewModelScope.launch {
            repository.startWifiScan().collect { latest ->
                _accessPoints.value = latest
                // Keep the strongest reading per BSSID for the session log.
                latest.forEach { ap ->
                    val prev = sessionAps[ap.bssid]
                    if (prev == null || ap.signalLevel > prev.signalLevel) sessionAps[ap.bssid] = ap
                }
            }
        }
    }

    fun stopScan() {
        if (_isScanning.value) {
            _isScanning.value = false
            scanJob?.cancel()
            repository.stopWifiScan()
            
            // Log every AP seen during the session (not just the last snapshot).
            val aps = sessionAps.values.sortedByDescending { it.signalLevel }.ifEmpty { _accessPoints.value }
            sessionAps.clear()
            if (aps.isNotEmpty()) {
                val rogues = aps.count { it.isRogueSuspect }
                val weak = aps.count { WifiSecurityAnalyzer.analyze(it.capabilities).risk >= com.fearmikey.rf_reapr.domain.model.RiskLevel.HIGH }
                viewModelScope.launch {
                    logRepository.saveLog(
                        type = "WIFI",
                        summary = "Scanned ${aps.size} WiFi access points" +
                            (if (rogues > 0) ", $rogues suspected evil twins" else "") +
                            (if (weak > 0) ", $weak open/WEP" else ""),
                        detailJson = gson.toJson(aps)
                    )
                }
            }
        }
    }
}
