package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.Finding
import com.fearmikey.rf_reapr.domain.model.FindingDraft
import com.fearmikey.rf_reapr.domain.model.FindingStatus
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.domain.service.ScopeMatcher.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FindingMergerTest {

    private fun draft(title: String = "Telnet exposed", target: String = "10.0.0.5", sev: RiskLevel = RiskLevel.HIGH) =
        FindingDraft("Port Scanner", target, sev, title, "Port 23 open")

    @Test
    fun fingerprintIsCaseAndWhitespaceInsensitive() {
        assertEquals(
            FindingMerger.fingerprint("Port Scanner", "10.0.0.5", "Telnet  exposed"),
            FindingMerger.fingerprint(" port scanner", "10.0.0.5 ", "TELNET exposed")
        )
    }

    @Test
    fun newDraftsAreInsertedAsOpen() {
        val plan = FindingMerger.plan("p1", listOf(draft()), emptyMap(), now = 100)
        assertEquals(1, plan.inserts.size)
        assertTrue(plan.updates.isEmpty())
        with(plan.inserts.single()) {
            assertEquals(FindingStatus.OPEN, status)
            assertEquals("p1", projectId)
            assertEquals(100, firstSeen)
            assertEquals(1, occurrences)
        }
    }

    @Test
    fun duplicatesInOneBatchCollapseToMostSevere() {
        val plan = FindingMerger.plan(null, listOf(draft(sev = RiskLevel.LOW), draft(sev = RiskLevel.CRITICAL)), emptyMap(), 1)
        assertEquals(RiskLevel.CRITICAL, plan.inserts.single().severity)
    }

    @Test
    fun blankTitlesOrTargetsAreIgnored() {
        val plan = FindingMerger.plan(null, listOf(draft(title = " "), draft(target = "")), emptyMap(), 1)
        assertTrue(plan.isEmpty)
    }

    @Test
    fun reSeenFindingUpdatesCountersAndKeepsTriage() {
        val existing = FindingMerger.plan("p", listOf(draft()), emptyMap(), 10).inserts.single()
            .copy(id = 7, status = FindingStatus.ACCEPTED, notes = "ok per client")
        val plan = FindingMerger.plan("p", listOf(draft(sev = RiskLevel.MEDIUM)), mapOf(existing.fingerprint to existing), 20)
        val updated = plan.updates.single()
        assertEquals(7, updated.id)
        assertEquals(FindingStatus.ACCEPTED, updated.status)
        assertEquals(2, updated.occurrences)
        assertEquals(10, updated.firstSeen)
        assertEquals(20, updated.lastSeen)
        assertEquals(RiskLevel.MEDIUM, updated.severity)
        assertEquals("ok per client", updated.notes)
        assertTrue(plan.reopened.isEmpty())
    }

    @Test
    fun fixedFindingThatReappearsIsReopened() {
        val existing = FindingMerger.plan("p", listOf(draft()), emptyMap(), 10).inserts.single()
            .copy(id = 3, status = FindingStatus.FIXED)
        val plan = FindingMerger.plan("p", listOf(draft()), mapOf(existing.fingerprint to existing), 20)
        assertEquals(FindingStatus.OPEN, plan.updates.single().status)
        assertEquals(1, plan.reopened.size)
    }
}

class ScopeMatcherTest {

    private val scope = ScopeMatcher.parse(
        """
        # Acme internal
        10.0.0.0/24
        192.168.1.20-40
        172.16.5.9
        2001:db8::/32
        example.com
        *.corp.acme.test
        !10.0.0.1
        !vpn.example.com
        not a valid entry!!
        """.trimIndent()
    )

    @Test
    fun emptyScopeMeansNoScope() {
        assertEquals(Verdict.NO_SCOPE, ScopeMatcher.parse("").check("10.0.0.1"))
        assertEquals(Verdict.NO_SCOPE, ScopeMatcher.parse("  \n# only a comment").check("10.0.0.1"))
    }

    @Test
    fun ipv4CidrRangeAndSingleHost() {
        assertEquals(Verdict.IN_SCOPE, scope.check("10.0.0.200"))
        assertEquals(Verdict.OUT_OF_SCOPE, scope.check("10.0.1.1"))
        assertEquals(Verdict.IN_SCOPE, scope.check("192.168.1.20"))
        assertEquals(Verdict.IN_SCOPE, scope.check("192.168.1.40"))
        assertEquals(Verdict.OUT_OF_SCOPE, scope.check("192.168.1.41"))
        assertEquals(Verdict.IN_SCOPE, scope.check("172.16.5.9"))
        assertEquals(Verdict.OUT_OF_SCOPE, scope.check("172.16.5.10"))
    }

    @Test
    fun exclusionsWin() {
        assertEquals(Verdict.EXCLUDED, scope.check("10.0.0.1"))
        assertEquals(Verdict.EXCLUDED, scope.check("https://vpn.example.com/login"))
    }

    @Test
    fun domainsMatchSubdomainsAndWildcardsExcludeApex() {
        assertEquals(Verdict.IN_SCOPE, scope.check("example.com"))
        assertEquals(Verdict.IN_SCOPE, scope.check("WWW.Example.com."))
        assertEquals(Verdict.OUT_OF_SCOPE, scope.check("notexample.com"))
        assertEquals(Verdict.IN_SCOPE, scope.check("mail.corp.acme.test"))
        assertEquals(Verdict.OUT_OF_SCOPE, scope.check("corp.acme.test"))
    }

    @Test
    fun urlsAndPortsAreNormalized() {
        assertEquals(Verdict.IN_SCOPE, scope.check("https://10.0.0.7:8443/admin"))
        assertEquals(Verdict.IN_SCOPE, scope.check("api.example.com:443"))
    }

    @Test
    fun ipv6Cidr() {
        assertEquals(Verdict.IN_SCOPE, scope.check("2001:db8:1::5"))
        assertEquals(Verdict.OUT_OF_SCOPE, scope.check("2001:db9::1"))
    }

    @Test
    fun kindMismatchIsUnknownNotOutOfScope() {
        val ipsOnly = ScopeMatcher.parse("10.0.0.0/8")
        assertEquals(Verdict.UNKNOWN, ipsOnly.check("intranet.local"))
        val domainsOnly = ScopeMatcher.parse("example.com")
        assertEquals(Verdict.UNKNOWN, domainsOnly.check("8.8.8.8"))
    }

    @Test
    fun invalidLinesAreReportedAndInvalidTargetsUnknown() {
        assertEquals(listOf("not a valid entry!!"), scope.invalidLines)
        assertEquals(Verdict.UNKNOWN, scope.check("-f 10.0.0.1"))
        assertTrue(ScopeMatcher.parse("10.0.0.0/33").invalidLines.isNotEmpty())
        assertTrue(ScopeMatcher.parse("10.0.0.50-10").invalidLines.isNotEmpty())
    }

    @Test
    fun slashZeroCoversEverything() {
        assertEquals(Verdict.IN_SCOPE, ScopeMatcher.parse("0.0.0.0/0").check("203.0.113.9"))
    }
}

class FindingsExporterTest {

    private fun finding(title: String, sev: RiskLevel, status: FindingStatus = FindingStatus.OPEN, target: String = "10.0.0.5") =
        Finding(
            id = 1, projectId = "p", fingerprint = title, tool = "Port Scanner", target = target, severity = sev,
            title = title, detail = "detail, with \"quotes\"\nand newline", status = status, firstSeen = 0, lastSeen = 0
        )

    @Test
    fun csvQuotesAndNeutralizesFormulas() {
        assertEquals("plain", FindingsExporter.csvCell("plain"))
        assertEquals("\"a,b\"", FindingsExporter.csvCell("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", FindingsExporter.csvCell("say \"hi\""))
        assertEquals("'=HYPERLINK(\"x\")".let { "\"" + it.replace("\"", "\"\"") + "\"" }, FindingsExporter.csvCell("=HYPERLINK(\"x\")"))
        assertEquals("'+cmd", FindingsExporter.csvCell("+cmd"))
        assertEquals("'@SUM(A1)", FindingsExporter.csvCell("@SUM(A1)"))
    }

    @Test
    fun csvHasHeaderAndOneRowPerFinding() {
        val csv = FindingsExporter.toCsv(listOf(finding("A", RiskLevel.LOW), finding("B", RiskLevel.HIGH)))
        assertTrue(csv.startsWith("id,severity,status,tool,target,title"))
        // Most severe first.
        assertTrue(csv.indexOf(",HIGH,") < csv.indexOf(",LOW,"))
    }

    @Test
    fun jsonEscapesControlCharacters() {
        assertEquals("\"a\\\"b\\\\c\\nd\\u0001\"", FindingsExporter.jsonString("a\"b\\c\nd\u0001"))
    }

    @Test
    fun jsonAndMarkdownContainFindingsAndScope() {
        val report = EngagementReport(
            projectName = "Acme <script>", description = "", scope = "10.0.0.0/24\n# note",
            generatedAt = 0, findings = listOf(finding("Telnet *open*", RiskLevel.CRITICAL)),
            hosts = listOf(ExportHost("10.0.0.5", null, "nas|1", "NAS", RiskLevel.HIGH, listOf(23, 80), null)),
            scanCount = 1, logCount = 2, evidenceCount = 0, appVersion = "1.6.0"
        )
        val json = FindingsExporter.toJson(report)
        assertTrue(json.contains("\"scope\": [\"10.0.0.0/24\"]"))
        assertTrue(json.contains("\"openPorts\": [23, 80]"))
        assertTrue(json.contains("\"severity\": \"CRITICAL\""))

        val md = FindingsExporter.toMarkdown(report)
        assertTrue(md.contains("Acme \\<script\\>"))
        assertTrue(md.contains("[CRITICAL] Telnet \\*open\\*"))
        assertTrue(md.contains("nas\\|1"))
        assertFalse(md.contains("# note"))
    }
}

class ScanDiffTest {
    @Test
    fun reportsAddedRemovedSortedNumerically() {
        val r = ScanDiff.compare(listOf("10.0.0.1", "10.0.0.10", "10.0.0.3"), listOf("10.0.0.1", "10.0.0.2", "10.0.0.20"))
        assertEquals(listOf("10.0.0.2", "10.0.0.20"), r.added)
        assertEquals(listOf("10.0.0.3", "10.0.0.10"), r.removed)
        assertEquals(listOf("10.0.0.1"), r.unchanged)
        assertTrue(r.hasChanges)
        assertFalse(ScanDiff.compare(listOf("a"), listOf("a")).hasChanges)
    }
}
