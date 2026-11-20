package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.service.HostValidator
import kotlinx.coroutines.flow.Flow

data class TracerouteHop(
    val hopNumber: Int,
    val ip: String?,
    val hostname: String? = null,
    val isFinal: Boolean = false,
    /** RTTs (ms) from pinging this hop directly; TTL-exceeded replies carry no timing. */
    val rttSamples: List<Float> = emptyList(),
    /** Share of direct pings to this hop that went unanswered; null until measured. */
    val lossPercent: Float? = null,
    /** True while RTT / reverse DNS for this hop are still being measured. */
    val isMeasuring: Boolean = false
) {
    val avgMs: Float? get() = rttSamples.takeIf { it.isNotEmpty() }?.average()?.toFloat()
    val minMs: Float? get() = rttSamples.minOrNull()
    val maxMs: Float? get() = rttSamples.maxOrNull()
    val isPrivate: Boolean get() = ip != null && HostValidator.isPrivateAddress(ip)
}

data class TracerouteOptions(
    val host: String,
    val maxHops: Int = 30,
    /** Seconds to wait for each TTL probe. */
    val probeTimeoutSec: Int = 1,
    /** Probe attempts per TTL before declaring the hop silent. */
    val probeAttempts: Int = 2,
    /** Direct pings per responding hop to measure latency. */
    val rttSamples: Int = 3,
    val resolveHostnames: Boolean = true,
    /** Stop early after this many consecutive silent hops (likely a filtering firewall). */
    val stopAfterSilentHops: Int = 6
)

data class TracerouteResult(
    val target: String,
    val targetIp: String? = null,
    val hops: List<TracerouteHop> = emptyList(),
    val isComplete: Boolean = false,
    val reachedDestination: Boolean = false,
    val error: String? = null
) {
    /** Plain-text report for copying/sharing. */
    fun toReport(): String = buildString {
        appendLine("Traceroute to $target" + (targetIp?.takeIf { it != target }?.let { " ($it)" } ?: ""))
        hops.forEach { hop ->
            append(hop.hopNumber.toString().padStart(2)).append("  ")
            if (hop.ip == null) {
                appendLine("* * *")
            } else {
                append(hop.ip)
                hop.hostname?.let { append(" ($it)") }
                hop.avgMs?.let { append("  %.1f ms".format(it)) } ?: append("  no direct reply")
                hop.lossPercent?.takeIf { it > 0f }?.let { append("  %.0f%% loss".format(it)) }
                appendLine()
            }
        }
        if (isComplete) appendLine(if (reachedDestination) "Destination reached in ${hops.size} hops" else "Destination not reached")
    }.trimEnd()
}

interface TracerouteRepository {
    fun traceroute(options: TracerouteOptions): Flow<TracerouteResult>
}
