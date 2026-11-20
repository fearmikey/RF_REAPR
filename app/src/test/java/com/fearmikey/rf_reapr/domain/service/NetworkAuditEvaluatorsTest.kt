package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.domain.model.SnmpResult
import com.fearmikey.rf_reapr.domain.repository.AlertSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** DNS auditor, captive portal, SNMP and ARP monitor verdict logic. */
class NetworkAuditEvaluatorsTest {

    private fun ok(vararg ips: String) = LookupOutcome(ips = ips.toList())
    private val nx = LookupOutcome(nxdomain = true)

    private fun observations(
        sentinel: LookupOutcome = ok("1.1.1.1", "1.0.0.1"),
        nxdomain: LookupOutcome = nx,
        dnssec: LookupOutcome = nx,
        privateDns: Boolean = true
    ) = DnsObservations(
        dnsServers = listOf("192.168.1.1"),
        privateDnsActive = privateDns,
        privateDnsServerName = null,
        sentinels = mapOf("one.one.one.one" to sentinel, "dns.google" to ok("8.8.8.8")),
        nxdomainName = "rfreapr-x.com",
        nxdomain = nxdomain,
        dnssecBogus = dnssec,
        rebinding = nx,
        rebindingTrusted = ok("10.0.0.1"),
        comparisons = listOf(DomainComparison("wikipedia.org", ok("185.15.59.224"), ok("185.15.59.224"))),
        trustedReachable = true
    )

    @Test
    fun `healthy dns passes`() {
        val checks = DnsAuditEvaluator.evaluate(observations())
        assertEquals(CheckStatus.PASS, DnsAuditEvaluator.overall(checks))
    }

    @Test
    fun `rewritten sentinel and nxdomain hijack fail`() {
        val checks = DnsAuditEvaluator.evaluate(observations(sentinel = ok("203.0.113.9"), nxdomain = ok("198.51.100.1")))
        assertEquals(CheckStatus.FAIL, DnsAuditEvaluator.overall(checks))
        assertEquals(CheckStatus.FAIL, checks.first { it.id == "nxdomain" }.status)
        assertEquals(CheckStatus.FAIL, checks.first { it.id == "sentinel:one.one.one.one" }.status)
    }

    @Test
    fun `non validating resolver and plaintext dns warn`() {
        val checks = DnsAuditEvaluator.evaluate(observations(dnssec = ok("1.2.3.4"), privateDns = false))
        assertEquals(CheckStatus.WARN, checks.first { it.id == "dnssec" }.status)
        assertEquals(CheckStatus.WARN, checks.first { it.id == "encryption" }.status)
        assertEquals(CheckStatus.WARN, DnsAuditEvaluator.overall(checks))
    }

    @Test
    fun `offline device is an error not a pass`() {
        val o = observations(sentinel = nx).copy(
            sentinels = mapOf("one.one.one.one" to nx, "dns.google" to nx),
            comparisons = listOf(DomainComparison("wikipedia.org", nx, ok("185.15.59.224")))
        )
        val checks = DnsAuditEvaluator.evaluate(o)
        assertEquals(CheckStatus.ERROR, DnsAuditEvaluator.overall(checks))
    }

    @Test
    fun `captive portal verdicts`() {
        val probes = CaptivePortalAnalyzer.PROBES
        val open = probes.map { PortalProbeResult(it, code = it.expectedCode, bodySnippet = it.expectedBody) }
        assertEquals(PortalStatus.OPEN, CaptivePortalAnalyzer.evaluate(open, null).status)

        val redirected = probes.map {
            if (it.isHttps) PortalProbeResult(it, error = "timeout")
            else PortalProbeResult(it, code = 302, location = "http://portal.example/login")
        }
        val v = CaptivePortalAnalyzer.evaluate(redirected, null)
        assertEquals(PortalStatus.CAPTIVE_PORTAL, v.status)
        assertEquals("http://portal.example/login", v.loginUrl)

        val tls = probes.map {
            if (it.isHttps) PortalProbeResult(it, error = "cert", tlsError = true)
            else PortalProbeResult(it, code = it.expectedCode, bodySnippet = it.expectedBody)
        }
        assertEquals(PortalStatus.INTERCEPTED, CaptivePortalAnalyzer.evaluate(tls, null).status)

        val offline = probes.map { PortalProbeResult(it, error = "unreachable") }
        assertEquals(PortalStatus.OFFLINE, CaptivePortalAnalyzer.evaluate(offline, null).status)
    }

    @Test
    fun `snmp helpers and findings`() {
        assertEquals("1.3.6.1.2.1.1", SnmpTools.normalizeOid(" .1.3.6.1.2.1.1 "))
        assertNull(SnmpTools.normalizeOid("1.3.x"))
        assertEquals("Cisco", SnmpTools.vendorFromObjectId(".1.3.6.1.4.1.9.1.1208"))
        assertEquals("1 Gbps", SnmpTools.formatSpeed(1_000_000_000))
        val findings = SnmpTools.findings(SnmpResult(targetIp = "10.0.0.1", community = "public"), listOf("public", "private"))
        assertEquals(RiskLevel.HIGH, findings.first().severity)
    }

    @Test
    fun `arp monitor detects gateway mac change and extra hop`() {
        val base = NetworkSnapshot("Home", "aa:aa:aa:aa:aa:01", "192.168.1.1", "11:11:11:11:11:11", listOf("192.168.1.1"))
        assertTrue(ArpMonitorEvaluator.evaluate(base, base).isEmpty())

        val changed = base.copy(gatewayMac = "22:22:22:22:22:22")
        assertEquals(AlertSeverity.CRITICAL, ArpMonitorEvaluator.evaluate(base, changed).single().severity)

        val shared = base.copy(neighbors = mapOf("192.168.1.1" to "11:11:11:11:11:11", "192.168.1.66" to "11:11:11:11:11:11"))
        assertNotNull(ArpMonitorEvaluator.evaluate(null, shared).find { it.message.contains("192.168.1.66") })

        val hop = base.copy(firstHop = "192.168.1.66")
        assertEquals(AlertSeverity.CRITICAL, ArpMonitorEvaluator.evaluate(base, hop).single().severity)
        assertTrue(ArpMonitorEvaluator.evaluate(base, base.copy(firstHop = "192.168.1.1")).isEmpty())

        val dns = base.copy(dnsServers = listOf("10.9.9.9"))
        assertEquals(AlertSeverity.WARNING, ArpMonitorEvaluator.evaluate(base, dns).single().severity)
    }

    @Test
    fun `parses first hop from ping output`() {
        assertEquals("192.168.1.1", ArpMonitorEvaluator.parseFirstHop("PING 1.1.1.1 (1.1.1.1) 56(84) bytes of data.\nFrom 192.168.1.1: icmp_seq=1 Time to live exceeded"))
        assertEquals("10.0.0.1", ArpMonitorEvaluator.parseFirstHop("From 10.0.0.1 icmp_seq=1 Time to live exceeded"))
        assertNull(ArpMonitorEvaluator.parseFirstHop("64 bytes from 1.1.1.1: icmp_seq=1 ttl=57 time=9 ms"))
    }
}
