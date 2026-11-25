package com.fearmikey.rf_reapr.data.survey

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** A Wi-Fi scan result reduced to what the survey tools store. */
data class WifiReading(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequencyMhz: Int,
    val capabilities: String,
    /** How old the reading was when it was read, in milliseconds. */
    val ageMs: Long
)

/**
 * Direct, self-contained access to the platform Wi-Fi scanner for the survey tools, so they
 * don't disturb the Wi-Fi Fingerprinter's own scan loop.
 */
class WifiScanSource(context: Context) {

    private val appContext = context.applicationContext
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    val isWifiEnabled: Boolean get() = wifiManager.isWifiEnabled

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Asks the platform for a scan. False when Android throttled the request (4 per 2 minutes). */
    @Suppress("DEPRECATION")
    fun requestScan(): Boolean = runCatching { wifiManager.startScan() }.getOrDefault(false)

    /** The platform's latest results (possibly cached), newest readings included. */
    @SuppressLint("MissingPermission")
    fun currentReadings(): List<WifiReading> {
        if (!hasPermission()) return emptyList()
        val now = SystemClock.elapsedRealtime()
        return runCatching { wifiManager.scanResults }.getOrDefault(emptyList()).mapNotNull { it.toReading(now) }
    }

    /** Emits the result list each time the platform finishes a scan (ours or anyone else's). */
    fun scanResults(): Flow<List<WifiReading>> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(currentReadings())
            }
        }
        ContextCompat.registerReceiver(
            appContext, receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        awaitClose { runCatching { appContext.unregisterReceiver(receiver) } }
    }

    /**
     * Requests a scan and waits for its results. If the request is throttled, falls back to
     * cached results; the caller decides from [WifiReading.ageMs] whether they are usable.
     *
     * @return (readings, true if they came from a scan that completed after the request)
     */
    suspend fun freshScan(timeoutMs: Long = 9_000): Pair<List<WifiReading>, Boolean> {
        if (!requestScan()) return currentReadings() to false
        val results = withTimeoutOrNull(timeoutMs) { scanResults().first() }
        return if (results != null) results to true else currentReadings() to false
    }

    @Suppress("DEPRECATION")
    private fun ScanResult.toReading(nowElapsedMs: Long): WifiReading? {
        val bssid = BSSID ?: return null
        return WifiReading(
            ssid = SSID.orEmpty(),
            bssid = bssid,
            rssi = level,
            frequencyMhz = frequency,
            capabilities = capabilities.orEmpty(),
            // ScanResult.timestamp is microseconds since boot.
            ageMs = (nowElapsedMs - timestamp / 1000).coerceAtLeast(0)
        )
    }
}
