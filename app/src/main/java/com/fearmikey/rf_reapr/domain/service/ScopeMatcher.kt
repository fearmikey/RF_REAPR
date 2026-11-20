package com.fearmikey.rf_reapr.domain.service

import java.math.BigInteger
import java.net.InetAddress

/**
 * Checks targets against an engagement's authorized scope (pure, no DNS lookups).
 *
 * Scope text, one entry per line:
 * ```
 * # comment
 * 10.0.0.0/24          IPv4 CIDR
 * 192.168.1.10         single host
 * 192.168.1.20-40      IPv4 range (last octet) or 192.168.1.20-192.168.1.40
 * 2001:db8::/32        IPv6 CIDR
 * example.com          domain and all of its subdomains
 * *.corp.example.com   subdomains only
 * !10.0.0.1            exclusion (wins over any include)
 * ```
 */
class ScopeMatcher private constructor(
    private val includes: List<Rule>,
    private val excludes: List<Rule>,
    /** Lines that could not be parsed (shown to the user so typos don't silently widen/narrow scope). */
    val invalidLines: List<String>
) {

    enum class Verdict {
        /** No scope configured for the project. */
        NO_SCOPE,
        IN_SCOPE,
        OUT_OF_SCOPE,
        /** Matched an explicit `!` exclusion. */
        EXCLUDED,
        /** Can't decide without DNS (e.g. hostname target but scope only lists IPs), or invalid target. */
        UNKNOWN
    }

    val hasScope: Boolean get() = includes.isNotEmpty()
    val ruleCount: Int get() = includes.size + excludes.size

    fun check(rawTarget: String): Verdict {
        if (includes.isEmpty() && excludes.isEmpty()) return Verdict.NO_SCOPE
        val target = Target.parse(rawTarget) ?: return Verdict.UNKNOWN
        if (excludes.any { it.matches(target) }) return Verdict.EXCLUDED
        if (includes.isEmpty()) return Verdict.NO_SCOPE
        if (includes.any { it.matches(target) }) return Verdict.IN_SCOPE
        // Only claim "out of scope" if the scope has rules of the same kind as the target.
        val comparable = includes.any { it.kind == target.kind }
        return if (comparable) Verdict.OUT_OF_SCOPE else Verdict.UNKNOWN
    }

    private enum class Kind { IP, DOMAIN }

    private class Target(val kind: Kind, val host: String, val ip: BigInteger?, val ipBits: Int) {
        companion object {
            fun parse(raw: String): Target? {
                val host = HostValidator.normalize(raw)?.lowercase()?.removeSuffix(".") ?: return null
                return when {
                    HostValidator.isIpv4(host) -> Target(Kind.IP, host, ipv4ToBig(host), 32)
                    HostValidator.isIpv6(host) -> ipv6ToBig(host.substringBefore('%'))?.let { Target(Kind.IP, host, it, 128) }
                    else -> Target(Kind.DOMAIN, host, null, 0)
                }
            }
        }
    }

    private sealed class Rule(val kind: Kind) {
        abstract fun matches(t: Target): Boolean

        class Cidr(private val network: BigInteger, private val prefix: Int, private val bits: Int) : Rule(Kind.IP) {
            private val mask: BigInteger = if (prefix == 0) BigInteger.ZERO
            else BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE)
                .xor(BigInteger.ONE.shiftLeft(bits - prefix).subtract(BigInteger.ONE))
            private val base = network.and(mask)
            override fun matches(t: Target) = t.ip != null && t.ipBits == bits && t.ip.and(mask) == base
        }

        class Range(private val start: BigInteger, private val end: BigInteger, private val bits: Int) : Rule(Kind.IP) {
            override fun matches(t: Target) = t.ip != null && t.ipBits == bits && t.ip >= start && t.ip <= end
        }

        class Domain(private val domain: String, private val subdomainsOnly: Boolean) : Rule(Kind.DOMAIN) {
            override fun matches(t: Target): Boolean {
                if (t.kind != Kind.DOMAIN) return false
                val isSub = t.host.endsWith(".$domain")
                return if (subdomainsOnly) isSub else isSub || t.host == domain
            }
        }
    }

    companion object {
        val EMPTY = ScopeMatcher(emptyList(), emptyList(), emptyList())

        fun parse(text: String?): ScopeMatcher {
            if (text.isNullOrBlank()) return EMPTY
            val includes = mutableListOf<Rule>()
            val excludes = mutableListOf<Rule>()
            val invalid = mutableListOf<String>()
            text.lineSequence()
                .map { it.substringBefore('#').trim() }
                .filter { it.isNotEmpty() }
                .forEach { line ->
                    val exclude = line.startsWith("!")
                    val entry = line.removePrefix("!").trim()
                    val rule = parseRule(entry)
                    when {
                        rule == null -> invalid += line
                        exclude -> excludes += rule
                        else -> includes += rule
                    }
                }
            return ScopeMatcher(includes, excludes, invalid)
        }

        private fun parseRule(entry: String): Rule? {
            if (entry.isEmpty()) return null
            // CIDR
            if (entry.contains('/') && !entry.contains("://")) {
                val addr = entry.substringBefore('/')
                val prefix = entry.substringAfter('/').toIntOrNull() ?: return null
                return when {
                    HostValidator.isIpv4(addr) && prefix in 0..32 -> Rule.Cidr(ipv4ToBig(addr), prefix, 32)
                    HostValidator.isIpv6(addr) && prefix in 0..128 ->
                        ipv6ToBig(addr)?.let { Rule.Cidr(it, prefix, 128) }
                    else -> null
                }
            }
            // IPv4 range: a.b.c.d-e or a.b.c.d-w.x.y.z
            if (entry.contains('-') && entry.substringBefore('-').let(HostValidator::isIpv4)) {
                val startStr = entry.substringBefore('-').trim()
                val endPart = entry.substringAfter('-').trim()
                val endStr = when {
                    HostValidator.isIpv4(endPart) -> endPart
                    endPart.toIntOrNull() in 0..255 -> startStr.substringBeforeLast('.') + "." + endPart
                    else -> return null
                }
                val start = ipv4ToBig(startStr)
                val end = ipv4ToBig(endStr)
                return if (end >= start) Rule.Range(start, end, 32) else null
            }
            // Wildcard domain
            if (entry.startsWith("*.")) {
                val domain = entry.removePrefix("*.").lowercase().removeSuffix(".")
                return if (HostValidator.isHostname(domain)) Rule.Domain(domain, subdomainsOnly = true) else null
            }
            val host = HostValidator.normalize(entry)?.lowercase()?.removeSuffix(".") ?: return null
            return when {
                HostValidator.isIpv4(host) -> ipv4ToBig(host).let { Rule.Range(it, it, 32) }
                HostValidator.isIpv6(host) -> ipv6ToBig(host.substringBefore('%'))?.let { Rule.Range(it, it, 128) }
                else -> Rule.Domain(host, subdomainsOnly = false)
            }
        }

        private fun ipv4ToBig(ip: String): BigInteger =
            BigInteger.valueOf(ip.split('.').fold(0L) { acc, octet -> (acc shl 8) or octet.toLong() })

        /** Only called on strings that passed [HostValidator.isIpv6], so no DNS lookup happens. */
        private fun ipv6ToBig(ip: String): BigInteger? = try {
            InetAddress.getByName(ip).address.takeIf { it.size == 16 }?.let { BigInteger(1, it) }
        } catch (_: Exception) {
            null
        }
    }
}
