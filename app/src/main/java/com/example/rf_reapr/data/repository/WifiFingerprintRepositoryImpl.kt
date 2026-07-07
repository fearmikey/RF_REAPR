package com.example.rf_reapr.data.repository

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import com.example.rf_reapr.domain.model.WifiAccessPoint
import com.example.rf_reapr.domain.repository.WifiFingerprintRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class WifiFingerprintRepositoryImpl(private val context: Context) : WifiFingerprintRepository {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    @SuppressLint("MissingPermission")
    override fun startWifiScan(): Flow<List<WifiAccessPoint>> = callbackFlow {
        val wifiScanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                if (success) {
                    val results = wifiManager.scanResults
                    val accessPoints = results.map {
                        @Suppress("DEPRECATION")
                        val ssid = it.SSID
                        WifiAccessPoint(
                            ssid = ssid,
                            bssid = it.BSSID,
                            signalLevel = it.level,
                            frequency = it.frequency,
                            bandwidth = when (it.channelWidth) {
                                android.net.wifi.ScanResult.CHANNEL_WIDTH_20MHZ -> 20
                                android.net.wifi.ScanResult.CHANNEL_WIDTH_40MHZ -> 40
                                android.net.wifi.ScanResult.CHANNEL_WIDTH_80MHZ -> 80
                                android.net.wifi.ScanResult.CHANNEL_WIDTH_160MHZ -> 160
                                android.net.wifi.ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 160
                                else -> 20 // Default to 20MHz
                            },
                            capabilities = it.capabilities,
                            isRogueSuspect = analyzeForRogue(ssid, it.BSSID, it.capabilities, results)
                        )
                    }
                    trySend(accessPoints)
                }
            }
        }

        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(wifiScanReceiver, intentFilter)

        // Initial scan
        @Suppress("DEPRECATION")
        wifiManager.startScan()

        awaitClose {
            context.unregisterReceiver(wifiScanReceiver)
        }
    }

    override fun stopWifiScan() {
        // Implementation for stopping specific jobs if needed
    }

    private fun analyzeForRogue(
        ssid: String,
        bssid: String,
        capabilities: String,
        allResults: List<android.net.wifi.ScanResult>
    ): Boolean {
        // Basic Evil Twin detection: Multiple BSSIDs with same SSID but different security/capabilities
        // or just flagging duplicate SSIDs in general as suspicious if they have widely different signal strengths
        @Suppress("DEPRECATION")
        val sameSsid = allResults.filter { it.SSID == ssid }
        if (sameSsid.size > 1) {
            // Check if security varies across same SSID
            val distinctCapabilities = sameSsid.map { it.capabilities }.distinct()
            if (distinctCapabilities.size > 1) return true
        }
        return false
    }
}
