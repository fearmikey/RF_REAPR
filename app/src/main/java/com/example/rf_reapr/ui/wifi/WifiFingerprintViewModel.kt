package com.example.rf_reapr.ui.wifi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rf_reapr.domain.model.WifiAccessPoint
import com.example.rf_reapr.domain.repository.LogRepository
import com.example.rf_reapr.domain.repository.WifiFingerprintRepository
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
    private val logRepository: LogRepository
) : ViewModel() {

    enum class FrequencyRange {
        FREQ_2_4GHZ, FREQ_5GHZ, FREQ_6GHZ
    }

    private val _accessPoints = MutableStateFlow<List<WifiAccessPoint>>(emptyList())
    val accessPoints: StateFlow<List<WifiAccessPoint>> = _accessPoints

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _selectedRange = MutableStateFlow(FrequencyRange.FREQ_2_4GHZ)
    val selectedRange: StateFlow<FrequencyRange> = _selectedRange

    private val _hiddenBssids = MutableStateFlow<Set<String>>(emptySet())
    val hiddenBssids: StateFlow<Set<String>> = _hiddenBssids

    val filteredAccessPoints = combine(accessPoints, _selectedRange) { aps, range ->
        aps.filter { ap ->
            when (range) {
                FrequencyRange.FREQ_2_4GHZ -> ap.frequency in 2400..2500
                FrequencyRange.FREQ_5GHZ -> ap.frequency in 5000..5900
                FrequencyRange.FREQ_6GHZ -> ap.frequency in 5925..7125
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
                    jsonObject.get("detailJson").asString
                } else {
                    jsonToParse
                }
                val type = object : com.google.gson.reflect.TypeToken<List<WifiAccessPoint>>() {}.type
                gson.fromJson(detailJson, type)
            } else {
                val type = object : com.google.gson.reflect.TypeToken<List<WifiAccessPoint>>() {}.type
                gson.fromJson(jsonToParse, type)
            }
            
            if (importedAps != null) {
                android.util.Log.d("WifiViewModel", "Successfully parsed ${importedAps.size} APs")
                _accessPoints.value = importedAps
            }
        } catch (e: Exception) {
            android.util.Log.e("WifiViewModel", "Failed to parse WiFi data", e)
        }
    }

    fun startScan() {
        stopScan()
        _isScanning.value = true
        scanJob = viewModelScope.launch {
            repository.startWifiScan().collect {
                _accessPoints.value = it
            }
        }
    }

    fun stopScan() {
        if (_isScanning.value) {
            _isScanning.value = false
            scanJob?.cancel()
            repository.stopWifiScan()
            
            // Log the results
            val aps = _accessPoints.value
            if (aps.isNotEmpty()) {
                viewModelScope.launch {
                    logRepository.saveLog(
                        type = "WIFI",
                        summary = "Scanned ${aps.size} WiFi access points",
                        detailJson = gson.toJson(aps)
                    )
                }
            }
        }
    }
}
