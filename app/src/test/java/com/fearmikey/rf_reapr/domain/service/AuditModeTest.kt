package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.AuditCheck
import com.fearmikey.rf_reapr.domain.model.AuditConfig
import com.fearmikey.rf_reapr.domain.model.AuditHost
import com.fearmikey.rf_reapr.domain.model.AuditRunState
import com.fearmikey.rf_reapr.domain.model.AuditStepState
import com.fearmikey.rf_reapr.domain.model.AuditStepStatus
import com.fearmikey.rf_reapr.domain.model.AuditTargets
import com.fearmikey.rf_reapr.domain.model.FindingDraft
import com.fearmikey.rf_reapr.domain.model.FindingTools
import com.fearmikey.rf_reapr.domain.model.OpenPort
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.domain.model.WifiAccessPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditModeTest {

    // ---- Planning ------------------------------------------------------------------------

    @Test
    fun tlsPullsInPortsAndDiscoveryInExecutionOrder() {
        val plan = AuditPlanner.plan(AuditConfig(setOf(AuditCheck.TLS, AuditCheck.WIFI)), passiveMode = false)
        assertEquals(listOf(AuditCheck.WIFI, AuditCheck.DISCOVERY, AuditCheck.PORTS, AuditCheck.TLS), plan.map { it.check })
        assertEquals(setOf(AuditCheck.DISCOVERY, AuditCheck.PORTS), plan.filter { it.autoAdded }.map { it.check }.toSet())
        assertTrue(plan.all { it.skipReason == null })
    }

    @Test
    fun passiveModeSkipsActiveChecksOnly() {
        val plan = AuditPlanner.plan(AuditConfig(setOf(AuditCheck.WIFI, AuditCheck.CAPTIVE_PORTAL, AuditCheck.DNS, AuditCheck.SNMP)), passiveMode = true)
        val runnable = plan.filter { it.skipReason == null }.map { it.check }
        assertEquals(listOf(AuditCheck.WIFI, AuditCheck.CAPTIVE_PORTAL), runnable)
        assertEquals("Passive Mode is on", plan.first { it.check == AuditCheck.SNMP }.skipReason)
        assertEquals("Passive Mode is on", plan.first { it.check == AuditCheck.DISCOVERY }.skipReason)
    }

    @Test
    fun manualTargetsReplaceDiscovery() {
        val plan = AuditPlanner.plan(
            AuditConfig(setOf(AuditCheck.PORTS), targets = AuditTargets.Manual(listOf("10.0.0.5"))),
            passiveMode = false
        )
        assertEquals(listOf(AuditCheck.PORTS), plan.map { it.check })

        val empty = AuditPlanner.plan(AuditConfig(setOf(AuditCheck.PORTS), targets = AuditTargets.Manual(emptyList())), false)
        assertEquals("No valid targets entered", empty.single().skipReason)
    }

    @Test
    fun parseTargetsExpandsCidrsAndRangesAndRejectsWideNetworks() {
        val parsed = AuditPlanner.parseTargets("10.0.0.0/30, 192.168.1.20-22\nNAS.lan # comment\n10.0.0.0/16 bad..host https://Example.com:8443/x")
        assertEquals(
            listOf("10.0.0.1", "10.0.0.2", "192.168.1.20", "192.168.1.21", "192.168.1.22", "nas.lan", "example.com"),
            parsed.hosts
        )
        assertEquals(listOf("10.0.0.0/16", "bad..host"), parsed.invalid)
        assertFalse(parsed.truncated)

        assertEquals(listOf("10.0.0.7"), AuditPlanner.parseTargets("10.0.0.7/32").hosts)
        assertEquals(listOf("10.0.0.6", "10.0.0.7"), AuditPlanner.parseTargets("10.0.0.7/31").hosts)
        assertEquals(254, AuditPlanner.parseTargets("192.168.4.77/24").hosts.size)
        assertEquals(listOf("1.2.3.9-3"), AuditPlanner.parseTargets("1.2.3.9-3").invalid)
    }

    @Test
    fun parseTargetsDedupesAndTruncates() {
        val parsed = AuditPlanner.parseTargets("10.0.0.1 10.0.0.1 10.0.0.0/29", limit = 4)
        assertEquals(listOf("10.0.0.1", "10.0.0.2", "10.0.0.3", "10.0.0.4"), parsed.hosts)
        assertTrue(parsed.truncated)
    }

    @Test
    fun scopeFilterDropsOutOfScopeAndExcludedHosts() {
        val verdicts = mapOf(
            "10.0.0.1" to ScopeMatcher.Verdict.IN_SCOPE,
            "10.0.0.2" to ScopeMatcher.Verdict.EXCLUDED,
            "10.0.0.3" to ScopeMatcher.Verdict.OUT_OF_SCOPE,
            "nas.lan" to ScopeMatcher.Verdict.UNKNOWN
        )
        val (allowed, dropped) = AuditPlanner.partitionByScope(verdicts.keys.toList()) { verdicts.getValue(it) }
        assertEquals(listOf("10.0.0.1", "nas.lan"), allowed)
        assertEquals(listOf("10.0.0.2", "10.0.0.3"), dropped)
    }

    @Test
    fun tlsEndpointsComeFromTlsPortsOnly() {
        val hosts = listOf(
            AuditHost("10.0.0.1", openPorts = listOf(OpenPort(22, "SSH"), OpenPort(443, "HTTPS"))),
            AuditHost("10.0.0.2", openPorts = listOf(OpenPort(8443, "HTTPS-Alt"), OpenPort(993, "IMAPS")))
        )
        assertEquals(listOf("10.0.0.1" to 443, "10.0.0.2" to 8443, "10.0.0.2" to 993), AuditPlanner.tlsEndpoints(hosts))
        assertEquals(1, AuditPlanner.tlsEndpoints(hosts, limit = 1).size)
    }

    // ---- Scoring -------------------------------------------------------------------------

    private fun draft(severity: RiskLevel, title: String = severity.name, target: String = "t") =
        FindingDraft(FindingTools.MANUAL, target, severity, title, "")

    @Test
    fun cleanAuditIsGradeA() {
        val score = AuditScoring.score(emptyList())
        assertEquals(100, score.score)
        assertEquals("A", score.grade)
    }

    @Test
    fun severeFindingsCapTheGrade() {
        assertEquals("D", AuditScoring.score(listOf(draft(RiskLevel.CRITICAL))).grade) // 75 would be B
        assertEquals("C", AuditScoring.score(listOf(draft(RiskLevel.HIGH))).grade)     // 90 would be A
        assertEquals("A", AuditScoring.score(listOf(draft(RiskLevel.LOW), draft(RiskLevel.MEDIUM))).grade)
        assertEquals("F", AuditScoring.score(List(5) { draft(RiskLevel.CRITICAL, "c$it") }).grade)
    }

    @Test
    fun duplicateFindingsCountOnce() {
        val score = AuditScoring.score(listOf(draft(RiskLevel.HIGH), draft(RiskLevel.HIGH), draft(RiskLevel.HIGH, target = "other")))
        assertEquals(2, score.counts[RiskLevel.HIGH])
        assertEquals(80, score.score)
    }

    // ---- Adapters ------------------------------------------------------------------------

    private fun ap(ssid: String, caps: String, connected: Boolean = false, rogue: String? = null, bssid: String = "aa:bb:cc:00:00:01") =
        WifiAccessPoint(
            ssid = ssid, bssid = bssid, signalLevel = -50, frequency = 2437, bandwidth = 20, capabilities = caps,
            isConnected = connected, isRogueSuspect = rogue != null, rogueReason = rogue
        )

    @Test
    fun wifiReportsWeakConnectedNetworkAndWps() {
        val open = FindingAdapters.wifi(listOf(ap("Cafe", "[ESS]", connected = true)))
        assertEquals(listOf("Connected Wi-Fi uses Open"), open.map { it.title })
        assertEquals(RiskLevel.HIGH, open.single().severity)
        assertEquals("Wi-Fi \"Cafe\"", open.single().target)

        val wps = FindingAdapters.wifi(listOf(ap("Home", "[WPA2-PSK-CCMP][ESS][WPS]", connected = true)))
        assertEquals(listOf("WPS enabled on connected Wi-Fi"), wps.map { it.title })

        val transition = FindingAdapters.wifi(listOf(ap("Home", "[WPA2-PSK+SAE-CCMP][RSN-PSK+SAE-CCMP][ESS]", connected = true)))
        assertEquals(listOf("Connected Wi-Fi allows legacy security modes"), transition.map { it.title })
        assertEquals(RiskLevel.LOW, transition.single().severity)
    }

    @Test
    fun wifiIgnoresNeighboursButGroupsEvilTwins() {
        val drafts = FindingAdapters.wifi(
            listOf(
                ap("Neighbour", "[ESS]"),
                ap("Corp", "[ESS]", rogue = "Open AP using the name", bssid = "de:ad:00:00:00:01"),
                ap("Corp", "[ESS]", rogue = "Open AP using the name", bssid = "de:ad:00:00:00:02")
            )
        )
        assertEquals(listOf("Possible evil twin of \"Corp\""), drafts.map { it.title })
        assertTrue(drafts.single().detail.contains("de:ad:00:00:00:02"))
    }

    @Test
    fun captivePortalOnlyReportsInterceptionPortalsAndProxies() {
        fun verdict(status: PortalStatus, title: String) = PortalVerdict(status, title, "detail", null)
        assertTrue(FindingAdapters.captivePortal("Wi-Fi", verdict(PortalStatus.OPEN, "Direct internet access")).isEmpty())
        assertTrue(FindingAdapters.captivePortal("Wi-Fi", verdict(PortalStatus.OFFLINE, "No internet access")).isEmpty())
        assertTrue(FindingAdapters.captivePortal("Wi-Fi", verdict(PortalStatus.PARTIAL, "Internet works, some checks blocked")).isEmpty())

        val mitm = FindingAdapters.captivePortal("Wi-Fi", verdict(PortalStatus.INTERCEPTED, "HTTPS interception detected")).single()
        assertEquals(RiskLevel.HIGH, mitm.severity)
        assertEquals("Wi-Fi network", mitm.target)
        assertEquals(RiskLevel.LOW, FindingAdapters.captivePortal(null, verdict(PortalStatus.PARTIAL, "Open internet through a proxy")).single().severity)
    }

    // ---- Report --------------------------------------------------------------------------

    @Test
    fun markdownReportListsChecksFindingsAndHosts() {
        val state = AuditRunState(
            config = AuditConfig(setOf(AuditCheck.PORTS)),
            steps = listOf(
                AuditStepState(
                    AuditCheck.PORTS, AuditStepStatus.DONE, summary = "2 open ports",
                    findings = listOf(FindingDraft(FindingTools.PORT_SCANNER, "10.0.0.5", RiskLevel.HIGH, "Telnet exposed (23/tcp)", "Banner: a|b", "Disable it"))
                ),
                AuditStepState(AuditCheck.DISCOVERY, AuditStepStatus.DONE, autoAdded = true, summary = "3 devices found"),
                AuditStepState(AuditCheck.SNMP, AuditStepStatus.CANCELLED, summary = "Cancelled")
            ),
            hosts = listOf(AuditHost("10.0.0.5", vendor = "Acme", openPorts = listOf(OpenPort(23, "Telnet")))),
            outOfScope = listOf("10.0.0.9"),
            startedAt = 0L,
            finishedAt = 75_000L,
            projectName = "Acme <internal>"
        )
        val md = AuditReportBuilder.markdown(state, "9.9")
        assertTrue(md.contains("**Posture grade:** C (90/100)"))
        assertTrue(md.contains("**Project:** Acme &lt;internal&gt;"))
        assertTrue(md.contains("**Duration:** 1m 15s"))
        assertTrue(md.contains("| Host discovery | Done (dependency) | 3 devices found |"))
        assertTrue(md.contains("### 1. [HIGH] Telnet exposed (23/tcp)"))
        assertTrue(md.contains("**Remediation:** Disable it"))
        assertTrue(md.contains("| 10.0.0.5 | Acme | 23 Telnet |  |"))
        assertTrue(md.contains("10.0.0.9"))
        assertTrue(md.contains("_Incomplete: SNMP default communities."))
    }

    @Test
    fun emptyRunSaysNoFindings() {
        val md = AuditReportBuilder.markdown(AuditRunState(steps = emptyList(), startedAt = 0L), "1")
        assertTrue(md.contains("No findings."))
        assertNull(Regex("## Hosts").find(md))
    }
}
