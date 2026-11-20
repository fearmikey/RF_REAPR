package com.fearmikey.rf_reapr.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.fearmikey.rf_reapr.domain.repository.AlertSeverity
import com.fearmikey.rf_reapr.domain.repository.ArpAlert
import com.fearmikey.rf_reapr.domain.repository.ArpDetectorRepository
import com.fearmikey.rf_reapr.domain.repository.GatewayInfo
import com.fearmikey.rf_reapr.domain.service.ActiveTaskMonitor
import com.fearmikey.rf_reapr.domain.service.ArpMonitorEvaluator
import com.fearmikey.rf_reapr.domain.service.NetworkAlertNotifier
import com.fearmikey.rf_reapr.domain.service.NetworkSnapshot
import com.fearmikey.rf_reapr.system.NeighborTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.net.Inet4Address
import java.util.concurrent.TimeUnit

class ArpDetectorRepositoryImpl(
    context: Context,
    private val notifier: NetworkAlertNotifier? = null
) : ArpDetectorRepository {

    private val appContext = context.applicationContext
    private val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val _gatewayInfo = MutableStateFlow(GatewayInfo("", "", "Unknown"))
    override val gatewayInfo: StateFlow<GatewayInfo> = _gatewayInfo.asStateFlow()

    private val _alerts = MutableStateFlow<List<ArpAlert>>(emptyList())
    override val alerts: StateFlow<List<ArpAlert>> = _alerts.asStateFlow()

    private val _isMonitoring = MutableStateFlow(false)
    override val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private var monitoringJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var previous: NetworkSnapshot? = null
    private var lastFirstHopCheck = 0L
    private var lastFirstHop: String? = null

    override fun startMonitoring() {
        if (monitoringJob?.isActive == true) return
        previous = null
        lastFirstHopCheck = 0
        ActiveTaskMonitor.addTask(TASK)
        _isMonitoring.value = true
        monitoringJob = scope.launch {
            try {
                while (isActive) {
                    runCatching { checkOnce() }
                    delay(CHECK_INTERVAL_MS)
                }
            } finally {
                ActiveTaskMonitor.removeTask(TASK)
                _isMonitoring.value = false
            }
        }
    }

    override fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }

    override fun clearAlerts() {
        _alerts.value = emptyList()
    }

    private suspend fun checkOnce() {
        val network = connectivityManager.activeNetwork ?: return
        val link = connectivityManager.getLinkProperties(network) ?: return
        val caps = connectivityManager.getNetworkCapabilities(network)
        val gatewayIp = link.routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway?.hostAddress
            ?: link.routes.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress
        val dns = link.dnsServers.mapNotNull { it.hostAddress }

        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val wifiInfo: WifiInfo? = if (!isWifi) null else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            caps.transportInfo as? WifiInfo
        } else {
            @Suppress("DEPRECATION")
            wifiManager.connectionInfo
        }
        // Without location permission Android returns placeholders.
        val ssid = wifiInfo?.ssid?.removeSurrounding("\"")?.takeUnless { it == WifiManager.UNKNOWN_SSID }
        val bssid = wifiInfo?.bssid?.lowercase()?.takeUnless { it == "02:00:00:00:00:00" }

        val neighbors = NeighborTable.read()
        val gatewayMac = gatewayIp?.let { neighbors.entries[it] }

        val now = System.currentTimeMillis()
        val firstHop = if (gatewayIp != null && now - lastFirstHopCheck >= FIRST_HOP_INTERVAL_MS) {
            lastFirstHopCheck = now
            probeFirstHop().also { lastFirstHop = it }
        } else lastFirstHop

        val snapshot = NetworkSnapshot(
            ssid = ssid ?: if (isWifi) "Wi-Fi (name hidden)" else caps?.let { transportName(it) },
            bssid = bssid,
            gatewayIp = gatewayIp,
            gatewayMac = gatewayMac,
            dnsServers = dns,
            neighbors = neighbors.entries,
            firstHop = firstHop
        )
        val newAlerts = ArpMonitorEvaluator.evaluate(previous, snapshot, now)
        // Don't repeat an identical alert raised on the previous cycle.
        val fresh = newAlerts.filter { a -> _alerts.value.none { it.message == a.message && it.newMac == a.newMac && now - it.timestamp < 60_000 } }
        if (fresh.isNotEmpty()) {
            _alerts.update { (fresh + it).take(200) }
            fresh.filter { it.severity == AlertSeverity.CRITICAL }.forEach {
                notifier?.notifySecurityAlert("Possible MITM: ${it.message}", it.detail.ifBlank { "${it.oldMac} → ${it.newMac}" }, key = "arp:${it.message}")
            }
        }
        previous = snapshot

        _gatewayInfo.update { old ->
            GatewayInfo(
                ipAddress = gatewayIp ?: "",
                macAddress = gatewayMac ?: if (neighbors.available) "Not in ARP table" else "Unavailable",
                ssid = snapshot.ssid ?: "Unknown",
                isSecure = fresh.none { it.severity == AlertSeverity.CRITICAL },
                bssid = bssid,
                dnsServers = dns,
                arpTableAvailable = neighbors.available,
                neighborCount = neighbors.entries.size,
                firstHop = firstHop,
                lastCheck = now,
                checks = old.checks + 1
            )
        }
    }

    private fun transportName(caps: NetworkCapabilities) = when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
        else -> "Network"
    }

    /** Sends one TTL=1 ping toward the internet and returns whoever reports the TTL expiry. */
    private suspend fun probeFirstHop(): String? = runCatching {
        runInterruptible {
            val process = ProcessBuilder("ping", "-n", "-c", "1", "-t", "1", "-W", "2", FIRST_HOP_TARGET)
                .redirectErrorStream(true)
                .start()
            try {
                val output = process.inputStream.bufferedReader().readText()
                process.waitFor(3, TimeUnit.SECONDS)
                ArpMonitorEvaluator.parseFirstHop(output)
            } finally {
                process.destroy()
            }
        }
    }.getOrNull()

    private companion object {
        const val TASK = "ARP Spoofing Detector"
        const val CHECK_INTERVAL_MS = 5_000L
        const val FIRST_HOP_INTERVAL_MS = 30_000L
        const val FIRST_HOP_TARGET = "1.1.1.1"
    }
}
