package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.OpenPort
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.domain.model.Vulnerability
import com.fearmikey.rf_reapr.domain.repository.AlertSeverity
import com.fearmikey.rf_reapr.domain.repository.ArpAlert
import com.fearmikey.rf_reapr.domain.repository.TlsCipherScannerRepository.CipherSuiteInfo
import com.fearmikey.rf_reapr.domain.repository.TlsCipherScannerRepository.ProtocolSupport
import com.fearmikey.rf_reapr.domain.repository.TlsCipherScannerRepository.TlsScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FindingAdaptersTest {

    @Test
    fun openPortsProduceExposureAndConfirmedCveFindings() {
        val drafts = FindingAdapters.openPorts(
            "10.0.0.5",
            listOf(
                OpenPort(23, "Telnet"),
                OpenPort(22, "SSH"), // no exposure risk in the catalog
                OpenPort(
                    80, "HTTP", vulnerabilities = listOf(
                        Vulnerability("CVE-2021-1", "bad", RiskLevel.CRITICAL, isConfirmed = true),
                        Vulnerability("CVE-2021-2", "maybe", RiskLevel.HIGH, isConfirmed = false)
                    )
                )
            )
        )
        assertEquals(listOf("Telnet exposed (23/tcp)", "CVE-2021-1 in HTTP (80/tcp)"), drafts.map { it.title })
        assertEquals(RiskLevel.HIGH, drafts[0].severity)
        assertTrue(drafts.all { it.target == "10.0.0.5" })
    }

    @Test
    fun tlsOnlyReportsFinishedScansWithWeakConfig() {
        assertTrue(FindingAdapters.tls(TlsScanResult(host = "a.com", isFinished = false)).isEmpty())
        val drafts = FindingAdapters.tls(
            TlsScanResult(
                host = "a.com", port = 8443, isFinished = true,
                protocols = listOf(
                    ProtocolSupport("TLSv1", enabled = true, riskLevel = RiskLevel.HIGH),
                    ProtocolSupport("TLSv1.3", enabled = true, riskLevel = RiskLevel.LOW)
                ),
                supportedCiphers = listOf(CipherSuiteInfo("TLSv1.2", "TLS_RSA_WITH_RC4_128_SHA", RiskLevel.CRITICAL))
            )
        )
        assertEquals(listOf("TLSv1 enabled", "Weak cipher suites accepted"), drafts.map { it.title })
        assertEquals("a.com:8443", drafts.first().target)
        assertEquals(RiskLevel.CRITICAL, drafts[1].severity)
    }

    @Test
    fun arpTitlesAreStableAcrossDifferentMacs() {
        fun alert(old: String, new: String) =
            ArpAlert(0, "Gateway MAC changed from $old to $new", old, new, AlertSeverity.CRITICAL)
        val a = FindingAdapters.arp("10.0.0.1", alert("aa:bb:cc:dd:ee:01", "aa:bb:cc:dd:ee:02"))!!
        val b = FindingAdapters.arp("10.0.0.1", alert("aa:bb:cc:dd:ee:03", "aa:bb:cc:dd:ee:04"))!!
        assertEquals(a.title, b.title)
        assertEquals(RiskLevel.CRITICAL, a.severity)
        assertNull(FindingAdapters.arp("10.0.0.1", ArpAlert(0, "Monitoring started", "", "", AlertSeverity.INFO)))
    }

    @Test
    fun dnsAuditKeepsOnlyFailuresAndWarnings() {
        val drafts = FindingAdapters.dnsAudit(
            listOf(
                DnsCheck("a", "DNSSEC validation", CheckStatus.FAIL, "Not validating", ""),
                DnsCheck("b", "Rebinding", CheckStatus.WARN, "Private answers allowed", "detail"),
                DnsCheck("c", "Resolver", CheckStatus.PASS, "ok", "")
            )
        )
        assertEquals(listOf(RiskLevel.HIGH, RiskLevel.MEDIUM), drafts.map { it.severity })
    }
}

class NetworkWatchEvaluatorTest {
    private fun dev(ip: String, mac: String? = null) = NetworkWatchEvaluator.SeenDevice(ip, mac, null)

    @Test
    fun firstSweepRecordsBaselineSilently() {
        val r = NetworkWatchEvaluator.evaluate(null, listOf(dev("10.0.0.2"), dev("10.0.0.3", "AA:BB:CC:DD:EE:FF")))
        assertTrue(r.firstSweep)
        assertTrue(r.newDevices.isEmpty())
        assertEquals(setOf("ip:10.0.0.2", "mac:aa:bb:cc:dd:ee:ff"), r.updatedBaseline)
    }

    @Test
    fun newDevicesAreReportedOnceAndMacSurvivesIpChange() {
        val baseline = setOf("ip:10.0.0.2", "mac:aa:bb:cc:dd:ee:ff")
        val r = NetworkWatchEvaluator.evaluate(baseline, listOf(dev("10.0.0.9", "aa:bb:cc:dd:ee:ff"), dev("10.0.0.7")))
        assertEquals(listOf("10.0.0.7"), r.newDevices.map { it.ip })
        val again = NetworkWatchEvaluator.evaluate(r.updatedBaseline, listOf(dev("10.0.0.7")))
        assertTrue(again.newDevices.isEmpty())
    }
}
