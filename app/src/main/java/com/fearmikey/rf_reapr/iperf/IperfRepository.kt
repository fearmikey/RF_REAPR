package com.fearmikey.rf_reapr.iperf

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface

data class NetworkInterfaceInfo(
    val name: String,
    val displayName: String,
    val ipv4Address: String?
) {
    val displayLabel: String
        get() = "$name ${ipv4Address?.let { "($it)" } ?: ""}"
}

data class IperfConfig(
    val serverHost: String = "",
    val port: Int = DEFAULT_PORT,
    val isServerMode: Boolean = false,
    val durationSeconds: Int = 10,
    val parallelStreams: Int = 1,
    /** -R: server sends, client receives (measures download). */
    val reverse: Boolean = false,
    val udp: Boolean = false,
    /** Target bitrate for UDP tests (-b); TCP is unthrottled. */
    val udpBitrateMbps: Int = 100,
    val bindInterfaceIp: String? = null
) {
    companion object {
        const val DEFAULT_PORT = 5201
    }

    /** iperf3 arguments. Always requests JSON (-J) so results can be parsed and charted. */
    fun toArgs(): List<String> = buildList {
        if (isServerMode) {
            add("-s")
            add("-1") // Run for one test then exit
        } else {
            add("-c"); add(serverHost)
            add("-t"); add(durationSeconds.coerceIn(1, 3600).toString())
            add("-P"); add(parallelStreams.coerceIn(1, 128).toString())
            if (reverse) add("-R")
            if (udp) {
                add("-u")
                add("-b"); add("${udpBitrateMbps.coerceAtLeast(1)}M")
            }
            add("--connect-timeout"); add("5000")
        }
        if (port != DEFAULT_PORT) {
            add("-p"); add(port.coerceIn(1, 65535).toString())
        }
        // Force the traffic out of the specific interface (e.g. Ethernet Dongle)
        if (!bindInterfaceIp.isNullOrBlank()) {
            add("-B"); add(bindInterfaceIp)
        }
        add("-J")
    }
}

class IperfRepository {

    /**
     * Retrieves a list of active network interfaces and their IPv4 addresses.
     * This is useful for identifying the Ethernet dongle (usually eth0) to bind the iperf test to.
     */
    suspend fun getAvailableNetworkInterfaces(): List<NetworkInterfaceInfo> = withContext(Dispatchers.IO) {
        val interfaces = mutableListOf<NetworkInterfaceInfo>()
        try {
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces.hasMoreElements()) {
                val ni = networkInterfaces.nextElement()
                if (ni.isUp && !ni.isLoopback && !isCellularInterface(ni.name)) {
                    var ipAddress: String? = null
                    val addresses = ni.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (addr is Inet4Address) {
                            ipAddress = addr.hostAddress
                            break // Prefer the first IPv4 address
                        }
                    }
                    if (ipAddress != null) {
                        interfaces.add(
                            NetworkInterfaceInfo(
                                name = ni.name,
                                displayName = ni.displayName,
                                ipv4Address = ipAddress
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        interfaces.toList()
    }

    /**
     * Executes the iPerf test via the native JNI bridge (blocking until it completes or
     * [stopTest] is called) and returns iperf3's output, which is JSON because of -J.
     */
    suspend fun runTest(config: IperfConfig): String = withContext(Dispatchers.IO) {
        try {
            IperfNative.runIperfCommand(config.toArgs().toTypedArray())
        } catch (e: Throwable) {
            // UnsatisfiedLinkError if the native library failed to load.
            "Error executing iPerf: ${e.message}"
        }
    }

    /**
     * Forcefully stops any currently running iPerf test.
     */
    fun stopTest() {
        runCatching { IperfNative.stopIperf() }
    }

    /**
     * Identifies interfaces backed by the cellular radio so they can be excluded from the
     * bindable interface list. iPerf tests should only run over Ethernet/Wi-Fi adapters.
     */
    private fun isCellularInterface(name: String): Boolean {
        val lower = name.lowercase()
        return CELLULAR_INTERFACE_PREFIXES.any { lower.startsWith(it) }
    }

    companion object {
        // Common cellular/mobile-data interface name prefixes across Android chipsets/vendors.
        private val CELLULAR_INTERFACE_PREFIXES = listOf(
            "rmnet",   // Qualcomm
            "ccmni",   // MediaTek
            "ccemni",  // MediaTek (variant)
            "pdp",     // Older basebands
            "cellular",
            "clat"     // 464xlat, layered on top of cellular
        )
    }
}
