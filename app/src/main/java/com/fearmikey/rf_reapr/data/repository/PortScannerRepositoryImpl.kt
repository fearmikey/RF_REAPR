package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.domain.model.OpenPort
import com.fearmikey.rf_reapr.domain.repository.PortScanOptions
import com.fearmikey.rf_reapr.domain.repository.PortScannerRepository
import com.fearmikey.rf_reapr.domain.scanner.NetworkScanner
import com.fearmikey.rf_reapr.domain.service.PortCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Collections
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class PortScannerRepositoryImpl : PortScannerRepository {

    // Legacy NetworkScanner config (setConfig + startScan).
    @Volatile private var legacyHost: String = ""
    @Volatile private var legacyPorts: List<Int> = emptyList()
    private val activeJobs = Collections.synchronizedSet(mutableSetOf<Job>())

    @Deprecated("Shared mutable config; use scan(host, ports, options)")
    override fun setConfig(ipAddress: String, portRange: IntRange) {
        legacyHost = ipAddress
        legacyPorts = portRange.toList()
    }

    @Deprecated("Shared mutable config; use scan(host, ports, options)")
    override fun setConfig(ipAddress: String, ports: List<Int>) {
        legacyHost = ipAddress
        legacyPorts = ports
    }

    override fun startScan(): Flow<NetworkScanner.ScanResult<OpenPort>> = scan(legacyHost, legacyPorts)

    /** Cancels every scan currently running through this repository ("Stop all"). */
    override fun stopScan() {
        synchronized(activeJobs) { activeJobs.toList() }.forEach { it.cancel() }
    }

    override fun scan(host: String, ports: List<Int>, options: PortScanOptions): Flow<NetworkScanner.ScanResult<OpenPort>> = channelFlow {
        val job = currentCoroutineContext().job
        activeJobs.add(job)
        try {
            // Resolve once instead of once per port (a hostname target would otherwise do
            // up to 65k DNS lookups).
            val address = try {
                runInterruptible { InetAddress.getByName(host) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                send(NetworkScanner.ScanResult.Error("Couldn't resolve $host", e))
                return@channelFlow
            }

            val total = ports.size
            if (total == 0) {
                send(NetworkScanner.ScanResult.Finished(emptyList()))
                return@channelFlow
            }

            val queue = ConcurrentLinkedQueue(ports)
            val found = Collections.synchronizedList(mutableListOf<OpenPort>())
            val scanned = AtomicInteger(0)
            val step = (total / 200).coerceAtLeast(1)

            coroutineScope {
                repeat(options.concurrency.coerceIn(1, 256).coerceAtMost(total)) {
                    launch {
                        while (isActive) {
                            val port = queue.poll() ?: break
                            val openPort = runInterruptible { probe(address, port, options) }
                            if (openPort != null) found.add(openPort)
                            val done = scanned.incrementAndGet()
                            if (openPort != null || done % step == 0 || done == total) {
                                send(NetworkScanner.ScanResult.Progress(done.toFloat() / total, found.sortedBy { it.port }))
                            }
                        }
                    }
                }
            }
            send(NetworkScanner.ScanResult.Finished(found.sortedBy { it.port }))
        } finally {
            activeJobs.remove(job)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Connects once; if the port is open, reads whatever the service announces (SSH, FTP,
     * SMTP...) or sends a HEAD request to plain-HTTP ports to get the Server header.
     */
    private fun probe(address: InetAddress, port: Int, options: PortScanOptions): OpenPort? {
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(address, port), options.connectTimeoutMs)
        } catch (_: Exception) {
            runCatching { socket.close() }
            return null
        }
        socket.use { s ->
            val banner = if (options.grabBanners) runCatching { readBanner(s, address, port) }.getOrNull() else null
            return OpenPort(port = port, serviceName = PortCatalog.serviceName(port), banner = banner)
        }
    }

    private fun readBanner(socket: Socket, address: InetAddress, port: Int): String? {
        socket.soTimeout = 1200
        val input = socket.getInputStream()
        if (PortCatalog.shouldProbeHttp(port)) {
            val hostHeader = address.hostAddress?.let { if (it.contains(':')) "[$it]" else it } ?: ""
            socket.getOutputStream().apply {
                write("HEAD / HTTP/1.0\r\nHost: $hostHeader\r\nUser-Agent: RF-REAPR\r\n\r\n".toByteArray())
                flush()
            }
            val response = readUpTo(input, 4096) ?: return null
            val lines = response.lines()
            val server = lines.firstOrNull { it.startsWith("Server:", ignoreCase = true) }
                ?.substringAfter(':')?.trim()
            val status = lines.firstOrNull()?.trim()
            return server?.takeIf { it.isNotEmpty() } ?: status?.takeIf { it.startsWith("HTTP/") }
        }
        // Most text protocols greet first; read the first line.
        val text = readUpTo(input, 512) ?: return null
        return text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
            ?.filter { it.code in 32..126 }
            ?.take(200)
            ?.takeIf { it.isNotEmpty() }
    }

    private fun readUpTo(input: InputStream, max: Int): String? {
        val buffer = ByteArray(max)
        var total = 0
        try {
            while (total < max) {
                val n = input.read(buffer, total, max - total)
                if (n <= 0) break
                total += n
                // Stop at end of HTTP headers or after the first line of a greeting.
                val soFar = String(buffer, 0, total, Charsets.ISO_8859_1)
                if (soFar.contains("\r\n\r\n") || (soFar.contains('\n') && !soFar.startsWith("HTTP/"))) break
            }
        } catch (_: SocketTimeoutException) {
            // Service didn't say anything (common for HTTPS, databases, RDP).
        }
        return if (total == 0) null else String(buffer, 0, total, Charsets.ISO_8859_1)
    }
}
