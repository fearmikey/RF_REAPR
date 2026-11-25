package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.FindingTools
import com.fearmikey.rf_reapr.domain.model.HttpSecurityResult
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Geotagged survey, site-survey heatmap, GATT analysis, Nmap import and the web checks. */
class SurveyAndImportTest {

    // ---- Geotagged survey ---------------------------------------------------------------

    private val fixA = SurveyFix(51.5, -0.12, 30.0, 5f, 1_700_000_000_000)
    private val fixB = SurveyFix(51.6, -0.13, 31.0, 4f, 1_700_000_060_000)

    @Test
    fun surveyMerger_keepsPositionOfStrongestSighting() {
        val first = SurveySighting(SurveyKind.WIFI, "AA:BB:CC:DD:EE:01", "", "[WPA2-PSK-CCMP][ESS]", 2437, 6, -80)
        val (inserts, updates) = SurveyMerger.merge(listOf(first), fixA, emptyMap())
        assertTrue(updates.isEmpty())
        val stored = inserts.single()
        assertEquals(51.5, stored.latitude, 0.0)

        val key = SurveyMerger.key(SurveyKind.WIFI, "aa:bb:cc:dd:ee:01")
        assertEquals(key, SurveyMerger.key(stored.kind, stored.identifier))

        // Stronger later sighting moves the position and fills in the (previously hidden) name.
        val stronger = first.copy(name = "Corp", rssi = -55)
        val moved = SurveyMerger.merge(listOf(stronger), fixB, mapOf(key to stored)).second.single()
        assertEquals(51.6, moved.latitude, 0.0)
        assertEquals(-55, moved.bestRssi)
        assertEquals("Corp", moved.name)
        assertEquals(2, moved.sightings)
        assertEquals(fixA.timestamp, moved.firstSeen)
        assertEquals(fixB.timestamp, moved.lastSeen)

        // A weaker sighting only bumps the counters.
        val weaker = SurveyMerger.merge(listOf(first.copy(rssi = -90)), fixA, mapOf(key to moved)).second.single()
        assertEquals(51.6, weaker.latitude, 0.0)
        assertEquals(-55, weaker.bestRssi)
        assertEquals(3, weaker.sightings)
    }

    private val observations = listOf(
        SurveyObservation(SurveyKind.WIFI, "AA:BB:CC:DD:EE:01", "Coffee, Shop & <Co>", "[WPA2-PSK-CCMP][ESS]", 2437, 6, -61, 51.5, -0.12, 30.0, 5f, 1_700_000_000_000, 1_700_000_060_000, 4),
        SurveyObservation(SurveyKind.BLE, "11:22:33:44:55:66", null, null, null, null, -70, 51.5001, -0.1201, null, null, 1_700_000_010_000, 1_700_000_010_000),
        SurveyObservation(SurveyKind.CELL, "310260_12345_67890", "T-Mobile", "LTE", null, 66486, -95, 51.5002, -0.1202, 29.0, 6f, 1_700_000_020_000, 1_700_000_020_000)
    )

    @Test
    fun wigleCsv_hasHeaderLinesAndElevenColumns() {
        val lines = SurveyExporter.wigleCsv(observations, "1.6.0", "Pixel, 8").trim().lines()
        assertTrue(lines[0].startsWith("WigleWifi-1.4,appRelease=1.6.0,model=Pixel_ 8,"))
        assertEquals("MAC,SSID,AuthMode,FirstSeen,Channel,RSSI,CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters,Type", lines[1])
        assertEquals(5, lines.size)
        lines.drop(2).forEach { assertEquals("row has 11 columns: $it", 11, it.split(',').size) }

        val wifi = lines[2].split(',')
        assertEquals("aa:bb:cc:dd:ee:01", wifi[0])
        assertEquals("Coffee_ Shop & <Co>", wifi[1]) // the comma would otherwise shift every column
        assertEquals("2023-11-14 22:13:20", wifi[3]) // UTC
        assertEquals(listOf("6", "-61", "51.500000", "-0.120000", "30.0", "5.0", "WIFI"), wifi.drop(4))

        assertTrue(lines[3].endsWith(",BLE"))
        assertTrue(lines[4].startsWith("310260_12345_67890,T-Mobile,LTE;,"))
        assertTrue(lines[4].endsWith(",LTE"))
    }

    @Test
    fun kmlAndGpx_areWellFormedAndEscaped() {
        val kml = SurveyExporter.kml("Site <A> & B", observations)
        val gpx = SurveyExporter.gpx("Site <A> & B", observations, "1.6.0")
        listOf(kml, gpx).forEach { xml ->
            // Parses as XML, i.e. names with <, > and & were escaped.
            javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
            assertTrue(xml.contains("Coffee, Shop &amp; &lt;Co&gt;"))
        }
        assertTrue(kml.contains("<coordinates>-0.120000,51.500000,30.0</coordinates>")) // KML is lon,lat,alt
        assertTrue(kml.contains("<name>Wi-Fi (1)</name>"))
        assertTrue(gpx.contains("""<wpt lat="51.500000" lon="-0.120000">"""))
        assertTrue(gpx.contains("<time>2023-11-14T22:13:20Z</time>"))
    }

    @Test
    fun xmlEscape_dropsIllegalControlCharacters() {
        assertEquals("ab&amp;c", SurveyExporter.xml("a\u0001b&c"))
    }

    // ---- Site survey heatmap ------------------------------------------------------------

    private fun sample(point: Long, x: Float, y: Float, bssid: String, ssid: String, rssi: Int, mhz: Int = 2437) =
        SiteSample(point, x, y, bssid, ssid, rssi, mhz)

    private val siteSamples = listOf(
        sample(1, 0.1f, 0.1f, "AA:01", "Corp", -45),
        sample(1, 0.1f, 0.1f, "AA:02", "Corp", -70, 5180),
        sample(1, 0.1f, 0.1f, "BB:01", "Guest", -60),
        sample(2, 0.9f, 0.9f, "AA:02", "Corp", -50, 5180),
        sample(3, 0.5f, 0.5f, "BB:01", "Guest", -65) // Corp not heard here
    )

    @Test
    fun heatmap_networksAndPerPointReadings() {
        val networks = SignalHeatmap.networks(siteSamples)
        assertEquals(listOf("Corp", "Guest"), networks.map { it.ssid })
        assertEquals(2, networks[0].points)
        assertEquals(setOf("2.4 GHz", "5 GHz"), networks[0].bands)

        // Whole SSID: best AP per point; a point where it wasn't heard counts as no signal.
        val corp = SignalHeatmap.pointsFor(siteSamples, "Corp").sortedBy { it.x }
        assertEquals(listOf(-45, SignalHeatmap.NOT_HEARD_RSSI, -50), corp.map { it.rssi })

        val oneAp = SignalHeatmap.pointsFor(siteSamples, "Corp", "aa:02").sortedBy { it.x }
        assertEquals(listOf(-70, SignalHeatmap.NOT_HEARD_RSSI, -50), oneAp.map { it.rssi })
        assertEquals(listOf("AA:01", "AA:02"), SignalHeatmap.accessPoints(siteSamples, "Corp").map { it.bssid })
    }

    @Test
    fun heatmap_interpolatesBetweenPointsAndDoesNotExtrapolate() {
        val points = listOf(HeatPoint(0.2f, 0.5f, -40), HeatPoint(0.4f, 0.5f, -80))
        assertEquals(-40f, SignalHeatmap.valueAt(points, 0.2f, 0.5f)!!, 0.001f) // exact at a sample
        assertEquals(-60f, SignalHeatmap.valueAt(points, 0.3f, 0.5f)!!, 0.001f) // midpoint
        val nearStrong = SignalHeatmap.valueAt(points, 0.22f, 0.5f)!!
        assertTrue(nearStrong > -50f)
        assertNull(SignalHeatmap.valueAt(points, 0.95f, 0.5f)) // too far from any measurement
        assertNull(SignalHeatmap.valueAt(emptyList(), 0.5f, 0.5f))

        val grid = SignalHeatmap.grid(points, 10, 10)
        assertEquals(100, grid.size)
        assertTrue(grid.any { it.isNaN() })
        assertTrue(grid.any { !it.isNaN() })
        val coverage = SignalHeatmap.coverage(grid)!!
        assertTrue(coverage > 0f && coverage < 1f)
        assertNull(SignalHeatmap.coverage(SignalHeatmap.grid(emptyList(), 4, 4)))
    }

    @Test
    fun heatmap_qualityBandsAndColours() {
        assertEquals(SignalQuality.EXCELLENT, SignalQuality.of(-50f))
        assertEquals(SignalQuality.GOOD, SignalQuality.of(-67f))
        assertEquals(SignalQuality.FAIR, SignalQuality.of(-70f))
        assertEquals(SignalQuality.WEAK, SignalQuality.of(-80f))
        assertEquals(SignalQuality.DEAD, SignalQuality.of(-95f))

        fun red(argb: Int) = (argb shr 16) and 0xFF
        fun green(argb: Int) = (argb shr 8) and 0xFF
        val weak = SignalHeatmap.colorArgb(-95f)
        val strong = SignalHeatmap.colorArgb(-45f)
        assertTrue(red(weak) > green(weak))
        assertTrue(green(strong) > red(strong))
        assertEquals(150, weak ushr 24)
    }

    // ---- GATT ---------------------------------------------------------------------------

    private fun uuid16(short: String) = "0000$short-0000-1000-8000-00805f9b34fb"

    @Test
    fun gattCatalog_namesAndProperties() {
        assertEquals("180a", GattCatalog.shortUuid(uuid16("180A").uppercase()))
        assertNull(GattCatalog.shortUuid(GattCatalog.NORDIC_UART))
        assertEquals("Device Information", GattCatalog.serviceName(uuid16("180a")))
        assertEquals("Nordic UART (serial)", GattCatalog.serviceName(GattCatalog.NORDIC_UART.uppercase()))
        assertEquals("Vendor-specific service", GattCatalog.serviceName("12345678-1234-1234-1234-123456789abc"))
        assertEquals("Serial Number", GattCatalog.characteristicName(uuid16("2a25")))
        assertEquals(listOf("Read", "Write", "Notify"), GattCatalog.describeProperties(0x02 or 0x08 or 0x10))
    }

    @Test
    fun gattAnalyzer_decodesValues() {
        assertEquals("ACME-1", GattAnalyzer.decodeValue(uuid16("2a24"), "ACME-1".toByteArray()))
        assertEquals("87%", GattAnalyzer.decodeValue(uuid16("2a19"), byteArrayOf(87)))
        assertEquals("01 FF 00", GattAnalyzer.decodeValue("12345678-1234-1234-1234-123456789abc", byteArrayOf(1, -1, 0)))
        assertEquals("(empty)", GattAnalyzer.decodeValue(uuid16("2a24"), byteArrayOf()))
        assertNull(GattAnalyzer.decodeValue(uuid16("2a24"), null))
    }

    @Test
    fun gattAnalyzer_flagsWhatAnUnpairedConnectionIsOffered() {
        val services = listOf(
            GattServiceInfo(uuid16("180a"), listOf(GattCharacteristicInfo(uuid16("2a25"), 0x02, "SN123".toByteArray()))),
            GattServiceInfo(GattCatalog.NORDIC_UART, listOf(GattCharacteristicInfo("6e400002-b5a3-f393-e0a9-e50e24dcca9e", 0x0C))),
            GattServiceInfo(uuid16("fe59"), listOf(GattCharacteristicInfo("8ec90003-f315-4f60-9fb8-838830daea50", 0x28)))
        )
        val titles = GattAnalyzer.findings(services, bonded = false).map { it.title }
        assertEquals(
            listOf(
                "Firmware update service reachable without pairing",
                "Serial (UART) service reachable without pairing",
                "Writable vendor characteristics offered without pairing",
                "Device serial number readable without pairing"
            ),
            titles
        )
        // A paired phone says nothing about what strangers can reach.
        assertTrue(GattAnalyzer.findings(services, bonded = true).isEmpty())
        // Standard services with only readable values are unremarkable.
        val benign = listOf(GattServiceInfo(uuid16("180f"), listOf(GattCharacteristicInfo(uuid16("2a19"), 0x12, byteArrayOf(50)))))
        assertTrue(GattAnalyzer.findings(benign, bonded = false).isEmpty())

        val drafts = GattAnalyzer.toDrafts("Lock", "AA:BB:CC:DD:EE:FF", GattAnalyzer.findings(services, false))
        assertTrue(drafts.all { it.tool == FindingTools.BLE && it.target == "BLE AA:BB:CC:DD:EE:FF (Lock)" })
    }

    // ---- Nmap import --------------------------------------------------------------------

    private val nmapXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE nmaprun>
        <?xml-stylesheet href="file:///usr/share/nmap/nmap.xsl" type="text/xsl"?>
        <nmaprun scanner="nmap" args="nmap -sV -oX out.xml 10.0.0.0/24" start="1700000000" version="7.94">
          <host>
            <status state="up" reason="arp-response"/>
            <address addr="10.0.0.7" addrtype="ipv4"/>
            <address addr="00:11:22:33:44:55" addrtype="mac" vendor="Synology"/>
            <hostnames><hostname name="nas.lan" type="PTR"/></hostnames>
            <ports>
              <port protocol="tcp" portid="23"><state state="open"/><service name="telnet" product="BusyBox telnetd" version="1.2"/></port>
              <port protocol="tcp" portid="443"><state state="open"/><service name="https"/>
                <script id="ssl-heartbleed" output="VULNERABLE: The Heartbleed Bug is a serious vulnerability"/>
              </port>
              <port protocol="tcp" portid="8080"><state state="closed"/><service name="http-proxy"/></port>
              <port protocol="udp" portid="161"><state state="open"/><service name="snmp"/></port>
            </ports>
            <os><osmatch name="Linux 3.2" accuracy="90"/><osmatch name="Linux 5.4" accuracy="96"/></os>
          </host>
          <host>
            <status state="down"/>
            <address addr="10.0.0.8" addrtype="ipv4"/>
          </host>
        </nmaprun>
    """.trimIndent()

    @Test
    fun nmapParser_readsHostsPortsAndScripts() {
        val scan = NmapXmlParser.parse(nmapXml.byteInputStream())
        assertEquals("nmap 7.94", scan.scanner)
        assertEquals(1_700_000_000_000, scan.startedAt)
        assertEquals(2, scan.hosts.size)

        val host = scan.hosts[0]
        assertEquals("10.0.0.7", host.address)
        assertEquals("00:11:22:33:44:55", host.mac)
        assertEquals("Synology", host.vendor)
        assertEquals(listOf("nas.lan"), host.hostnames)
        assertEquals("Linux 5.4", host.osGuess)
        assertEquals(listOf(23, 443, 8080, 161), host.ports.map { it.port })
        assertEquals("BusyBox telnetd 1.2", host.ports[0].banner)
        assertFalse(host.ports[2].isOpen)
        assertEquals("ssl-heartbleed", host.ports[1].scripts.single().id)
        assertFalse(scan.hosts[1].isUp)
    }

    @Test
    fun nmapImporter_mapsUpHostsOpenTcpPortsAndFindings() {
        val scan = NmapXmlParser.parse(nmapXml.byteInputStream())
        val node = NmapImporter.toNodes(scan).single() // the down host is skipped
        assertEquals("10.0.0.7", node.ipAddress)
        assertEquals("nas.lan", node.hostname)
        assertEquals(listOf(23, 443), node.openPorts.map { it.port }) // closed and UDP ports are left out
        assertEquals("telnet", node.openPorts[0].serviceName)

        val findings = NmapImporter.toFindings(scan)
        assertTrue(findings.all { it.tool == FindingTools.NMAP && it.target == "10.0.0.7" })
        assertTrue(findings.any { it.title.contains("23/tcp") })
        val script = findings.single { it.title.startsWith("Nmap script ssl-heartbleed") }
        assertEquals(RiskLevel.HIGH, script.severity)

        val summary = NmapImporter.summarize(scan)
        assertEquals(1, summary.hosts)
        assertEquals(2, summary.openPorts)
        assertEquals(findings.size, summary.findings)
    }

    @Test
    fun nmapParser_rejectsOtherXmlAndGarbage() {
        listOf("<html><body/></html>", "this is not xml", "").forEach { input ->
            try {
                NmapXmlParser.parse(input.byteInputStream())
                fail("Expected NmapParseException for: $input")
            } catch (e: NmapParseException) {
                assertNotNull(e.message)
            }
        }
    }

    @Test
    fun nmapParser_doesNotResolveExternalEntities() {
        val secret = java.io.File.createTempFile("nmap-xxe", ".txt").apply { writeText("TOP-SECRET-CONTENT"); deleteOnExit() }
        val xml = """
            <?xml version="1.0"?>
            <!DOCTYPE nmaprun [<!ENTITY xxe SYSTEM "${secret.toURI()}">]>
            <nmaprun scanner="nmap" args="&xxe;">
              <host><status state="up"/><address addr="10.0.0.1" addrtype="ipv4"/>
                <hostnames><hostname name="&xxe;"/></hostnames></host>
            </nmaprun>
        """.trimIndent()
        val leaked = try {
            val scan = NmapXmlParser.parse(xml.byteInputStream())
            scan.args.orEmpty() + scan.hosts.flatMap { it.hostnames }.joinToString()
        } catch (e: NmapParseException) {
            "" // refusing the document outright is also fine
        }
        assertFalse(leaked.contains("TOP-SECRET-CONTENT"))
    }

    // ---- Web checks ---------------------------------------------------------------------

    @Test
    fun securityHeaders_hardenedSiteGetsAnA() {
        val audit = SecurityHeaderAnalyzer.analyze(
            "https://example.com/",
            mapOf(
                "Strict-Transport-Security" to "max-age=63072000; includeSubDomains; preload",
                "Content-Security-Policy" to "default-src 'self'; script-src 'self' 'nonce-abc'; frame-ancestors 'none'",
                "X-Content-Type-Options" to "nosniff",
                "Referrer-Policy" to "strict-origin-when-cross-origin",
                "Permissions-Policy" to "camera=()",
                "Server" to "nginx"
            ),
            listOf("sid=abc; Path=/; Secure; HttpOnly; SameSite=Lax")
        )
        assertEquals(emptyList<String>(), audit.issues.map { "${it.header}: ${it.issue}" })
        assertEquals("A", audit.grade)
        assertEquals(100, audit.score)
        assertTrue(audit.passed.containsAll(listOf("HSTS enabled", "Framing restricted", "Cookies are hardened")))
    }

    @Test
    fun securityHeaders_bareSiteFailsAndPlainHttpIsHigh() {
        val bare = SecurityHeaderAnalyzer.analyze("https://example.com", mapOf("Server" to "Apache/2.4.41 (Ubuntu)", "X-Powered-By" to "PHP/7.4"))
        assertEquals("F", bare.grade)
        val headers = bare.issues.map { it.header }
        assertTrue(headers.containsAll(listOf("Strict-Transport-Security", "Content-Security-Policy", "X-Frame-Options", "X-Content-Type-Options", "Server", "X-Powered-By")))

        val http = SecurityHeaderAnalyzer.analyze("http://example.com", emptyMap())
        assertEquals(RiskLevel.HIGH, http.issues.first { it.header == "Transport" }.severity)
        assertTrue(http.issues.none { it.header == "Strict-Transport-Security" }) // browsers ignore HSTS over HTTP
    }

    @Test
    fun securityHeaders_weakValuesAndCookies() {
        val audit = SecurityHeaderAnalyzer.analyze(
            "https://example.com",
            mapOf(
                "strict-transport-security" to "max-age=300",
                "content-security-policy" to "default-src *; script-src 'self' 'unsafe-inline'",
                "x-frame-options" to "DENY",
                "x-content-type-options" to "nosniff",
                "referrer-policy" to "unsafe-url",
                "permissions-policy" to "geolocation=()",
                "access-control-allow-origin" to "*",
                "access-control-allow-credentials" to "true"
            ),
            listOf("sid=abc; Path=/", "theme=dark; Secure; HttpOnly; SameSite=Strict")
        )
        val issues = audit.issues.associate { "${it.header}: ${it.issue}" to it.severity }
        assertEquals(RiskLevel.LOW, issues["Strict-Transport-Security: HSTS max-age is short"])
        assertEquals(RiskLevel.LOW, issues["Content-Security-Policy: Policy allows inline scripts"])
        assertEquals(RiskLevel.LOW, issues["Referrer-Policy: Policy leaks full URLs to other sites"])
        assertEquals(RiskLevel.MEDIUM, issues["Access-Control-Allow-Origin: Wildcard origin with credentials allowed"])
        assertEquals(RiskLevel.MEDIUM, issues["Set-Cookie: Cookies set without the Secure flag"])
        assertTrue(audit.issues.first { it.issue.contains("Secure flag") }.recommendation.contains("sid"))
        assertFalse(audit.issues.first { it.issue.contains("Secure flag") }.recommendation.contains("theme"))

        val drafts = SecurityHeaderAnalyzer.toDrafts(
            HttpSecurityResult("https://user@example.com:8443/path?q=1", 200, emptyMap(), audit.issues)
        )
        assertTrue(drafts.all { it.tool == FindingTools.WEB && it.target == "example.com:8443" })
        assertEquals(audit.issues.size, drafts.size)
    }

    @Test
    fun takeover_recognisesHostingPlatformsAndOnlyReportsDanglingOnes() {
        assertEquals("GitHub Pages", SubdomainTakeoverAnalyzer.serviceFor("acme.github.io."))
        assertEquals("Amazon S3", SubdomainTakeoverAnalyzer.serviceFor("assets.s3.eu-west-1.amazonaws.com"))
        assertEquals("Amazon S3", SubdomainTakeoverAnalyzer.serviceFor("site.s3-website-us-east-1.amazonaws.com"))
        assertNull(SubdomainTakeoverAnalyzer.serviceFor("ec2-1-2-3-4.compute-1.amazonaws.com")) // not claimable by name
        assertNull(SubdomainTakeoverAnalyzer.serviceFor("notgithub.io.example.com"))
        assertNull(SubdomainTakeoverAnalyzer.serviceFor("www.example.com"))

        assertNull(SubdomainTakeoverAnalyzer.assess(null, resolves = false))
        assertNull(SubdomainTakeoverAnalyzer.assess("www.example.com", resolves = false))
        val hosted = SubdomainTakeoverAnalyzer.assess("acme.herokuapp.com", resolves = true)!!
        val dangling = SubdomainTakeoverAnalyzer.assess("acme-old.herokuapp.com.", resolves = false)!!
        assertEquals(TakeoverRisk.HOSTED, hosted.risk)
        assertEquals(TakeoverRisk.DANGLING, dangling.risk)
        assertEquals("acme-old.herokuapp.com", dangling.cname)

        val drafts = SubdomainTakeoverAnalyzer.toDrafts(listOf("app.example.com" to hosted, "old.example.com" to dangling))
        val draft = drafts.single()
        assertEquals("old.example.com", draft.target)
        assertEquals(RiskLevel.HIGH, draft.severity)
        assertEquals(FindingTools.SUBDOMAIN, draft.tool)
    }
}
