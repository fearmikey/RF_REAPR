package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.AuditCheck
import com.fearmikey.rf_reapr.domain.model.AuditRun
import com.fearmikey.rf_reapr.domain.model.AuditRunFinding
import com.fearmikey.rf_reapr.domain.model.EventLog
import com.fearmikey.rf_reapr.domain.model.Evidence
import com.fearmikey.rf_reapr.domain.model.Finding
import com.fearmikey.rf_reapr.domain.model.FindingDraft
import com.fearmikey.rf_reapr.domain.model.FindingStatus
import com.fearmikey.rf_reapr.domain.model.FindingTools
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Date

/** Evidence hashing, compliance mapping, audit-run comparison, timeline, known devices and the new exports. */
class EngagementExtensionsTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun finding(
        id: Long = 1,
        tool: String = FindingTools.TLS,
        target: String = "10.0.0.5:443",
        title: String = "TLS 1.0 enabled",
        severity: RiskLevel = RiskLevel.HIGH,
        status: FindingStatus = FindingStatus.OPEN,
        firstSeen: Long = 1_000_000
    ) = Finding(
        id = id, projectId = "p1", fingerprint = FindingMerger.fingerprint(tool, target, title), tool = tool, target = target,
        severity = severity, title = title, detail = "detail <b>&", remediation = "fix it", status = status,
        firstSeen = firstSeen, lastSeen = firstSeen + 60_000
    )

    // ---- Evidence hashing ---------------------------------------------------------------

    @Test
    fun sha256_matchesKnownVector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            EvidenceHasher.sha256("abc".byteInputStream())
        )
    }

    @Test
    fun verify_detectsModificationMissingAndUnhashed() {
        val file = temp.newFile("photo.jpg").apply { writeText("original") }
        val hash = EvidenceHasher.sha256(file)
        assertEquals(EvidenceIntegrity.VERIFIED, EvidenceHasher.verify(file, hash))
        assertEquals(EvidenceIntegrity.VERIFIED, EvidenceHasher.verify(file, hash!!.uppercase()))
        assertEquals(EvidenceIntegrity.UNHASHED, EvidenceHasher.verify(file, null))

        file.writeText("tampered")
        assertEquals(EvidenceIntegrity.MODIFIED, EvidenceHasher.verify(file, hash))

        file.delete()
        assertEquals(EvidenceIntegrity.MISSING, EvidenceHasher.verify(file, hash))
        assertNull(EvidenceHasher.sha256(file))
    }

    @Test
    fun shortHash_keepsEnds() {
        assertEquals("—", EvidenceHasher.short(null))
        assertEquals("ba7816bf…f20015ad", EvidenceHasher.short("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"))
    }

    // ---- Compliance mapping -------------------------------------------------------------

    @Test
    fun complianceMapper_mapsToolsToThemes() {
        assertEquals(setOf(ControlTheme.TRANSMISSION), ComplianceMapper.themes(FindingTools.TLS, "TLS 1.0 enabled"))
        assertEquals(setOf(ControlTheme.ACCESS), ComplianceMapper.themes(FindingTools.SNMP, "Default community string accepted"))
        assertEquals(setOf(ControlTheme.VULNERABILITY), ComplianceMapper.themes(FindingTools.PORT_SCANNER, "CVE-2023-1234 in OpenSSH (22/tcp)"))
        assertEquals(
            setOf(ControlTheme.ACCESS, ControlTheme.TRANSMISSION),
            ComplianceMapper.themes(FindingTools.PORT_SCANNER, "Telnet exposed (23/tcp)")
        )
        // HTTPS is not a cleartext service even though it starts with "http".
        assertEquals(setOf(ControlTheme.ACCESS), ComplianceMapper.themes(FindingTools.PORT_SCANNER, "HTTPS exposed (443/tcp)"))
        assertEquals(setOf(ControlTheme.MONITORING), ComplianceMapper.themes(FindingTools.WIFI, "Possible evil twin of \"Corp\""))
        assertTrue(ComplianceMapper.themes(FindingTools.MANUAL, "Door propped open").isEmpty())
    }

    @Test
    fun complianceMapper_onlyOpenAndAcceptedCountAgainstControls() {
        val byControl = ComplianceMapper.findingsByControl(
            listOf(
                finding(id = 1),
                finding(id = 2, title = "Weak cipher suites accepted", status = FindingStatus.FIXED),
                finding(id = 3, title = "Certificate: expired", status = FindingStatus.ACCEPTED),
                finding(id = 4, title = "SSLv3 enabled", status = FindingStatus.FALSE_POSITIVE)
            )
        )
        assertEquals(listOf(1L, 3L), byControl.getValue("SOC2-CC6.7").map { it.id }.sorted())
        assertEquals(setOf("NIST-3.13.8", "ISO-A.10.1.1", "SOC2-CC6.7"), byControl.keys)
    }

    @Test
    fun complianceMapper_onlyReferencesCatalogControls() {
        val catalog = listOf("NIST", "ISO27001", "SOC2").flatMap { ComplianceCatalog.controls(it) }.map { it.id }.toSet()
        ControlTheme.entries.forEach { theme ->
            theme.controlIds.forEach { id -> assertTrue("$id is not in the catalog", id in catalog) }
        }
    }

    // ---- Audit run history --------------------------------------------------------------

    private fun runFinding(tool: String, target: String, title: String, severity: RiskLevel = RiskLevel.MEDIUM) =
        AuditRunFinding(FindingMerger.fingerprint(tool, target, title), tool, target, severity, title)

    private fun auditRun(id: Long, finishedAt: Long, grade: String, score: Int, checks: Set<AuditCheck>, findings: List<AuditRunFinding>) =
        AuditRun(
            id = id, projectId = "p1", startedAt = finishedAt - 1000, finishedAt = finishedAt, networkLabel = "lab", grade = grade,
            score = score, completed = true, checks = checks, hostCount = 3, findings = findings, reportMarkdown = "# report"
        )

    @Test
    fun auditRunCodec_roundTripsAndToleratesGarbage() {
        val findings = listOf(
            runFinding(FindingTools.TLS, "10.0.0.5:443", "TLS 1.0 \"enabled\"\nline", RiskLevel.HIGH),
            runFinding(FindingTools.WIFI, "Wi-Fi \"Café\"", "WPS enabled on connected Wi-Fi")
        )
        assertEquals(findings, AuditRunCodec.decode(AuditRunCodec.encode(findings)))
        assertTrue(AuditRunCodec.decode("not json").isEmpty())
        assertTrue(AuditRunCodec.decode("{}").isEmpty())
        assertEquals(setOf(AuditCheck.TLS, AuditCheck.DNS), AuditRunCodec.decodeChecks("TLS,DNS,REMOVED_CHECK"))
    }

    @Test
    fun auditRunCodec_snapshotDeduplicatesKeepingWorstSeverity() {
        val snapshot = AuditRunCodec.snapshot(
            listOf(
                FindingDraft(FindingTools.TLS, "h:443", RiskLevel.LOW, "Weak cipher suites accepted", "a"),
                FindingDraft(FindingTools.TLS, "h:443", RiskLevel.HIGH, "weak cipher suites accepted ", "b"),
                FindingDraft(FindingTools.TLS, "", RiskLevel.HIGH, "no target", "c")
            )
        )
        assertEquals(1, snapshot.size)
        assertEquals(RiskLevel.HIGH, snapshot.single().severity)
    }

    @Test
    fun auditRunDiff_separatesResolvedFromNotRetested() {
        val tls = runFinding(FindingTools.TLS, "10.0.0.5:443", "TLS 1.0 enabled", RiskLevel.HIGH)
        val wps = runFinding(FindingTools.WIFI, "Wi-Fi \"Corp\"", "WPS enabled on connected Wi-Fi")
        val snmp = runFinding(FindingTools.SNMP, "10.0.0.9", "Default community string accepted", RiskLevel.HIGH)
        val telnet = runFinding(FindingTools.PORT_SCANNER, "10.0.0.7", "Telnet exposed (23/tcp)", RiskLevel.HIGH)

        val previous = auditRun(1, 10_000, "D", 45, setOf(AuditCheck.WIFI, AuditCheck.PORTS, AuditCheck.TLS, AuditCheck.SNMP), listOf(tls, wps, snmp))
        // The later run fixed TLS, still has WPS, found Telnet, and did not run the SNMP check.
        val latest = auditRun(2, 20_000, "C", 66, setOf(AuditCheck.WIFI, AuditCheck.PORTS, AuditCheck.TLS), listOf(wps, telnet))

        val diff = AuditRunDiff.compare(previous, latest)
        assertEquals(listOf(tls), diff.resolved)
        assertEquals(listOf(telnet), diff.newFindings)
        assertEquals(listOf(wps), diff.persisting)
        assertEquals(listOf(snmp), diff.notRetested)
        assertEquals(21, diff.scoreDelta)
        assertTrue(diff.gradeChanged)
        assertEquals("1 resolved, 1 new, 1 still present, 1 not re-tested, grade D → C", diff.summary())
        assertTrue(AuditRunDiff.markdown(diff).contains("### Resolved"))
    }

    @Test
    fun auditRunDiff_identicalRunsHaveNoChanges() {
        val wps = runFinding(FindingTools.WIFI, "Wi-Fi \"Corp\"", "WPS enabled on connected Wi-Fi")
        val a = auditRun(1, 10_000, "B", 80, setOf(AuditCheck.WIFI), listOf(wps))
        val diff = AuditRunDiff.compare(a, a.copy(id = 2, finishedAt = 20_000))
        assertFalse(diff.hasChanges)
        assertEquals("1 still present, grade B unchanged", diff.summary())
    }

    // ---- Timeline -----------------------------------------------------------------------

    @Test
    fun timeline_ordersNewestFirstAndDropsDuplicateAuditLog() {
        val run = auditRun(1, 50_000, "B", 80, setOf(AuditCheck.WIFI), emptyList())
        val events = TimelineBuilder.build(
            logs = listOf(
                EventLog(1, "PORT", 20_000, "Scanned 10.0.0.5", "{}"),
                EventLog(2, "AUDIT", 50_500, "Audit Mode: grade B", "{}"), // same audit as the stored run
                EventLog(3, "AUDIT", 5_000, "Audit Mode: grade C", "{}") // older audit with no stored run
            ),
            findings = listOf(finding(firstSeen = 30_000)),
            evidence = listOf(
                Evidence(id = "e1", filePath = "/x.jpg", timestamp = Date(40_000), latitude = 1.5, longitude = 2.5, notes = "Server room door", sha256 = "ab".repeat(32))
            ),
            scans = emptyList(),
            audits = listOf(run)
        )
        assertEquals(listOf(50_000L, 40_000L, 30_000L, 20_000L, 5_000L), events.map { it.timestamp })
        assertEquals(TimelineKind.AUDIT, events.first().kind)
        assertEquals(RiskLevel.HIGH, events.first { it.kind == TimelineKind.FINDING }.severity)
        assertTrue(events.first { it.kind == TimelineKind.EVIDENCE }.detail!!.contains("SHA-256"))

        val markdown = TimelineBuilder.toMarkdown("Acme <audit>", events)
        assertTrue(markdown.startsWith("# Acme &lt;audit&gt; — Activity timeline"))
        // Chronological in the export: the oldest entry comes before the newest.
        assertTrue(markdown.indexOf("grade C") < markdown.indexOf("Audit finished"))
    }

    // ---- Known devices ------------------------------------------------------------------

    @Test
    fun knownDevices_normalizeAndStanding() {
        assertEquals("AA:BB:CC:DD:EE:FF", KnownDeviceMatcher.normalize("aa-bb-cc-dd-ee-ff"))
        assertEquals("AA:BB:CC:DD:EE:FF", KnownDeviceMatcher.normalize("aabb.ccdd.eeff"))
        assertNull(KnownDeviceMatcher.normalize("00:00:00:00:00:00"))
        assertNull(KnownDeviceMatcher.normalize("not a mac"))
        assertNull(KnownDeviceMatcher.normalize(null))

        val known = setOf("AA:BB:CC:DD:EE:FF")
        assertEquals(DeviceStanding.KNOWN, KnownDeviceMatcher.standing("aa:bb:cc:dd:ee:ff", known))
        assertEquals(DeviceStanding.UNKNOWN, KnownDeviceMatcher.standing("11:22:33:44:55:66", known))
        assertEquals(DeviceStanding.UNIDENTIFIED, KnownDeviceMatcher.standing(null, known))
    }

    @Test
    fun knownDevices_emptyListMeansFeatureOff() {
        val devices = listOf(ObservedDevice("10.0.0.2", "11:22:33:44:55:66"))
        assertTrue(KnownDeviceMatcher.findings(devices, emptySet()).isEmpty())
    }

    @Test
    fun knownDevices_findingsTargetMacAndRateRandomizedLower() {
        val known = setOf("AA:BB:CC:DD:EE:FF")
        val drafts = KnownDeviceMatcher.findings(
            listOf(
                ObservedDevice("10.0.0.2", "aa:bb:cc:dd:ee:ff", "printer"),
                ObservedDevice("10.0.0.3", "00:11:22:33:44:55", "nas", "Synology"),
                ObservedDevice("10.0.0.4", "DA:11:22:33:44:55"), // locally administered = randomized
                ObservedDevice("10.0.0.5", null)
            ),
            known
        )
        assertEquals(listOf("00:11:22:33:44:55", "DA:11:22:33:44:55"), drafts.map { it.target })
        assertEquals(listOf(RiskLevel.MEDIUM, RiskLevel.LOW), drafts.map { it.severity })
        assertTrue(drafts.all { it.title == KnownDeviceMatcher.FINDING_TITLE && it.tool == FindingTools.NETWORK_MONITOR })
        assertTrue(drafts[0].detail.contains("Vendor Synology"))
    }

    // ---- Exports ------------------------------------------------------------------------

    private fun report(findings: List<Finding>) = EngagementReport(
        projectName = "Acme <script>", description = "", scope = "10.0.0.0/24", generatedAt = 2_000_000, findings = findings,
        hosts = emptyList(), scanCount = 1, logCount = 2, evidenceCount = 1, appVersion = "9.9",
        evidenceByFinding = mapOf(
            1L to listOf(ExportEvidence("evidence_1.jpg", 1_500_000, "ab".repeat(32), EvidenceIntegrity.VERIFIED))
        )
    )

    @Test
    fun html_escapesUntrustedTextAndListsEvidence() {
        val html = FindingsExporter.toHtml(report(listOf(finding(title = "<img src=x onerror=alert(1)>"))))
        assertFalse(html.contains("<img src=x"))
        assertTrue(html.contains("&lt;img src=x onerror=alert(1)&gt;"))
        assertTrue(html.contains("Acme &lt;script&gt;"))
        assertFalse(html.contains("<script"))
        assertTrue(html.contains("evidence_1.jpg"))
        assertTrue(html.contains("Verified"))
        assertTrue(html.contains("SOC2-CC6.7"))
    }

    @Test
    fun markdownAndJson_includeControlsAndEvidence() {
        val r = report(listOf(finding()))
        assertTrue(FindingsExporter.toMarkdown(r).contains("**Related controls:** NIST-3.13.8"))
        assertTrue(FindingsExporter.toMarkdown(r).contains("evidence\\_1.jpg").not()) // file names are in code spans, not escaped
        assertTrue(FindingsExporter.toMarkdown(r).contains("`evidence_1.jpg`"))
        val json = FindingsExporter.toJson(r)
        assertTrue(json.contains("\"controls\": [\"NIST-3.13.8\", \"ISO-A.10.1.1\", \"SOC2-CC6.7\"]"))
        assertTrue(json.contains("\"integrity\": \"VERIFIED\""))
        // Still parseable JSON after splicing the extra fields in.
        assertNotNull(com.google.gson.JsonParser.parseString(json).asJsonObject.getAsJsonArray("findings"))
    }

    @Test
    fun defectDojo_mapsTriageAndEndpoints() {
        val json = FindingsExporter.toDefectDojoJson(
            report(
                listOf(
                    finding(id = 1),
                    finding(id = 2, tool = FindingTools.PORT_SCANNER, target = "10.0.0.7", title = "CVE-2023-1234 in OpenSSH (22/tcp)", status = FindingStatus.FIXED),
                    finding(id = 3, tool = FindingTools.WIFI, target = "Wi-Fi \"Corp\"", title = "WPS enabled on connected Wi-Fi", status = FindingStatus.ACCEPTED, severity = RiskLevel.MEDIUM)
                )
            )
        )
        val findings = com.google.gson.JsonParser.parseString(json).asJsonObject.getAsJsonArray("findings").map { it.asJsonObject }
        assertEquals(3, findings.size)

        val tls = findings.first { it.get("title").asString == "TLS 1.0 enabled" }
        assertEquals("High", tls.get("severity").asString)
        assertTrue(tls.get("active").asBoolean)
        val endpoint = tls.getAsJsonArray("endpoints").single().asJsonObject
        assertEquals("10.0.0.5", endpoint.get("host").asString)
        assertEquals(443, endpoint.get("port").asInt)

        val cve = findings.first { it.has("cve") }
        assertEquals("CVE-2023-1234", cve.get("cve").asString)
        assertTrue(cve.get("is_mitigated").asBoolean)
        assertFalse(cve.get("active").asBoolean)

        val wifi = findings.first { it.get("severity").asString == "Medium" }
        assertTrue(wifi.get("risk_accepted").asBoolean)
        assertFalse(wifi.has("endpoints")) // not a host
    }
}
