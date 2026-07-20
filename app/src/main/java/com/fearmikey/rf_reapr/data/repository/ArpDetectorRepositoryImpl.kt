package com.fearmikey.rf_reapr.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.fearmikey.rf_reapr.domain.repository.AlertSeverity
import com.fearmikey.rf_reapr.domain.repository.ArpAlert
import com.fearmikey.rf_reapr.domain.repository.ArpDetectorRepository
import com.fearmikey.rf_reapr.domain.repository.GatewayInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

class ArpDetectorRepositoryImpl(private val context: Context) : ArpDetectorRepository {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val _gatewayInfo = MutableStateFlow(GatewayInfo("", "", "Unknown"))
    override val gatewayInfo: StateFlow<GatewayInfo> = _gatewayInfo.asStateFlow()

    private val _alerts = MutableStateFlow<List<ArpAlert>>(emptyList())
    override val alerts: StateFlow<List<ArpAlert>> = _alerts.asStateFlow()

    private var monitoringJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun startMonitoring() {
        if (monitoringJob != null) return
        
        monitoringJob = scope.launch {
            while (isActive) {
                checkArpStatus()
                delay(5000) // Check every 5 seconds
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

    private suspend fun checkArpStatus() {
        val activeNetwork = connectivityManager.activeNetwork ?: return
        val linkProperties: LinkProperties? = connectivityManager.getLinkProperties(activeNetwork)
        val gatewayIp = linkProperties?.routes?.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress ?: return
        
        val ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            val wifiInfo = capabilities?.transportInfo as? WifiInfo
            wifiInfo?.ssid?.removeSurrounding("\"") ?: "Unknown"
        } else {
            @Suppress("DEPRECATION")
            wifiManager.connectionInfo.ssid.removeSurrounding("\"")
        }
        
        val currentMac = getMacFromArpTable(gatewayIp) ?: "Unknown"

        val previousInfo = _gatewayInfo.value
        
        if (previousInfo.ipAddress == gatewayIp && previousInfo.ssid == ssid) {
            if (previousInfo.macAddress.isNotEmpty() && previousInfo.macAddress != "Unknown" && 
                currentMac != "Unknown" && currentMac != previousInfo.macAddress) {
                
                addAlert(
                    ArpAlert(
                        timestamp = System.currentTimeMillis(),
                        message = "Gateway MAC address changed!",
                        oldMac = previousInfo.macAddress,
                        newMac = currentMac,
                        severity = AlertSeverity.CRITICAL
                    )
                )
            }
        }

        _gatewayInfo.value = GatewayInfo(
            ipAddress = gatewayIp,
            macAddress = currentMac,
            ssid = ssid,
            isSecure = currentMac != "Unknown"
        )
    }

    private fun addAlert(alert: ArpAlert) {
        val currentList = _alerts.value.toMutableList()
        currentList.add(0, alert)
        _alerts.value = currentList
    }

    private fun getMacFromArpTable(ip: String): String? {
        return try {
            val file = File("/proc/net/arp")
            if (!file.exists()) return null
            
            file.bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val columns = line.split(Regex("\\s+")).filter { it.isNotBlank() }
                    if (columns.size >= 4 && columns[0] == ip) {
                        val mac = columns[3]
                        if (mac != "00:00:00:00:00:00") {
                            return mac
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
