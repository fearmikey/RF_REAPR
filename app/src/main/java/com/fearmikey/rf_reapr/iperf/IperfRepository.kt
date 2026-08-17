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
                if (ni.isUp && !ni.isLoopback) {
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
     * @param serverIp The target iperf3 server IP.
     * @param bindInterfaceIp The IP address of the local interface to bind to (forces traffic over specific adapter).
     * @param durationSeconds The duration of the test.
     */
    suspend fun runTest(
        serverIp: String,
        bindInterfaceIp: String?,
        durationSeconds: Int = 10
    ): String = withContext(Dispatchers.IO) {
        val args = mutableListOf("-c", serverIp, "-t", durationSeconds.toString())
        
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
}