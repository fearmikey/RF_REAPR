package com.fearmikey.rf_reapr.domain.service

/**
 * Validates and normalizes user-entered targets before they're handed to command-line
 * tools (ping, traceroute) or network APIs.
 *
 * Runtime.exec() doesn't use a shell, but unvalidated input can still inject extra
 * arguments (e.g. `-f 10.0.0.1` would enable flood ping), so only plain hostnames and IP
 * literals are accepted.
 */
object HostValidator {

    private val ipv4 = Regex("^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$")
    private val hostnameLabel = Regex("^[A-Za-z0-9_]([A-Za-z0-9_-]{0,61}[A-Za-z0-9_])?$")
    private val ipv6Chars = Regex("^[0-9A-Fa-f:.]+$")

    fun isIpv4(value: String): Boolean = ipv4.matches(value)

    /** Loose IPv6 literal check (hex groups, `::` compression, optional embedded IPv4). */
    fun isIpv6(value: String): Boolean {
        val addr = value.substringBefore('%') // allow scope ids like fe80::1%wlan0
        if (!addr.contains(':') || !ipv6Chars.matches(addr)) return false
        if (addr.split("::").size > 2) return false
        val groups = addr.split(':').filter { it.isNotEmpty() }
        if (groups.size > 8) return false
        return groups.all { g -> (g.length in 1..4 && g.all { it.isLetterOrDigit() } && !g.contains('.')) || isIpv4(g) }
    }

    fun isHostname(value: String): Boolean {
        val host = value.removeSuffix(".")
        if (host.isEmpty() || host.length > 253 || host.startsWith("-")) return false
        // All-numeric dotted strings must be valid IPv4 instead (rejects "999.1.1.1").
        if (host.all { it.isDigit() || it == '.' }) return false
        return host.split('.').all { hostnameLabel.matches(it) }
    }

    fun isValidHost(value: String): Boolean = isIpv4(value) || isIpv6(value) || isHostname(value)

    /**
     * Trims whitespace and strips things users commonly paste (URL scheme, path, port,
     * brackets around IPv6). Returns null if what's left isn't a valid host.
     */
    fun normalize(input: String): String? {
        var value = input.trim()
        if (value.isEmpty()) return null
        value = value.substringAfter("://")
        value = value.substringBefore('/').substringBefore('?').substringBefore('#')
        value = value.substringAfter('@') // user@host
        value = when {
            value.startsWith("[") -> value.substringAfter('[').substringBefore(']')
            // host:port (but not an IPv6 literal, which has several colons)
            value.count { it == ':' } == 1 -> value.substringBefore(':')
            else -> value
        }
        return value.takeIf { isValidHost(it) }
    }

    /**
     * Private, loopback, link-local and CGNAT ranges, used to label traceroute hops as
     * "local network" vs ISP/internet.
     */
    fun isPrivateAddress(ip: String): Boolean {
        if (isIpv4(ip)) {
            val p = ip.split('.').map { it.toInt() }
            return p[0] == 10 ||
                (p[0] == 172 && p[1] in 16..31) ||
                (p[0] == 192 && p[1] == 168) ||
                (p[0] == 100 && p[1] in 64..127) ||
                (p[0] == 169 && p[1] == 254) ||
                p[0] == 127
        }
        val lower = ip.lowercase()
        return lower.startsWith("fe80") || lower.startsWith("fc") || lower.startsWith("fd") || lower == "::1"
    }
}
