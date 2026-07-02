package com.example.rf_reapr.data.repository

import com.example.rf_reapr.domain.model.OpenPort
import com.example.rf_reapr.domain.repository.PortScannerRepository
import com.example.rf_reapr.domain.scanner.NetworkScanner
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.io.BufferedReader
import java.io.InputStreamReader

class PortScannerRepositoryImpl : PortScannerRepository {
    private var ipAddress: String = ""
    private var portsToScan: Iterable<Int> = 1..1024
    private var scanJob: Job? = null

    private val commonServices = mapOf(
        21 to "FTP",
        22 to "SSH",
        23 to "Telnet",
        25 to "SMTP",
        53 to "DNS",
        80 to "HTTP",
        110 to "POP3",
        143 to "IMAP",
        443 to "HTTPS",
        3306 to "MySQL",
        5432 to "PostgreSQL",
        8080 to "HTTP-Proxy",
    )

    override fun setConfig(ipAddress: String, portRange: IntRange) {
        this.ipAddress = ipAddress
        this.portsToScan = portRange
    }

    override fun setConfig(ipAddress: String, ports: List<Int>) {
        this.ipAddress = ipAddress
        this.portsToScan = ports
    }

    override fun startScan(): Flow<NetworkScanner.ScanResult<OpenPort>> = callbackFlow {
        val foundPorts = Collections.synchronizedList(mutableListOf<OpenPort>())
        val scannedCount = AtomicInteger(0)
        val portList = portsToScan.toList()
        val totalPorts = portList.size
        val semaphore = Semaphore(50) // Scan 50 ports concurrently

        scanJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                coroutineScope {
                    for (port in portList) {
                        if (!isActive) break
                        launch {
                            semaphore.withPermit {
                                if (isPortOpen(ipAddress, port)) {
                                    val banner = grabBanner(ipAddress, port)
                                    val openPort = OpenPort(
                                        port = port,
                                        serviceName = commonServices[port] ?: "Unknown",
                                        banner = banner
                                    )
                                    foundPorts.add(openPort)
                                }
                                val current = scannedCount.incrementAndGet()
                                trySend(NetworkScanner.ScanResult.Progress(current.toFloat() / totalPorts, foundPorts.toList()))
                            }
                        }
                    }
                }
                trySend(NetworkScanner.ScanResult.Finished(foundPorts.toList()))
            } catch (e: Exception) {
                trySend(NetworkScanner.ScanResult.Error(e.message ?: "Unknown error", e))
            } finally {
                channel.close()
            }
        }
        
        awaitClose { 
            stopScan()
        }
    }

    override fun stopScan() {
        scanJob?.cancel()
    }

    private fun isPortOpen(host: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 500)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun grabBanner(host: String, port: Int): String? {
        return try {
            Socket().use { socket ->
                socket.soTimeout = 1000
                socket.connect(InetSocketAddress(host, port), 500)
                
                // For HTTP/HTTPS, we might need to send something, but for others (SSH, FTP, SMTP) 
                // they usually send the banner first.
                // This is a generic banner grabber.
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                reader.readLine()?.trim()
            }
        } catch (_: Exception) {
            null
        }
    }
}
