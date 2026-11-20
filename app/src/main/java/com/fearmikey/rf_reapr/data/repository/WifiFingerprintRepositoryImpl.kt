package com.fearmikey.rf_reapr.data.repository

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.fearmikey.rf_reapr.domain.model.WifiAccessPoint
import com.fearmikey.rf_reapr.domain.repository.WifiFingerprintRepository
import com.fearmikey.rf_reapr.domain.repository.WifiScanStatus
import com.fearmikey.rf_reapr.domain.service.DeviceIdentificationService
import com.fearmikey.rf_reapr.domain.service.WifiSecurityAnalyzer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WifiFingerprintRepositoryImpl(context: Context) : WifiFingerprintRepository {

    private val appContext = context.applicationContext
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val _status = MutableStateFlow(WifiScanStatus())
    override val status: StateFlow<WifiScanStatus> = _status.asStateFlow()

    @SuppressLint("MissingPermission")
    override fun startWifiScan(): Flow<List<WifiAccessPoint>> = callbackFlow {
        fun publish() {
            val results = runCatching { wifiManager.scanResults }.getOrDefault(emptyList())
            trySend(map(results))
            _status.value = _status.value.copy(lastResultsAt = System.currentTimeMillis(), resultCount = results.size)
        }

        val wifiScanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // Even when EXTRA_RESULTS_UPDATED is false (throttled), the cached results are
                // still worth showing.
                publish()
            }
        }
        ContextCompat.registerReceiver(
            appContext, wifiScanReceiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
            ContextCompat.RECEIVER_EXPORTED
        )

        _status.value = WifiScanStatus(wifiEnabled = wifiManager.isWifiEnabled)
        // Show whatever the system already has right away.
        publish()

        // Android allows 4 scans per 2 minutes in the foreground; request one every 30 s.
        val ticker = launch {
            while (isActive) {
                @Suppress("DEPRECATION")
                val accepted = runCatching { wifiManager.startScan() }.getOrDefault(false)
                _status.value = _status.value.copy(
                    wifiEnabled = wifiManager.isWifiEnabled,
                    lastRequestAccepted = accepted,
                    lastRequestAt = System.currentTimeMillis()
                )
                delay(SCAN_INTERVAL_MS)
            }
        }

        awaitClose {
            ticker.cancel()
            runCatching { appContext.unregisterReceiver(wifiScanReceiver) }
        }
    }

    override fun stopWifiScan() = Unit

    @SuppressLint("MissingPermission")
    private fun map(results: List<ScanResult>): List<WifiAccessPoint> {
        @Suppress("DEPRECATION")
        val connectedBssid = runCatching { wifiManager.connectionInfo?.bssid?.lowercase() }.getOrNull()
        val base = results.distinctBy { it.BSSID }.map { r ->
            @Suppress("DEPRECATION")
            val ssid = r.SSID.orEmpty()
            WifiAccessPoint(
                ssid = ssid,
                bssid = r.BSSID,
                signalLevel = r.level,
                frequency = r.frequency,
                bandwidth = when (r.channelWidth) {
                    ScanResult.CHANNEL_WIDTH_20MHZ -> 20
                    ScanResult.CHANNEL_WIDTH_40MHZ -> 40
                    ScanResult.CHANNEL_WIDTH_80MHZ -> 80
                    ScanResult.CHANNEL_WIDTH_160MHZ, ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 160
                    5 -> 320 // CHANNEL_WIDTH_320MHZ (API 33)
                    else -> 20
                },
                capabilities = r.capabilities.orEmpty(),
                vendor = DeviceIdentificationService.getManufacturer(r.BSSID),
                standard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) standardName(r.wifiStandard) else null,
                isConnected = connectedBssid != null && r.BSSID.equals(connectedBssid, ignoreCase = true)
            )
        }
        val rogues = WifiSecurityAnalyzer.findRogues(base)
        return base.map { ap ->
            val reason = rogues[ap.bssid]
            if (reason != null) ap.copy(isRogueSuspect = true, rogueReason = reason) else ap
        }
    }

    private fun standardName(standard: Int): String? = when (standard) {
        1 -> "Legacy (a/b/g)"
        4 -> "Wi-Fi 4 (n)"
        5 -> "Wi-Fi 5 (ac)"
        6 -> "Wi-Fi 6 (ax)"
        7 -> "WiGig (ad)"
        8 -> "Wi-Fi 7 (be)"
        else -> null
    }

    private companion object {
        const val SCAN_INTERVAL_MS = 30_000L
    }
}
