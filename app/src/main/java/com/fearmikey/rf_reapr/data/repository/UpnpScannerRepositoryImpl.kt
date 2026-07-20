package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.domain.repository.UpnpDevice
import com.fearmikey.rf_reapr.domain.repository.UpnpScannerRepository
import com.fearmikey.rf_reapr.domain.scanner.NetworkScanner
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class UpnpScannerRepositoryImpl : UpnpScannerRepository {

    private var scanJob: Job? = null

    override fun discoverDevices(): Flow<NetworkScanner.ScanResult<UpnpDevice>> = callbackFlow {
        val foundDevices = ConcurrentHashMap<String, UpnpDevice>()
        
        val job = launch(Dispatchers.IO) {
            try {
                trySend(NetworkScanner.ScanResult.Progress(0.1f, emptyList()))
                
                val socket = DatagramSocket()
                socket.soTimeout = 3000
                
                val message = """
                    M-SEARCH * HTTP/1.1
                    HOST: 239.255.255.250:1900
                    MAN: "ssdp:discover"
                    MX: 3
                    ST: ssdp:all
                    
                """.trimIndent().replace("\n", "\r\n")
                
                val group = InetAddress.getByName("239.255.255.250")
                val packet = DatagramPacket(message.toByteArray(), message.length, group, 1900)
                
                socket.send(packet)
                
                val receiveBuffer = ByteArray(4096)
                val startTime = System.currentTimeMillis()
                
                while (System.currentTimeMillis() - startTime < 5000 && isActive) {
                    try {
                        val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
                        socket.receive(receivePacket)
                        
                        val response = String(receivePacket.data, 0, receivePacket.length)
                        val location = response.lines().find { it.startsWith("LOCATION:", true) }
                            ?.substringAfter(":")?.trim()
                        
                        if (location != null && !foundDevices.containsKey(location)) {
                            // In a real implementation, we would fetch the XML at 'location' 
                            // and parse it for device details. For now, we'll use the IP.
                            val url = URL(location)
                            val ip = url.host
                            
                            val device = UpnpDevice(
                                ipAddress = ip,
                                friendlyName = "UPnP Device ($ip)",
                                manufacturer = "Unknown",
                                modelName = "Unknown",
                                location = location
                            )
                            foundDevices[location] = device
                            trySend(NetworkScanner.ScanResult.Progress(0.5f, foundDevices.values.toList()))
                        }
                    } catch (e: Exception) {
                        // Timeout or other error, continue
                    }
                }
                
                trySend(NetworkScanner.ScanResult.Finished(foundDevices.values.toList()))
            } catch (e: Exception) {
                trySend(NetworkScanner.ScanResult.Error(e.message ?: "UPnP scan failed", e))
            } finally {
                channel.close()
            }
        }
        scanJob = job

        awaitClose { 
            job.cancel()
        }
    }

    override fun startScan(): Flow<NetworkScanner.ScanResult<UpnpDevice>> = discoverDevices()

    override fun stopScan() {
        scanJob?.cancel()
    }
}
