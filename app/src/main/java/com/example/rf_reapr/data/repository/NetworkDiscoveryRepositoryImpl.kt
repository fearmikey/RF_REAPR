package com.example.rf_reapr.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import com.example.rf_reapr.domain.model.NetworkNode
import com.example.rf_reapr.domain.model.RiskLevel
import com.example.rf_reapr.domain.repository.LocalNetworkInfo
import com.example.rf_reapr.domain.repository.NetworkDiscoveryRepository
import com.example.rf_reapr.domain.scanner.NetworkScanner
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
    private val context: Context
) : NetworkDiscoveryRepository {

    private var scanJob: Job? = null

    override suspend fun getLocalNetworkInfo(): LocalNetworkInfo = withContext(Dispatchers.IO) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val linkProperties: LinkProperties? = connectivityManager.getLinkProperties(activeNetwork)
        
        val linkAddress = linkProperties?.linkAddresses?.firstOrNull { it.address is Inet4Address }
        val localIp = linkAddress?.address?.hostAddress ?: "127.0.0.1"
        val prefixLength = linkAddress?.prefixLength ?: 24
        
        // Gateway is harder to get reliably via LinkProperties sometimes, 
        // but often it's the first IP in the subnet.
        val gatewayIp = linkProperties?.routes?.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress
        
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
        
        scanJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val networkInfo = getLocalNetworkInfo()
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

        awaitClose { stopScan() }
    }

    override fun startScan(): Flow<NetworkScanner.ScanResult<NetworkNode>> = discoverDevices()

    override fun stopScan() {
        scanJob?.cancel()
    }

    private fun isHostAlive(host: String): Boolean {
        // Try common ports to check if host is alive, since Ping (ICMP) often fails on Android
        val portsToCheck = listOf(80, 443, 22, 135, 445)
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
        // Fallback to InetAddress.isReachable (might work on some setups/emulators)
        return try {
            InetAddress.getByName(host).isReachable(300)
        } catch (e: Exception) {
            false
        }
    }

    private fun prefixToMask(prefix: Int): String {
        val mask = -1 shl (32 - prefix)
        return intToIp(mask)
    }

    private fun getIpsInRange(localIp: String, prefix: Int): List<String> {
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
