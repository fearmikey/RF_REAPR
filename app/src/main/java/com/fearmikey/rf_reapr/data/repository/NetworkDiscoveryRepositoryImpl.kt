package com.fearmikey.rf_reapr.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import com.fearmikey.rf_reapr.domain.model.NetworkNode
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.domain.repository.LocalNetworkInfo
import com.fearmikey.rf_reapr.domain.repository.NetworkDiscoveryRepository
import com.fearmikey.rf_reapr.domain.scanner.NetworkScanner
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

class NetworkDiscoveryRepositoryImpl(
    context: Context
) : NetworkDiscoveryRepository {

    private val appContext = context.applicationContext
    private var scanJob: Job? = null

    override suspend fun getLocalNetworkInfo(): LocalNetworkInfo = withContext(Dispatchers.IO) {
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val linkProperties: LinkProperties? = connectivityManager.getLinkProperties(activeNetwork)
        
        val linkAddress = linkProperties?.linkAddresses?.firstOrNull { 
            val addr = it.address
            addr is Inet4Address && !addr.isLoopbackAddress
        }
        
        if (linkAddress == null) {
            throw IllegalStateException("No valid non-loopback IPv4 network interface found")
        }

        val localIp = linkAddress.address.hostAddress ?: "127.0.0.1"
        val prefixLength = linkAddress.prefixLength
        
        // Gateway is harder to get reliably via LinkProperties sometimes, 
        // but often it's the first IP in the subnet.
        val gatewayIp = linkProperties.routes.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress
        
        LocalNetworkInfo(
            localIp = localIp,
            gatewayIp = gatewayIp,
            subnetMask = prefixToMask(prefixLength),
            prefixLength = prefixLength
        )
    }

    override fun discoverDevices(): Flow<NetworkScanner.ScanResult<NetworkNode>> = callbackFlow {
        val foundNodes = Collections.synchronizedList(mutableListOf<NetworkNode>())
        val scannedCount = AtomicInteger(0)
        
        val job = launch(Dispatchers.IO) {
            try {
                val networkInfo = try {
                    getLocalNetworkInfo()
                } catch (e: Exception) {
                    trySend(NetworkScanner.ScanResult.Error("Network unreachable: ${e.message}"))
                    channel.close()
                    return@launch
                }

                if (networkInfo.localIp.startsWith("127.")) {
                    trySend(NetworkScanner.ScanResult.Finished(emptyList()))
                    channel.close()
                    return@launch
                }

                val ipList = getIpsInRange(networkInfo.localIp, networkInfo.prefixLength)
                val totalIps = ipList.size
                val semaphore = Semaphore(50)

                coroutineScope {
                    ipList.forEach { ip ->
                        if (!isActive) return@forEach
                        launch {
                            semaphore.withPermit {
                                val isAlive = isHostAlive(ip)
                                if (isAlive) {
                                    val node = NetworkNode(
                                        id = ip,
                                        ipAddress = ip,
                                        macAddress = getMacFromArpTable(ip),
                                        hostname = try { InetAddress.getByName(ip).hostName } catch (_: Exception) { null },
                                        riskLevel = RiskLevel.LOW // Default
                                    )
                                    foundNodes.add(node)
                                }
                                val current = scannedCount.incrementAndGet()
                                trySend(NetworkScanner.ScanResult.Progress(current.toFloat() / totalIps, foundNodes.toList()))
                            }
                        }
                    }
                }
                trySend(NetworkScanner.ScanResult.Finished(foundNodes.toList()))
            } catch (e: Exception) {
                trySend(NetworkScanner.ScanResult.Error(e.message ?: "Discovery failed", e))
            } finally {
                channel.close()
            }
        }
        scanJob = job

        awaitClose { 
            job.cancel()
            if (scanJob == job) scanJob = null
        }
    }

    override fun startScan(): Flow<NetworkScanner.ScanResult<NetworkNode>> = discoverDevices()

    override fun stopScan() {
        scanJob?.cancel()
    }

    private fun isHostAlive(host: String): Boolean {
        // Try common ports to check if host is alive, since Ping (ICMP) often fails on Android
        // Added 53 (DNS), 8080, 8443 (UniFi), 23 (Telnet), 161 (SNMP) for better infra discovery
        val portsToCheck = listOf(80, 443, 22, 53, 135, 445, 8080, 8443, 23, 161)
        for (port in portsToCheck) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 200)
                    return true
                }
            } catch (e: Exception) {
                // Continue to next port
            }
        }
        
        // Fallback 1: Native Ping (ICMP) - More reliable than isReachable on many Android versions.
        // Bound the wait so a cancelled/slow scan can't leave orphaned ping processes running,
        // or block scan shutdown indefinitely.
        try {
            val process = Runtime.getRuntime().exec("ping -c 1 -W 1 $host")
            val finished = process.waitFor(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroy()
            } else if (process.exitValue() == 0) {
                return true
            }
        } catch (e: Exception) {
            // Fallback to next
        }

        // Fallback 2: InetAddress.isReachable (might work on some setups/emulators)
        return try {
            InetAddress.getByName(host).isReachable(300)
        } catch (e: Exception) {
            false
        }
    }

    private fun getMacFromArpTable(ip: String): String? {
        return try {
            val file = java.io.File("/proc/net/arp")
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

    private fun prefixToMask(prefix: Int): String {
        val mask = -1 shl (32 - prefix)
        return intToIp(mask)
    }

    private fun getIpsInRange(localIp: String, prefix: Int): List<String> {
        if (localIp.startsWith("127.")) return emptyList()
        
        val inetAddress = InetAddress.getByName(localIp)
        val addressInt = ByteBuffer.wrap(inetAddress.address).int
        val mask = -1 shl (32 - prefix)
        val startIp = (addressInt and mask)
        val endIp = startIp or mask.inv()
        
        val ips = mutableListOf<String>()
        // Skip network and broadcast addresses for common /24
        for (i in (startIp + 1) until endIp) {
            ips.add(intToIp(i))
        }
        return ips
    }

    private fun intToIp(ip: Int): String {
        return String.format(
            Locale.US,
            "%d.%d.%d.%d",
            (ip shr 24) and 0xFF,
            (ip shr 16) and 0xFF,
            (ip shr 8) and 0xFF,
            ip and 0xFF
        )
    }
}
