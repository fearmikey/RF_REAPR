package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.domain.repository.TracerouteHop
import com.fearmikey.rf_reapr.domain.repository.TracerouteOptions
import com.fearmikey.rf_reapr.domain.repository.TracerouteRepository
import com.fearmikey.rf_reapr.domain.repository.TracerouteResult
import com.fearmikey.rf_reapr.domain.service.HostValidator
import com.fearmikey.rf_reapr.domain.service.PingOutputParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * ICMP traceroute using the system `ping` binary with increasing TTLs (no root needed).
 *
 * Each responding hop is then pinged directly in the background to get real round-trip
 * times and loss (TTL-exceeded replies carry no timing, and timing the whole `ping`
 * process mostly measured process start-up). Reverse DNS runs alongside so it doesn't
 * slow down path discovery.
 */
class TracerouteRepositoryImpl : TracerouteRepository {

    private val fromRegex = Regex("""From\s+([0-9A-Fa-f:.]+?):?\s""")
    private val replyFromRegex = Regex("""bytes from\s+([0-9A-Fa-f:.]+?):?\s""")

    override fun traceroute(options: TracerouteOptions): Flow<TracerouteResult> = channelFlow {
        val host = HostValidator.normalize(options.host)
        if (host == null) {
            send(TracerouteResult(options.host, isComplete = true, error = "Invalid host: enter a hostname or IP address"))
            return@channelFlow
        }
        val targetIp = runCatching { InetAddress.getByName(host).hostAddress }.getOrNull()
        if (targetIp == null) {
            send(TracerouteResult(host, isComplete = true, error = "Could not resolve $host"))
            return@channelFlow
        }
        val ipv6 = HostValidator.isIpv6(targetIp)

        val lock = Mutex()
        val hops = sortedMapOf<Int, TracerouteHop>()
        suspend fun publish(complete: Boolean = false, reached: Boolean = false) {
            val snapshot = lock.withLock { hops.values.toList() }
            send(TracerouteResult(host, targetIp, snapshot, complete, reached))
        }

        val measurements = mutableListOf<Job>()
        val measureLimit = Semaphore(3)
        var silentStreak = 0
        var reached = false

        publish()
        for (ttl in 1..options.maxHops.coerceIn(1, 64)) {
            val hopIp = probe(targetIp, ttl, ipv6, options)
            val isFinal = hopIp == targetIp
            lock.withLock { hops[ttl] = TracerouteHop(ttl, hopIp, isFinal = isFinal, isMeasuring = hopIp != null) }
            publish()

            if (hopIp != null) {
                silentStreak = 0
                measurements += launch(Dispatchers.IO) {
                    measureLimit.withPermit {
                        val samples = measureRtt(hopIp, ipv6, options.rttSamples)
                        val name = if (options.resolveHostnames) reverseLookup(hopIp) else null
                        lock.withLock {
                            hops[ttl] = hops.getValue(ttl).copy(
                                hostname = name,
                                rttSamples = samples,
                                lossPercent = if (options.rttSamples > 0) {
                                    (options.rttSamples - samples.size).coerceAtLeast(0) * 100f / options.rttSamples
                                } else null,
                                isMeasuring = false
                            )
                        }
                        publish()
                    }
                }
            } else {
                silentStreak++
            }

            if (isFinal) {
                reached = true
                break
            }
            if (silentStreak >= options.stopAfterSilentHops) break
        }

        measurements.joinAll()
        publish(complete = true, reached = reached)
    }.flowOn(Dispatchers.IO)

    /** Sends TTL-limited echo requests and returns the address that answered, if any. */
    private suspend fun probe(targetIp: String, ttl: Int, ipv6: Boolean, options: TracerouteOptions): String? {
        repeat(options.probeAttempts.coerceAtLeast(1)) {
            val output = runCommand(
                listOf(pingBinary(ipv6), "-n", "-c", "1", "-t", ttl.toString(), "-W", options.probeTimeoutSec.toString(), targetIp),
                timeoutMs = options.probeTimeoutSec * 1000L + 1500
            )
            (fromRegex.find(output) ?: replyFromRegex.find(output))?.let { return it.groupValues[1] }
        }
        return null
    }

    private suspend fun measureRtt(ip: String, ipv6: Boolean, samples: Int): List<Float> {
        if (samples <= 0) return emptyList()
        val output = runCommand(
            listOf(pingBinary(ipv6), "-n", "-c", samples.toString(), "-i", "0.2", "-W", "1", ip),
            timeoutMs = samples * 1200L + 1500
        )
        return output.lineSequence().mapNotNull { PingOutputParser.parseReply(it)?.timeMs }.toList()
    }

    private fun reverseLookup(ip: String): String? = runCatching {
        InetAddress.getByName(ip).canonicalHostName.takeIf { it != ip }
    }.getOrNull()

    private fun pingBinary(ipv6: Boolean) = if (ipv6) "/system/bin/ping6" else "/system/bin/ping"

    /** Runs a command, returning combined output; cancellation interrupts and kills it. */
    private suspend fun runCommand(args: List<String>, timeoutMs: Long): String = runInterruptible(Dispatchers.IO) {
        val process = ProcessBuilder(args).redirectErrorStream(true).start()
        try {
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) process.destroy()
            process.inputStream.bufferedReader().use { it.readText() }
        } catch (_: java.io.IOException) {
            ""
        } finally {
            process.destroy()
        }
    }
}
