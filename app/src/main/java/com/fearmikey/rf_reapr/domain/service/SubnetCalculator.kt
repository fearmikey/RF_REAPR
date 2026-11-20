package com.fearmikey.rf_reapr.domain.service

enum class Ipv4AddressType(val label: String) {
    PRIVATE("Private (RFC 1918)"),
    PUBLIC("Public"),
    CGNAT("Carrier-grade NAT (RFC 6598)"),
    LOOPBACK("Loopback"),
    LINK_LOCAL("Link-local (APIPA)"),
    MULTICAST("Multicast"),
    BROADCAST("Limited broadcast"),
    THIS_NETWORK("\"This\" network"),
    DOCUMENTATION("Documentation (TEST-NET)"),
    RESERVED("Reserved")
}

data class SubnetInfo(
    val address: Long,
    val prefix: Int,
    val network: Long,
    val broadcast: Long,
    val netmask: Long,
    val firstHost: Long,
    val lastHost: Long,
    val totalAddresses: Long,
    val usableHosts: Long
) {
    val wildcard: Long get() = netmask xor 0xFFFFFFFFL
    val cidr: String get() = "${SubnetCalculator.format(network)}/$prefix"
    val addressType: Ipv4AddressType get() = SubnetCalculator.classify(address)

    /** Classful network class of the address (historical, still commonly asked for). */
    val ipClass: Char
        get() {
            val first = (address ushr 24).toInt()
            return when {
                first < 128 -> 'A'
                first < 192 -> 'B'
                first < 224 -> 'C'
                first < 240 -> 'D'
                else -> 'E'
            }
        }

    fun contains(ip: Long): Boolean = (ip and netmask) == network
}

/**
 * Pure IPv4 subnet maths using Longs as unsigned 32-bit values. No DNS lookups, so it is
 * safe to call on every keystroke from the UI thread.
 */
object SubnetCalculator {

    private const val MAX = 0xFFFFFFFFL

    fun parseIpv4(text: String): Long? {
        val parts = text.trim().split('.')
        if (parts.size != 4) return null
        var value = 0L
        for (p in parts) {
            if (p.isEmpty() || p.length > 3 || !p.all { it.isDigit() }) return null
            val octet = p.toInt()
            if (octet > 255) return null
            value = (value shl 8) or octet.toLong()
        }
        return value
    }

    fun format(ip: Long): String =
        listOf(24, 16, 8, 0).joinToString(".") { ((ip ushr it) and 0xFF).toString() }

    fun maskForPrefix(prefix: Int): Long = if (prefix == 0) 0L else (MAX shl (32 - prefix)) and MAX

    /** Returns the prefix length for a contiguous netmask such as 255.255.255.0, else null. */
    fun prefixFromMask(mask: String): Int? {
        val value = parseIpv4(mask) ?: return null
        val prefix = java.lang.Long.bitCount(value)
        return prefix.takeIf { maskForPrefix(it) == value }
    }

    /** Accepts "24", "/24", or a dotted netmask. */
    fun parsePrefix(text: String): Int? {
        val t = text.trim().removePrefix("/")
        if (t.contains('.')) return prefixFromMask(t)
        return t.toIntOrNull()?.takeIf { it in 0..32 }
    }

    /**
     * Parses user input where the address field may itself contain the prefix
     * ("10.0.0.5/8"); otherwise [prefixText] is used.
     */
    fun parseInput(addressText: String, prefixText: String): Pair<Long, Int>? {
        val trimmed = addressText.trim()
        val (ipPart, inlinePrefix) = if (trimmed.contains('/')) {
            trimmed.substringBefore('/') to parsePrefix(trimmed.substringAfter('/'))
        } else {
            trimmed to null
        }
        val ip = parseIpv4(ipPart) ?: return null
        val prefix = inlinePrefix ?: parsePrefix(prefixText) ?: return null
        return ip to prefix
    }

    fun calculate(ip: Long, prefix: Int): SubnetInfo {
        require(prefix in 0..32) { "prefix must be 0..32" }
        val mask = maskForPrefix(prefix)
        val network = ip and mask
        val broadcast = network or (mask xor MAX)
        val total = 1L shl (32 - prefix)
        val (first, last, usable) = when (prefix) {
            32 -> Triple(network, network, 1L)
            31 -> Triple(network, broadcast, 2L) // RFC 3021 point-to-point
            else -> Triple(network + 1, broadcast - 1, total - 2)
        }
        return SubnetInfo(ip, prefix, network, broadcast, mask, first, last, total, usable)
    }

    /** Splits [info] into subnets of [newPrefix], returning at most [limit] of them. */
    fun split(info: SubnetInfo, newPrefix: Int, limit: Int = 256): List<SubnetInfo> {
        if (newPrefix <= info.prefix || newPrefix > 32) return emptyList()
        val size = 1L shl (32 - newPrefix)
        val count = 1L shl (newPrefix - info.prefix)
        return (0 until minOf(count, limit.toLong())).map { i -> calculate(info.network + i * size, newPrefix) }
    }

    fun subnetCount(info: SubnetInfo, newPrefix: Int): Long =
        if (newPrefix <= info.prefix || newPrefix > 32) 0 else 1L shl (newPrefix - info.prefix)

    fun toBinary(ip: Long): String =
        listOf(24, 16, 8, 0).joinToString(".") { ((ip ushr it) and 0xFF).toString(2).padStart(8, '0') }

    fun toHex(ip: Long): String = "0x%08X".format(ip)

    fun classify(ip: Long): Ipv4AddressType {
        val a = (ip ushr 24).toInt()
        val b = ((ip ushr 16) and 0xFF).toInt()
        val c = ((ip ushr 8) and 0xFF).toInt()
        return when {
            ip == MAX -> Ipv4AddressType.BROADCAST
            a == 0 -> Ipv4AddressType.THIS_NETWORK
            a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168) -> Ipv4AddressType.PRIVATE
            a == 100 && b in 64..127 -> Ipv4AddressType.CGNAT
            a == 127 -> Ipv4AddressType.LOOPBACK
            a == 169 && b == 254 -> Ipv4AddressType.LINK_LOCAL
            (a == 192 && b == 0 && c == 2) || (a == 198 && b == 51 && c == 100) || (a == 203 && b == 0 && c == 113) ->
                Ipv4AddressType.DOCUMENTATION
            a in 224..239 -> Ipv4AddressType.MULTICAST
            a >= 240 -> Ipv4AddressType.RESERVED
            else -> Ipv4AddressType.PUBLIC
        }
    }
}
