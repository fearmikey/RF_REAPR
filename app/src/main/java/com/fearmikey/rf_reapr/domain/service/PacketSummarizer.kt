package com.fearmikey.rf_reapr.domain.service

import java.net.InetAddress

enum class TransportProtocol(val label: String) {
    TCP("TCP"), UDP("UDP"), ICMP("ICMP"), ICMPV6("ICMPv6"), OTHER("Other")
}

data class PacketInfo(
    val ipVersion: Int,
    val protocol: TransportProtocol,
    val source: String,
    val destination: String,
    val sourcePort: Int? = null,
    val destinationPort: Int? = null,
    /** Queried name if this is a DNS query (UDP/53). */
    val dnsQuery: String? = null
)

/**
 * Minimal, allocation-light parser for raw IPv4/IPv6 packets as read from a TUN device
 * (no link-layer header). Pure Kotlin so it can be unit tested.
 */
object PacketSummarizer {

    fun parse(buf: ByteArray, length: Int = buf.size): PacketInfo? {
        if (length < 1) return null
        return when ((buf[0].toInt() ushr 4) and 0x0F) {
            4 -> parseIpv4(buf, length)
            6 -> parseIpv6(buf, length)
            else -> null
        }
    }

    private fun parseIpv4(buf: ByteArray, length: Int): PacketInfo? {
        if (length < 20) return null
        val headerLen = (buf[0].toInt() and 0x0F) * 4
        if (headerLen < 20 || headerLen > length) return null
        val protoNum = buf[9].toInt() and 0xFF
        val src = address(buf, 12, 4) ?: return null
        val dst = address(buf, 16, 4) ?: return null
        return transport(4, protoNum, src, dst, buf, headerLen, length)
    }

    private fun parseIpv6(buf: ByteArray, length: Int): PacketInfo? {
        if (length < 40) return null
        val nextHeader = buf[6].toInt() and 0xFF
        val src = address(buf, 8, 16) ?: return null
        val dst = address(buf, 24, 16) ?: return null
        return transport(6, nextHeader, src, dst, buf, 40, length)
    }

    private fun transport(version: Int, protoNum: Int, src: String, dst: String, buf: ByteArray, offset: Int, length: Int): PacketInfo {
        val protocol = when (protoNum) {
            6 -> TransportProtocol.TCP
            17 -> TransportProtocol.UDP
            1 -> TransportProtocol.ICMP
            58 -> TransportProtocol.ICMPV6
            else -> TransportProtocol.OTHER
        }
        var srcPort: Int? = null
        var dstPort: Int? = null
        var dns: String? = null
        if ((protocol == TransportProtocol.TCP || protocol == TransportProtocol.UDP) && length >= offset + 4) {
            srcPort = u16(buf, offset)
            dstPort = u16(buf, offset + 2)
            if (protocol == TransportProtocol.UDP && dstPort == 53) {
                dns = parseDnsQuestion(buf, offset + 8, length)
            }
        }
        return PacketInfo(version, protocol, src, dst, srcPort, dstPort, dns)
    }

    /** Reads the first question name from a DNS message starting at [start]. */
    internal fun parseDnsQuestion(buf: ByteArray, start: Int, length: Int): String? {
        if (length < start + 12) return null
        val qdCount = u16(buf, start + 4)
        if (qdCount < 1) return null
        var pos = start + 12
        val labels = mutableListOf<String>()
        while (pos < length) {
            val len = buf[pos].toInt() and 0xFF
            if (len == 0) break
            if (len and 0xC0 != 0 || pos + 1 + len > length || labels.size > 127) return null // no compression in questions
            labels += String(buf, pos + 1, len, Charsets.US_ASCII)
            pos += 1 + len
        }
        return labels.takeIf { it.isNotEmpty() }?.joinToString(".")
    }

    private fun u16(buf: ByteArray, at: Int) = ((buf[at].toInt() and 0xFF) shl 8) or (buf[at + 1].toInt() and 0xFF)

    private fun address(buf: ByteArray, at: Int, len: Int): String? =
        runCatching { InetAddress.getByAddress(buf.copyOfRange(at, at + len)).hostAddress }.getOrNull()
}

/** Rolling statistics for a capture session. Not thread-safe; owned by the capture loop. */
class CaptureAggregator(private val maxDnsNames: Int = 50) {
    var packets = 0L
        private set
    var bytes = 0L
        private set
    private val protocols = HashMap<TransportProtocol, Long>()
    private val destinations = HashMap<String, Long>()
    private val dnsNames = LinkedHashMap<String, Long>()

    fun add(info: PacketInfo?, length: Int) {
        packets++
        bytes += length
        if (info == null) return
        protocols.merge(info.protocol, 1L, Long::plus)
        val dest = info.destinationPort?.let { "${info.destination}:$it" } ?: info.destination
        destinations.merge(dest, 1L, Long::plus)
        info.dnsQuery?.let { name ->
            dnsNames.remove(name)
            dnsNames[name] = System.currentTimeMillis()
            if (dnsNames.size > maxDnsNames) dnsNames.remove(dnsNames.keys.first())
        }
    }

    fun protocolCounts(): Map<TransportProtocol, Long> = protocols.toMap()

    fun topDestinations(limit: Int = 8): List<Pair<String, Long>> =
        destinations.entries.sortedByDescending { it.value }.take(limit).map { it.key to it.value }

    /** Most recently queried names first. */
    // toList().asReversed() rather than reversed(): on compileSdk 35 the latter can bind to
    // Java's SequencedCollection.reversed(), which doesn't exist on older devices.
    fun recentDnsQueries(): List<String> = dnsNames.keys.toList().asReversed()
}
