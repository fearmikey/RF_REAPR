package com.fearmikey.rf_reapr.domain.service

enum class CheckStatus { PASS, INFO, WARN, FAIL, ERROR }

data class DnsCheck(
    val id: String,
    val title: String,
    val status: CheckStatus,
    val summary: String,
    val detail: String = ""
)

/** Result of resolving one name with a resolver. */
data class LookupOutcome(
    val ips: List<String> = emptyList(),
    val nxdomain: Boolean = false,
    val error: String? = null
) {
    val resolved: Boolean get() = ips.isNotEmpty()
    val ipv4: List<String> get() = ips.filter { HostValidator.isIpv4(it) }
}

data class DomainComparison(val domain: String, val system: LookupOutcome, val trusted: LookupOutcome)

data class DnsObservations(
    val dnsServers: List<String>,
    val privateDnsActive: Boolean,
    val privateDnsServerName: String?,
    /** name -> expected fixed anycast IPs (e.g. one.one.one.one -> 1.1.1.1/1.0.0.1). */
    val sentinels: Map<String, LookupOutcome>,
    val nxdomainName: String,
    val nxdomain: LookupOutcome,
    val dnssecBogus: LookupOutcome,
    val rebinding: LookupOutcome,
    val rebindingTrusted: LookupOutcome,
    val comparisons: List<DomainComparison>,
    val trustedReachable: Boolean
)

/**
 * Turns raw DNS lookups into audit checks. Pure Kotlin so the verdict logic is unit-tested.
 */
object DnsAuditEvaluator {

    /** Names whose public answers never change: any other answer means rewriting. */
    val SENTINELS: Map<String, Set<String>> = mapOf(
        "one.one.one.one" to setOf("1.1.1.1", "1.0.0.1"),
        "dns.google" to setOf("8.8.8.8", "8.8.4.4")
    )

    const val DNSSEC_BOGUS_DOMAIN = "dnssec-failed.org"
    const val REBIND_DOMAIN = "10.0.0.1.nip.io"
    val COMPARISON_DOMAINS = listOf("wikipedia.org", "cloudflare.com", "google.com")

    private val knownResolvers = mapOf(
        "1.1.1.1" to "Cloudflare", "1.0.0.1" to "Cloudflare", "1.1.1.2" to "Cloudflare (malware)", "1.1.1.3" to "Cloudflare (family)",
        "8.8.8.8" to "Google", "8.8.4.4" to "Google",
        "9.9.9.9" to "Quad9", "149.112.112.112" to "Quad9",
        "208.67.222.222" to "OpenDNS", "208.67.220.220" to "OpenDNS",
        "94.140.14.14" to "AdGuard", "94.140.15.15" to "AdGuard",
        "76.76.2.0" to "Control D", "45.90.28.0" to "NextDNS", "45.90.30.0" to "NextDNS",
        "2606:4700:4700::1111" to "Cloudflare", "2606:4700:4700::1001" to "Cloudflare",
        "2001:4860:4860::8888" to "Google", "2001:4860:4860::8844" to "Google",
        "2620:fe::fe" to "Quad9"
    )

    fun describeResolver(ip: String): String = when {
        knownResolvers.containsKey(ip) -> knownResolvers.getValue(ip)
        HostValidator.isPrivateAddress(ip) -> "Local network (router or LAN DNS)"
        else -> "ISP or unknown public resolver"
    }

    private fun isBlockedAnswer(ips: List<String>) = ips.isNotEmpty() && ips.all { it == "0.0.0.0" || it == "::" || it == "127.0.0.1" || it == "::1" }

    fun evaluate(o: DnsObservations): List<DnsCheck> {
        val checks = mutableListOf<DnsCheck>()

        // 1. Which resolvers is the device using?
        checks += DnsCheck(
            "servers",
            "DNS servers",
            CheckStatus.INFO,
            if (o.dnsServers.isEmpty()) "No DNS servers reported by the system" else o.dnsServers.joinToString { "$it (${describeResolver(it)})" }
        )

        // Android reports NXDOMAIN and "no network" the same way, so if nothing resolved at
        // all the remaining checks would falsely pass.
        val systemWorking = o.comparisons.any { it.system.resolved } || o.sentinels.values.any { it.resolved }
        if (!systemWorking) {
            checks += DnsCheck(
                "connectivity", "System DNS", CheckStatus.ERROR,
                "No test domain resolved",
                "The device couldn't resolve any test name. Check that you're connected, then try again."
            )
            return checks
        }

        // 2. Encrypted DNS
        checks += if (o.privateDnsActive) {
            DnsCheck(
                "encryption", "Encrypted DNS", CheckStatus.PASS,
                "Private DNS (DNS-over-TLS) is active" + (o.privateDnsServerName?.let { " via $it" } ?: " (automatic)"),
                "Queries are encrypted, so the Wi-Fi operator and ISP can't read or tamper with them."
            )
        } else {
            DnsCheck(
                "encryption", "Encrypted DNS", CheckStatus.WARN,
                "DNS queries are sent unencrypted",
                "Anyone on the path (Wi-Fi operator, ISP) can see every domain you look up and change the answers. Turn on Settings → Network → Private DNS."
            )
        }

        // 3. Sentinel names with fixed answers
        o.sentinels.forEach { (name, outcome) ->
            val expected = SENTINELS[name].orEmpty()
            val v4 = outcome.ipv4
            checks += when {
                outcome.error != null && !outcome.nxdomain -> DnsCheck("sentinel:$name", "Answer integrity ($name)", CheckStatus.ERROR, "Lookup failed: ${outcome.error}")
                !outcome.resolved -> DnsCheck(
                    "sentinel:$name", "Answer integrity ($name)", CheckStatus.WARN,
                    "$name was blocked (no answer)",
                    "This name always resolves to ${expected.joinToString()}. Your resolver refuses it, which is typical of networks that block third-party DNS."
                )
                v4.isNotEmpty() && v4.all { it in expected } -> DnsCheck(
                    "sentinel:$name", "Answer integrity ($name)", CheckStatus.PASS,
                    "Correct answer (${v4.joinToString()})"
                )
                isBlockedAnswer(outcome.ips) -> DnsCheck(
                    "sentinel:$name", "Answer integrity ($name)", CheckStatus.WARN,
                    "Blocked by a DNS filter (${outcome.ips.joinToString()})"
                )
                v4.any { HostValidator.isPrivateAddress(it) } -> DnsCheck(
                    "sentinel:$name", "Answer integrity ($name)", CheckStatus.WARN,
                    "Redirected to a local address (${v4.joinToString()})",
                    "A local DNS filter or captive portal is answering in place of the real server."
                )
                else -> DnsCheck(
                    "sentinel:$name", "Answer integrity ($name)", CheckStatus.FAIL,
                    "Answer was rewritten: got ${outcome.ips.joinToString()}, expected ${expected.joinToString()}",
                    "This name has fixed anycast addresses worldwide, so a different public IP means something on the network is modifying DNS responses."
                )
            }
        }

        // 4. NXDOMAIN hijacking
        checks += when {
            o.nxdomain.nxdomain || (!o.nxdomain.resolved && o.nxdomain.error == null) -> DnsCheck(
                "nxdomain", "NXDOMAIN handling", CheckStatus.PASS,
                "Nonexistent names correctly return \"not found\""
            )
            o.nxdomain.resolved && !isBlockedAnswer(o.nxdomain.ips) -> DnsCheck(
                "nxdomain", "NXDOMAIN handling", CheckStatus.FAIL,
                "Nonexistent names resolve to ${o.nxdomain.ips.joinToString()}",
                "\"${o.nxdomainName}\" doesn't exist, but your resolver returned an address. ISPs do this to show ad/search pages for typos; it also breaks security tools and can expose you to phishing."
            )
            o.nxdomain.resolved -> DnsCheck("nxdomain", "NXDOMAIN handling", CheckStatus.PASS, "Nonexistent names are sinkholed (${o.nxdomain.ips.joinToString()})")
            else -> DnsCheck("nxdomain", "NXDOMAIN handling", CheckStatus.ERROR, "Lookup failed: ${o.nxdomain.error}")
        }

        // 5. DNSSEC validation
        checks += when {
            o.dnssecBogus.resolved -> DnsCheck(
                "dnssec", "DNSSEC validation", CheckStatus.WARN,
                "Resolver does not validate DNSSEC",
                "$DNSSEC_BOGUS_DOMAIN has deliberately broken signatures and resolved anyway, so forged answers for signed domains won't be caught. Validating resolvers include Cloudflare, Google and Quad9."
            )
            o.dnssecBogus.nxdomain || o.dnssecBogus.error != null || !o.dnssecBogus.resolved -> DnsCheck(
                "dnssec", "DNSSEC validation", CheckStatus.PASS,
                "Resolver rejected a domain with invalid DNSSEC signatures"
            )
            else -> DnsCheck("dnssec", "DNSSEC validation", CheckStatus.ERROR, "Inconclusive")
        }

        // 6. DNS rebinding protection
        checks += when {
            !o.rebindingTrusted.resolved && !o.rebinding.resolved -> DnsCheck(
                "rebinding", "DNS rebinding protection", CheckStatus.ERROR,
                "Test service unreachable; couldn't check"
            )
            o.rebinding.ipv4.any { HostValidator.isPrivateAddress(it) } -> DnsCheck(
                "rebinding", "DNS rebinding protection", CheckStatus.INFO,
                "Public names can resolve to private addresses",
                "A malicious website could use DNS rebinding to reach devices on your LAN (router admin pages, IoT). Routers such as OpenWrt, pfSense and Pi-hole can filter private answers."
            )
            else -> DnsCheck(
                "rebinding", "DNS rebinding protection", CheckStatus.PASS,
                "Private-address answers for public names are filtered"
            )
        }

        // 7. Cross-check popular domains against trusted DoH
        if (!o.trustedReachable) {
            checks += DnsCheck(
                "compare", "Comparison with trusted resolvers", CheckStatus.ERROR,
                "Couldn't reach Cloudflare or Google DNS-over-HTTPS",
                "The network may block DoH. The other checks above don't need it."
            )
        } else {
            val mismatches = o.comparisons.filter { c ->
                val sys = c.system.ipv4
                val trusted = c.trusted.ipv4
                sys.isNotEmpty() && trusted.isNotEmpty() && !overlaps(sys, trusted)
            }
            val privateAnswers = o.comparisons.filter { c -> c.system.ipv4.any { HostValidator.isPrivateAddress(it) } }
            checks += when {
                privateAnswers.isNotEmpty() -> DnsCheck(
                    "compare", "Comparison with trusted resolvers", CheckStatus.WARN,
                    "Popular sites resolve to local addresses: ${privateAnswers.joinToString { "${it.domain} → ${it.system.ipv4.joinToString()}" }}",
                    "Usually a captive portal or DNS filter, but also how local DNS spoofing attacks look."
                )
                mismatches.size >= 2 -> DnsCheck(
                    "compare", "Comparison with trusted resolvers", CheckStatus.WARN,
                    "${mismatches.size} of ${o.comparisons.size} domains got different answers",
                    mismatches.joinToString("\n") { "${it.domain}: system ${it.system.ipv4.joinToString()} vs trusted ${it.trusted.ipv4.joinToString()}" } +
                        "\n\nCDNs can legitimately return different servers by location, so treat this as a hint rather than proof."
                )
                else -> DnsCheck(
                    "compare", "Comparison with trusted resolvers", CheckStatus.PASS,
                    "Answers match Cloudflare/Google DNS-over-HTTPS"
                )
            }
        }
        return checks
    }

    /** Same address, or same /24 (CDN pools rotate within a block). */
    private fun overlaps(a: List<String>, b: List<String>): Boolean {
        if (a.any { it in b }) return true
        val prefixesB = b.map { it.substringBeforeLast('.') }.toSet()
        return a.any { it.substringBeforeLast('.') in prefixesB }
    }

    fun overall(checks: List<DnsCheck>): CheckStatus {
        val scored = checks.filter { it.status != CheckStatus.INFO && it.status != CheckStatus.ERROR }
        return when {
            scored.any { it.status == CheckStatus.FAIL } -> CheckStatus.FAIL
            scored.any { it.status == CheckStatus.WARN } -> CheckStatus.WARN
            scored.isEmpty() -> CheckStatus.ERROR
            else -> CheckStatus.PASS
        }
    }
}
