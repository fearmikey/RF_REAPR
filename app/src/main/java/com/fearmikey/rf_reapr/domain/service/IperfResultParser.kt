package com.fearmikey.rf_reapr.domain.service

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Locale

data class IperfInterval(
    val startSec: Float,
    val endSec: Float,
    val bitsPerSecond: Double,
    val retransmits: Int? = null,
    val jitterMs: Double? = null,
    val lostPercent: Double? = null
)

data class IperfSummary(
    val protocol: String = "TCP",
    val role: String? = null,
    val remoteHost: String? = null,
    val remotePort: Int? = null,
    val numStreams: Int = 1,
    val durationSec: Float? = null,
    val reverse: Boolean = false,
    val sentBps: Double? = null,
    val receivedBps: Double? = null,
    val sentBytes: Long? = null,
    val receivedBytes: Long? = null,
    val retransmits: Int? = null,
    val jitterMs: Double? = null,
    val lostPercent: Double? = null,
    val lostPackets: Long? = null,
    val packets: Long? = null,
    val cpuLocalPercent: Double? = null,
    val cpuRemotePercent: Double? = null,
    val intervals: List<IperfInterval> = emptyList(),
    val error: String? = null
) {
    val isUdp: Boolean get() = protocol.equals("UDP", ignoreCase = true)

    /** The figure users care about: what actually arrived (receiver side), else sender. */
    val headlineBps: Double? get() = receivedBps ?: sentBps

    fun toText(): String = buildString {
        error?.let { appendLine("Error: $it") }
        appendLine("$protocol, $numStreams stream${if (numStreams == 1) "" else "s"}" + if (reverse) ", download (reverse)" else ", upload")
        remoteHost?.let { appendLine("Peer: $it" + (remotePort?.let { p -> ":$p" } ?: "")) }
        sentBps?.let { appendLine("Sender:   ${IperfResultParser.formatBitrate(it)}" + (sentBytes?.let { b -> "  (${IperfResultParser.formatBytes(b)})" } ?: "")) }
        receivedBps?.let { appendLine("Receiver: ${IperfResultParser.formatBitrate(it)}" + (receivedBytes?.let { b -> "  (${IperfResultParser.formatBytes(b)})" } ?: "")) }
        retransmits?.let { appendLine("Retransmits: $it") }
        jitterMs?.let { appendLine("Jitter: %.3f ms".format(Locale.US, it)) }
        lostPercent?.let { appendLine("Loss: %.2f%%".format(Locale.US, it) + (lostPackets?.let { l -> " ($l/${packets ?: "?"})" } ?: "")) }
    }.trimEnd()
}

/**
 * Parses iperf3 `-J` (JSON) output. Uses Gson rather than org.json so it runs in plain
 * JVM unit tests.
 */
object IperfResultParser {

    /**
     * Extracts and parses the JSON document from [output]; the JNI bridge may append
     * plain-text error lines after it. Returns null if no JSON object is present.
     */
    fun parse(output: String): IperfSummary? {
        val start = output.indexOf('{')
        val end = output.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val root = try {
            JsonParser.parseString(output.substring(start, end + 1)).asJsonObject
        } catch (_: Exception) {
            return null
        }

        val startObj = root.obj("start")
        val testStart = startObj?.obj("test_start")
        val connected = startObj?.arr("connected")?.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
        val endObj = root.obj("end")
        val protocol = testStart?.str("protocol") ?: "TCP"
        val isUdp = protocol.equals("UDP", true)

        val sumSent = endObj?.obj("sum_sent")
        val sumReceived = endObj?.obj("sum_received")
        val udpSum = endObj?.obj("sum")
        val cpu = endObj?.obj("cpu_utilization_percent")

        val intervals = root.arr("intervals")?.mapNotNull { el ->
            val sum = el.takeIf { it.isJsonObject }?.asJsonObject?.obj("sum") ?: return@mapNotNull null
            IperfInterval(
                startSec = sum.num("start")?.toFloat() ?: 0f,
                endSec = sum.num("end")?.toFloat() ?: 0f,
                bitsPerSecond = sum.num("bits_per_second") ?: 0.0,
                retransmits = sum.num("retransmits")?.toInt(),
                jitterMs = sum.num("jitter_ms"),
                lostPercent = sum.num("lost_percent")
            )
        }.orEmpty()

        return IperfSummary(
            protocol = protocol,
            role = when {
                startObj?.has("accepted_connection") == true -> "server"
                startObj?.has("connecting_to") == true -> "client"
                else -> null
            },
            remoteHost = connected?.str("remote_host"),
            remotePort = connected?.num("remote_port")?.toInt(),
            numStreams = testStart?.num("num_streams")?.toInt() ?: 1,
            durationSec = testStart?.num("duration")?.toFloat(),
            reverse = (testStart?.num("reverse")?.toInt() ?: 0) != 0,
            sentBps = sumSent?.num("bits_per_second") ?: if (isUdp) udpSum?.num("bits_per_second") else null,
            receivedBps = sumReceived?.num("bits_per_second"),
            sentBytes = sumSent?.num("bytes")?.toLong() ?: if (isUdp) udpSum?.num("bytes")?.toLong() else null,
            receivedBytes = sumReceived?.num("bytes")?.toLong(),
            retransmits = if (isUdp) null else sumSent?.num("retransmits")?.toInt(),
            jitterMs = if (isUdp) (udpSum?.num("jitter_ms") ?: sumReceived?.num("jitter_ms")) else null,
            lostPercent = if (isUdp) (udpSum?.num("lost_percent") ?: sumReceived?.num("lost_percent")) else null,
            lostPackets = if (isUdp) (udpSum?.num("lost_packets") ?: sumReceived?.num("lost_packets"))?.toLong() else null,
            packets = if (isUdp) (udpSum?.num("packets") ?: sumReceived?.num("packets"))?.toLong() else null,
            cpuLocalPercent = cpu?.num("host_total"),
            cpuRemotePercent = cpu?.num("remote_total"),
            intervals = intervals,
            error = root.str("error")
        )
    }

    fun formatBitrate(bps: Double): String = when {
        bps >= 1e9 -> "%.2f Gbit/s".format(Locale.US, bps / 1e9)
        bps >= 1e6 -> "%.1f Mbit/s".format(Locale.US, bps / 1e6)
        bps >= 1e3 -> "%.1f Kbit/s".format(Locale.US, bps / 1e3)
        else -> "%.0f bit/s".format(Locale.US, bps)
    }

    fun formatBytes(bytes: Long): String = when {
        bytes >= 1L shl 30 -> "%.2f GB".format(Locale.US, bytes / (1L shl 30).toDouble())
        bytes >= 1L shl 20 -> "%.1f MB".format(Locale.US, bytes / (1L shl 20).toDouble())
        bytes >= 1L shl 10 -> "%.1f KB".format(Locale.US, bytes / (1L shl 10).toDouble())
        else -> "$bytes B"
    }

    private fun JsonObject.obj(name: String): JsonObject? = get(name)?.takeIf { it.isJsonObject }?.asJsonObject
    private fun JsonObject.arr(name: String) = get(name)?.takeIf { it.isJsonArray }?.asJsonArray
    private fun JsonObject.str(name: String): String? = get(name)?.takeIf { it.isJsonPrimitive }?.asString
    private fun JsonObject.num(name: String): Double? =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble
}
