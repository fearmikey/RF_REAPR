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
     * Executes the iPerf test via the native JNI bridge.
     * @param serverIp The target iperf3 server IP (only for client mode).
     * @param isServerMode Whether to run as a server (-s) or client (-c).
     * @param bindInterfaceIp The IP address of the local interface to bind to (forces traffic over specific adapter).
     * @param durationSeconds The duration of the test.
     */
    suspend fun runTest(
        serverIp: String,
        isServerMode: Boolean,
        bindInterfaceIp: String?,
        durationSeconds: Int = 10
    ): String = withContext(Dispatchers.IO) {
        val args = mutableListOf<String>()
        
        if (isServerMode) {
            args.add("-s")
            args.add("-1") // Run for one test then exit
        } else {
            args.add("-c")
            args.add(serverIp)
            args.add("-t")
            args.add(durationSeconds.toString())
            args.add("--connect-timeout")
            args.add("5000")
        }
        
        // Force the traffic out of the specific interface (e.g. Ethernet Dongle)
        if (!bindInterfaceIp.isNullOrBlank()) {
            args.add("-B")
            args.add(bindInterfaceIp)
        }
        
        try {
            IperfNative.runIperfCommand(args.toTypedArray())
        } catch (e: Exception) {
            "Error executing iPerf: ${e.message}"
        }
    }

    /**
     * Forcefully stops any currently running iPerf test.
     */
    fun stopTest() {
        IperfNative.stopIperf()
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