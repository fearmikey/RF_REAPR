package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.FindingTools
import com.fearmikey.rf_reapr.domain.model.Paste
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.domain.model.UsernameCheckResult
import com.fearmikey.rf_reapr.domain.model.UsernameSitesDatabase
import com.fearmikey.rf_reapr.domain.model.UsernameStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

/** Threat intel, Shodan, breach, cloud-bucket and username analyzers. */
class OsintAnalyzersTest {

    // ---- PublicIpGuard ----------------------------------------------------------------

    @Test
    fun `public ip guard accepts routable addresses`() {
        assertEquals(PublicIpGuard.Verdict.Public("8.8.8.8", 4), PublicIpGuard.check(" 8.8.8.8 "))
        val v6 = PublicIpGuard.check("[2606:4700:4700::1111]")
        assertTrue(v6 is PublicIpGuard.Verdict.Public && v6.version == 6)
    }

    @Test
    fun `public ip guard rejects internal addresses and hostnames`() {
        listOf("192.168.1.10", "10.0.0.1", "172.16.5.4", "127.0.0.1", "169.254.1.1", "100.64.0.1", "224.0.0.1").forEach {
            assertTrue(it, PublicIpGuard.check(it) is PublicIpGuard.Verdict.NotRoutable)
        }
        listOf("::1", "fe80::1", "fd00::1", "2001:db8::1", "::ffff:192.168.1.1").forEach {
            assertTrue(it, PublicIpGuard.check(it) is PublicIpGuard.Verdict.NotRoutable)
        }
        listOf("example.com", "", "1.2.3", "dead:beef:zz::1").forEach {
            assertEquals(it, PublicIpGuard.Verdict.NotAnIp, PublicIpGuard.check(it))
        }
        val refusal = PublicIpGuard.refusal(PublicIpGuard.check("10.0.0.1") as PublicIpGuard.Verdict.NotRoutable, "Shodan")
        assertTrue(refusal.contains("10.0.0.1") && refusal.contains("nothing was sent"))
    }

    // ---- AbuseIPDB --------------------------------------------------------------------

    private val abuseJson = """
        {"data":{"ipAddress":"118.25.6.39","isPublic":true,"ipVersion":4,"isWhitelisted":false,
        "abuseConfidenceScore":100,"countryCode":"CN","countryName":"China",
        "usageType":"Data Center/Web Hosting/Transit","isp":"Tencent Cloud Computing","domain":"tencent.com",
        "hostnames":[],"isTor":false,"totalReports":3,"numDistinctUsers":2,
        "lastReportedAt":"2024-05-01T10:00:00+00:00","reports":[
          {"reportedAt":"2024-05-01T10:00:00+00:00","comment":"ssh brute","categories":[18,22],"reporterCountryCode":"US"},
          {"reportedAt":"2024-04-20T10:00:00+00:00","comment":"","categories":[22],"reporterCountryCode":null},
          {"reportedAt":"2024-01-01T10:00:00+00:00","comment":"scan","categories":[14]}]}}
    """.trimIndent()

    @Test
    fun `parses abuseipdb response`() {
        val result = AbuseIpDbParser.parse(abuseJson, "x")!!
        assertEquals("118.25.6.39", result.ipAddress)
        assertEquals(100, result.abuseConfidenceScore)
        assertEquals("China", result.countryName)
        assertEquals(3, result.reports.size)
        assertEquals(listOf(18, 22), result.reports[0].categories)
        assertNull(result.reports[1].reporterCountryCode)
        assertFalse(result.isTor)
        assertNull(AbuseIpDbParser.parse("not json", "x"))
        assertNull(AbuseIpDbParser.parse("{}", "x"))
    }

    @Test
    fun `parses abuseipdb error body`() {
        val body = """{"errors":[{"detail":"The ip address must be a valid IPv4 or IPv6 address.","status":422}]}"""
        assertEquals("The ip address must be a valid IPv4 or IPv6 address.", AbuseIpDbParser.parseError(body))
        assertNull(AbuseIpDbParser.parseError("<html>"))
        assertNull(AbuseIpDbParser.parseError(null))
    }

    @Test
    fun `assesses a malicious address`() {
        val result = AbuseIpDbParser.parse(abuseJson, "x")!!
        val assessment = ThreatIntelAnalyzer.assess(result, OffsetDateTime.parse("2024-05-03T00:00:00Z"))
        assertEquals(ThreatLevel.MALICIOUS, assessment.level)
        assertEquals(1, assessment.reportsLast7Days)
        assertEquals(2, assessment.reportsLast30Days)
        assertEquals(listOf("SSH" to 2, "Port Scan" to 1, "Brute-Force" to 1), assessment.topCategories)
        assertTrue(assessment.notes.any { it.contains("Hosting") })
        assertEquals(listOf("Brute-Force", "SSH"), ThreatIntelAnalyzer.categoryNames(result.reports[0]))
        assertEquals("Category 99", ThreatIntelAnalyzer.categoryName(99))

        val draft = ThreatIntelAnalyzer.toDrafts(result).single()
        assertEquals(FindingTools.THREAT_INTEL, draft.tool)
        assertEquals("118.25.6.39", draft.target)
        assertEquals(RiskLevel.HIGH, draft.severity)
        assertTrue(draft.detail.contains("100%"))
    }

    @Test
    fun `threat level thresholds`() {
        val base = AbuseIpDbParser.parse(abuseJson, "x")!!
        assertEquals(ThreatLevel.SUSPICIOUS, ThreatIntelAnalyzer.level(base.copy(abuseConfidenceScore = 30)))
        assertEquals(RiskLevel.MEDIUM, ThreatIntelAnalyzer.toDrafts(base.copy(abuseConfidenceScore = 30)).single().severity)
        assertEquals(ThreatLevel.LOW, ThreatIntelAnalyzer.level(base.copy(abuseConfidenceScore = 5)))
        assertEquals(ThreatLevel.CLEAN, ThreatIntelAnalyzer.level(base.copy(abuseConfidenceScore = 0, totalReports = 0)))
        val whitelisted = base.copy(isWhitelisted = true)
        assertEquals(ThreatLevel.WHITELISTED, ThreatIntelAnalyzer.level(whitelisted))
        assertTrue(ThreatIntelAnalyzer.toDrafts(whitelisted).isEmpty())
        assertTrue(ThreatIntelAnalyzer.toDrafts(base.copy(abuseConfidenceScore = 5)).isEmpty())
    }

    // ---- Shodan -----------------------------------------------------------------------

    private val shodanHostJson = """
        {"ip_str":"1.2.3.4","org":"Example Org","isp":"Example ISP","asn":"AS123","os":null,"ports":[443,23],
        "hostnames":["a.example.com"],"domains":["example.com"],"tags":["cloud"],"city":"Berlin",
        "country_name":"Germany","last_update":"2024-05-01T12:00:00.000000","vulns":["CVE-2021-41773"],
        "data":[
          {"port":23,"transport":"tcp","data":"login: ","_shodan":{"module":"telnet"}},
          {"port":443,"transport":"tcp","product":"Apache httpd","version":"2.4.49","data":"HTTP/1.1 200 OK",
           "cpe23":["cpe:2.3:a:apache:http_server:2.4.49"],
           "vulns":{"CVE-2021-42013":{"cvss":9.8},"CVE-2021-41773":{"cvss":7.5}},"_shodan":{"module":"https"}},
          {"port":8080,"data":"x"}]}
    """.trimIndent()

    @Test
    fun `parses shodan host report`() {
        val report = ShodanParser.parseHost(shodanHostJson)!!
        assertEquals("1.2.3.4", report.ip)
        assertNull(report.os)
        assertEquals(listOf(23, 443, 8080), report.ports)
        assertEquals(listOf("CVE-2021-42013", "CVE-2021-41773"), report.vulns)
        assertEquals(3, report.services.size)
        val https = report.services.first { it.port == 443 }
        assertEquals("Apache httpd 2.4.49", https.productLabel)
        assertEquals("https", https.module)
        assertEquals(2, https.vulns.size)
        assertEquals("Germany", report.country)
    }

    @Test
    fun `shodan parser tolerates missing fields`() {
        val minimal = ShodanParser.parseHost("""{"ip_str":"5.6.7.8"}""")!!
        assertTrue(minimal.ports.isEmpty() && minimal.services.isEmpty() && minimal.hostnames.isEmpty())
        assertNull(ShodanParser.parseHost("not json"))
        assertNull(ShodanParser.parseHost("{}"))

        val search = ShodanParser.parseSearch(
            """{"total":2,"matches":[{"ip_str":"1.1.1.1","port":80,"org":"X","data":" hi ","hostnames":["h"],
               "location":{"city":"Paris","country_name":"France"}},{"port":1}]}"""
        )!!
        assertEquals(2, search.total)
        assertEquals(1, search.matches.size)
        assertEquals("Paris, France", search.matches[0].location)
        assertEquals("hi", search.matches[0].banner)
        assertEquals(0, ShodanParser.parseSearch("{}")!!.total)

        assertEquals("Invalid API key", ShodanParser.parseError("""{"error":"Invalid API key"}"""))
        assertEquals("No information available", ShodanParser.parseError("""{"detail":"No information available"}"""))
        assertNull(ShodanParser.parseError("oops"))
    }

    @Test
    fun `parses internetdb and sorts cves newest first`() {
        val data = ShodanParser.parseInternetDb(
            """{"cpes":[],"hostnames":["dns.google"],"ip":"8.8.8.8","ports":[443,53],"tags":[],"vulns":["cve-2020-1","CVE-2023-5"]}"""
        )!!
        assertEquals(listOf(53, 443), data.ports)
        assertEquals(listOf("CVE-2023-5", "CVE-2020-1"), data.vulns)
        assertEquals("InternetDB", ShodanAnalyzer.internetDbLog(data)["type"])
    }

    @Test
    fun `shodan findings cover risky ports and cves`() {
        val report = ShodanParser.parseHost(shodanHostJson)!!
        assertTrue(ShodanAnalyzer.exposures(report.ports).any { it.port == 23 && it.risk == RiskLevel.HIGH })
        assertTrue(ShodanAnalyzer.exposures(listOf(443)).isEmpty())

        val drafts = ShodanAnalyzer.toDrafts(report)
        assertTrue(drafts.all { it.tool == FindingTools.SHODAN && it.target == "1.2.3.4" })
        assertTrue(drafts.any { it.title == "Telnet exposed to the internet (23/tcp)" })
        val cves = drafts.single { it.title == "Known CVEs associated with internet-facing services" }
        assertTrue(cves.detail.contains("CVE-2021-42013"))
        assertTrue(ShodanAnalyzer.toDrafts(report.copy(ports = listOf(443), vulns = emptyList())).isEmpty())
        assertTrue(ShodanAnalyzer.summary(report).contains("Open ports: 23, 443, 8080"))
    }

    @Test
    fun `shodan log payloads keep the keys reports read`() {
        val report = ShodanParser.parseHost(shodanHostJson)!!
        val host = ShodanAnalyzer.hostLog(report)
        assertEquals("1.2.3.4", host["ip_str"])
        assertEquals("Germany", host["country_name"])
        assertEquals(listOf(23, 443, 8080), host["ports"])

        val search = ShodanParser.parseSearch("""{"total":1,"matches":[{"ip_str":"1.1.1.1","port":80}]}""")!!
        val log = ShodanAnalyzer.searchLog("apache", search)
        assertEquals(1, log["total"])
        @Suppress("UNCHECKED_CAST")
        val first = (log["matches"] as List<Map<String, Any?>>).first()
        assertEquals("1.1.1.1", first["ip_str"])
        assertEquals(80, first["port"])
    }

    // ---- Breaches ---------------------------------------------------------------------

    private val breachesJson = """
        [{"Name":"Adobe","Title":"Adobe","Domain":"adobe.com","BreachDate":"2013-10-04","AddedDate":"2013-12-04T00:00:00Z",
          "PwnCount":152445165,"Description":"In October 2013, <a href=\"x\">153 million</a> Adobe accounts were breached &amp; leaked.",
          "LogoPath":"x.png","DataClasses":["Email addresses","Password hints","Passwords","Usernames"],
          "IsVerified":true,"IsFabricated":false,"IsSensitive":false,"IsRetired":false,"IsSpamList":false},
         {"Name":"Spam","Title":"Spam List","BreachDate":"2020-01-01","DataClasses":["Email addresses"],"IsVerified":true,"IsSpamList":true},
         {"Name":"Stealer","BreachDate":"2024-02-02","DataClasses":["Email addresses","Passwords"],"IsStealerLog":true}]
    """.trimIndent()

    @Test
    fun `parses and rates breaches`() {
        val breaches = HibpParser.parseBreaches(breachesJson)
        assertEquals(3, breaches.size)
        val (adobe, spam, stealer) = breaches
        assertEquals(152445165L, adobe.pwnCount)
        assertEquals("Stealer", stealer.title)
        assertEquals(RiskLevel.HIGH, BreachAnalyzer.severity(adobe))
        assertEquals(RiskLevel.LOW, BreachAnalyzer.severity(spam))
        assertEquals(RiskLevel.CRITICAL, BreachAnalyzer.severity(stealer))
        assertEquals(RiskLevel.LOW, BreachAnalyzer.severity(adobe.copy(isVerified = false)))
        assertEquals("Stealer", BreachAnalyzer.sorted(breaches).first().name)
        assertEquals(
            "In October 2013, 153 million Adobe accounts were breached & leaked.",
            BreachAnalyzer.cleanDescription(adobe.description)
        )
        assertEquals("152.4M", BreachAnalyzer.formatCount(adobe.pwnCount))
        assertTrue(HibpParser.parseBreaches("garbage").isEmpty())
    }

    @Test
    fun `summarises breaches and builds findings`() {
        val breaches = HibpParser.parseBreaches(breachesJson)
        val pastes = HibpParser.parsePastes("""[{"Source":"Pastebin","Id":"8Q0BvKD8","Title":"syslog","Date":"2014-03-04T19:14:54Z","EmailCount":139}]""")
        assertEquals(Paste("Pastebin", "8Q0BvKD8", "syslog", "2014-03-04T19:14:54Z", 139), pastes.single())

        val summary = BreachAnalyzer.summarize(breaches, pastes)
        assertEquals(3, summary.total)
        assertEquals(2, summary.passwordExposures)
        assertEquals("2024-02-02", summary.mostRecent)
        assertEquals("2013-10-04", summary.earliest)
        assertEquals(RiskLevel.CRITICAL, summary.severity)
        assertEquals("Email addresses" to 3, summary.exposedClasses.first())
        assertTrue(summary.advice.any { it.contains("multi-factor") })

        val drafts = BreachAnalyzer.toDrafts("a@b.com", breaches, pastes)
        assertEquals(2, drafts.size)
        assertEquals("Account found in public data breaches", drafts[0].title)
        assertEquals(RiskLevel.CRITICAL, drafts[0].severity)
        assertTrue(drafts[0].detail.startsWith("2 breaches"))
        assertFalse(drafts[0].detail.contains("Spam List"))
        assertEquals("Account found in public pastes", drafts[1].title)
        assertTrue(drafts.all { it.tool == FindingTools.BREACH && it.target == "a@b.com" })

        assertTrue(BreachAnalyzer.toDrafts("a@b.com", breaches.filter { it.isSpamList }, emptyList()).isEmpty())
        assertTrue(BreachAnalyzer.isEmail("user@example.com"))
        assertFalse(BreachAnalyzer.isEmail("username"))
    }

    @Test
    fun `pwned passwords k-anonymity matching`() {
        val hash = PwnedPasswords.sha1Hex("password")
        assertEquals("5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8", hash)
        assertEquals("5BAA6", PwnedPasswords.prefix(hash))
        val body = "003D68EB55068C33ACE09247EE4C639306B:3\r\n1e4c9b93f3f0682250b6cf8331b7ee68fd8:9545824\r\n1E4C9B93F3F0682250B6CF8331B7EE68FD9:0"
        assertEquals(9545824, PwnedPasswords.match(hash, body))
        assertEquals(0, PwnedPasswords.match(PwnedPasswords.sha1Hex("correct horse battery staple x9"), body))
        // A padding row for the same suffix must not count as a hit.
        assertEquals(0, PwnedPasswords.match(hash, "1E4C9B93F3F0682250B6CF8331B7EE68FD8:0"))
        assertTrue(PwnedPasswords.verdict(0).startsWith("Not found"))
        assertTrue(PwnedPasswords.verdict(9545824).contains("9.5M"))
    }

    // ---- Cloud buckets ----------------------------------------------------------------

    @Test
    fun `plans bucket names from a domain`() {
        assertEquals("example.com", CloudBucketPlanner.normalizeDomain("https://www.Example.com/path"))
        assertNull(CloudBucketPlanner.normalizeDomain("localhost"))
        assertNull(CloudBucketPlanner.normalizeDomain("exa mple.com"))
        assertEquals("example", CloudBucketPlanner.baseLabel("shop.example.co.uk"))
        assertEquals("example", CloudBucketPlanner.baseLabel("example.com"))
        assertEquals("example", CloudBucketPlanner.baseLabel("a.b.example.org"))
        assertEquals(
            listOf("example", "example-com", "example-dev", "dev-example", "exampledev"),
            CloudBucketPlanner.candidateNames("example.com", listOf("dev"))
        )
        assertEquals(listOf("dev", "prod", "mykey", "backup"), CloudBucketPlanner.parseKeywords("Dev, PROD;  my_key\nbackup--"))
        assertEquals("exampledev", CloudBucketPlanner.azureAccountName("example-dev"))
        assertNull(CloudBucketPlanner.azureAccountName("ab"))
        assertFalse(CloudBucketPlanner.isValidBucketName("Has.Dots"))
    }

    @Test
    fun `builds probes per provider`() {
        val defaults = CloudProvider.entries.filter { it.enabledByDefault }.toSet()
        val probes = CloudBucketPlanner.probes(listOf("example"), defaults)
        assertEquals(
            setOf(
                "https://example.s3.amazonaws.com/?max-keys=5",
                "https://storage.googleapis.com/example?max-keys=5",
                "https://example.firebaseio.com/.json?shallow=true",
                "https://example-default-rtdb.firebaseio.com/.json?shallow=true",
                "https://example.blob.core.windows.net/?comp=list"
            ),
            probes.map { it.url }.toSet()
        )
        assertEquals(10, CloudBucketPlanner.probes(listOf("example"), defaults + CloudProvider.DO_SPACES).size)
        assertEquals("https://example.s3.eu-west-1.amazonaws.com/?max-keys=5", CloudBucketPlanner.s3Probe("example", "eu-west-1").url)
        assertTrue(CloudBucketPlanner.azureContainerProbes("example").all { it.qualifier != null && it.url.contains("restype=container") })
    }

    @Test
    fun `classifies s3 style answers`() {
        val probe = CloudBucketPlanner.s3Probe("example", null)
        val listing = CloudBucketClassifier.classify(
            probe, ProbeResponse(200, "<ListBucketResult><Contents><Key>a.txt</Key></Contents><Contents><Key>b/c.pdf</Key></Contents></ListBucketResult>")
        )
        assertEquals(BucketState.PUBLIC_LISTING, listing.state)
        assertEquals(listOf("a.txt", "b/c.pdf"), listing.sampleKeys)
        assertEquals(BucketState.PUBLIC_READ, CloudBucketClassifier.classify(probe, ProbeResponse(200, "<html>")).state)
        assertEquals(BucketState.EXISTS_PRIVATE, CloudBucketClassifier.classify(probe, ProbeResponse(403, "<Code>AccessDenied</Code>")).state)
        assertEquals(BucketState.NOT_FOUND, CloudBucketClassifier.classify(probe, ProbeResponse(404)).state)
        assertEquals(BucketState.NOT_FOUND, CloudBucketClassifier.classify(probe, ProbeResponse(null, dnsFailed = true)).state)
        assertEquals(BucketState.UNKNOWN, CloudBucketClassifier.classify(probe, ProbeResponse(null)).state)

        val redirect = CloudBucketClassifier.classify(probe, ProbeResponse(301, region = "eu-west-1"))
        assertEquals("eu-west-1", redirect.redirectRegion)
        // Already asking the right region: no second redirect, or the scanner would loop.
        val regional = CloudBucketClassifier.classify(CloudBucketPlanner.s3Probe("example", "eu-west-1"), ProbeResponse(301, region = "eu-west-1"))
        assertNull(regional.redirectRegion)
        assertEquals(BucketState.EXISTS_PRIVATE, regional.state)
    }

    @Test
    fun `classifies azure and firebase answers`() {
        val account = CloudBucketPlanner.probes(listOf("example"), setOf(CloudProvider.AZURE_BLOB)).single()
        assertEquals(BucketState.EXISTS_PRIVATE, CloudBucketClassifier.classify(account, ProbeResponse(400)).state)
        assertEquals(BucketState.NOT_FOUND, CloudBucketClassifier.classify(account, ProbeResponse(null, dnsFailed = true)).state)

        val container = CloudBucketPlanner.azureContainerProbes("example").first()
        val open = CloudBucketClassifier.classify(container, ProbeResponse(200, "<EnumerationResults><Blobs><Blob><Name>x.bak</Name></Blob></Blobs></EnumerationResults>"))
        assertEquals(BucketState.PUBLIC_LISTING, open.state)
        assertEquals(listOf("x.bak"), open.sampleKeys)
        assertEquals(BucketState.NOT_FOUND, CloudBucketClassifier.classify(container, ProbeResponse(404)).state)

        val firebase = CloudBucketPlanner.probes(listOf("example"), setOf(CloudProvider.FIREBASE)).first()
        assertEquals(BucketState.PUBLIC_LISTING, CloudBucketClassifier.classify(firebase, ProbeResponse(200, "null")).state)
        assertEquals(listOf("users"), CloudBucketClassifier.classify(firebase, ProbeResponse(200, """{"users":true}""")).sampleKeys)
        assertEquals(BucketState.EXISTS_PRIVATE, CloudBucketClassifier.classify(firebase, ProbeResponse(401)).state)
        assertEquals(BucketState.NOT_FOUND, CloudBucketClassifier.classify(firebase, ProbeResponse(404)).state)
    }

    @Test
    fun `only public assets become findings`() {
        val probe = CloudBucketPlanner.s3Probe("example", null)
        val draft = CloudBucketClassifier.toDraft(probe, BucketVerdict(BucketState.PUBLIC_LISTING, "Anonymous listing allowed", listOf("a.txt")))!!
        assertEquals(FindingTools.CLOUD, draft.tool)
        assertEquals("example.s3.amazonaws.com", draft.target)
        assertEquals(RiskLevel.HIGH, draft.severity)
        assertEquals("Publicly listable AWS S3 bucket", draft.title)
        assertTrue(draft.detail.contains("a.txt"))
        assertNull(CloudBucketClassifier.toDraft(probe, BucketVerdict(BucketState.EXISTS_PRIVATE, "x")))
        assertNull(CloudBucketClassifier.toDraft(probe, BucketVerdict(BucketState.PUBLIC_READ, "x")))

        val container = CloudBucketPlanner.azureContainerProbes("example").first()
        assertEquals(
            "Publicly listable Azure Blob Storage container",
            CloudBucketClassifier.toDraft(container, BucketVerdict(BucketState.PUBLIC_LISTING, "x"))!!.title
        )
        val firebase = CloudBucketPlanner.probes(listOf("example"), setOf(CloudProvider.FIREBASE)).first()
        assertEquals(
            "Firebase database readable without authentication",
            CloudBucketClassifier.toDraft(firebase, BucketVerdict(BucketState.PUBLIC_LISTING, "x"))!!.title
        )
    }

    // ---- Usernames --------------------------------------------------------------------

    private fun site(name: String) = UsernameSitesDatabase.sites.first { it.name == name }

    @Test
    fun `normalises usernames`() {
        assertEquals("torvalds", UsernameCheckEvaluator.normalize("@torvalds"))
        assertEquals("torvalds", UsernameCheckEvaluator.normalize(" https://github.com/torvalds/ "))
        assertNull(UsernameCheckEvaluator.normalize("bad name"))
        assertNull(UsernameCheckEvaluator.normalize("a/b?c"))
        assertNull(UsernameCheckEvaluator.normalize(""))
        assertNull(UsernameCheckEvaluator.normalize(".."))
    }

    @Test
    fun `status code sites need the profile url to survive redirects`() {
        val github = site("GitHub")
        assertEquals(UsernameStatus.FOUND, UsernameCheckEvaluator.evaluate(github, "torvalds", 200, "", "https://github.com/Torvalds").status)
        assertEquals(UsernameStatus.NOT_FOUND, UsernameCheckEvaluator.evaluate(github, "nobody", 404, "", "https://github.com/nobody").status)
        // GitLab answers a missing profile with a redirect to its sign-in page (HTTP 200).
        val gitlab = UsernameCheckEvaluator.evaluate(site("GitLab"), "nobody", 200, "", "https://gitlab.com/users/sign_in")
        assertEquals(UsernameStatus.NOT_FOUND, gitlab.status)
        assertNotNull(gitlab.note)
        assertEquals(UsernameStatus.NOT_FOUND, UsernameCheckEvaluator.evaluate(site("Bluesky"), "nobody", 400, "", "").status)
    }

    @Test
    fun `blocked checks are uncertain, not misses`() {
        val github = site("GitHub")
        listOf(401, 403, 429, 503, 999).forEach { code ->
            assertEquals("HTTP $code", UsernameStatus.UNCERTAIN, UsernameCheckEvaluator.evaluate(github, "x", code, "", "").status)
        }
        val challenge = UsernameCheckEvaluator.evaluate(github, "x", 200, "<title>Just a moment...</title>", "https://github.com/x")
        assertEquals(UsernameStatus.UNCERTAIN, challenge.status)
    }

    @Test
    fun `body marker sites`() {
        val telegram = site("Telegram")
        assertEquals(UsernameStatus.FOUND, UsernameCheckEvaluator.evaluate(telegram, "durov", 200, "<div class=\"tgme_page_title\">", "https://t.me/durov").status)
        assertEquals(UsernameStatus.NOT_FOUND, UsernameCheckEvaluator.evaluate(telegram, "nobody", 200, "<html>", "https://t.me/nobody").status)
        val steam = site("Steam")
        assertEquals(
            UsernameStatus.NOT_FOUND,
            UsernameCheckEvaluator.evaluate(steam, "nobody", 200, "The specified profile could not be found.", "").status
        )
        assertEquals(UsernameStatus.FOUND, UsernameCheckEvaluator.evaluate(steam, "gabe", 200, "<html>profile</html>", "").status)
        val npm = site("npm (maintainers)")
        assertEquals(UsernameStatus.NOT_FOUND, UsernameCheckEvaluator.evaluate(npm, "nobody", 200, """{"objects":[],"total":0}""", "").status)
    }

    @Test
    fun `site rules are well formed`() {
        val sites = UsernameSitesDatabase.sites
        assertEquals(sites.size, sites.map { it.name }.distinct().size)
        sites.forEach { s ->
            assertTrue(s.name, s.profileUrlTemplate.startsWith("https://") && s.profileUrlTemplate.contains("{username}"))
            assertTrue(s.name, s.getCheckUrl("alice").startsWith("https://") && s.getCheckUrl("alice").contains("alice"))
            assertFalse(s.name, s.getCheckUrl("alice").contains("{username}"))
            s.usernamePattern?.let { Regex(it) }
            if (s.detection != com.fearmikey.rf_reapr.domain.model.UsernameDetection.STATUS_CODE) assertTrue(s.name, s.markers.isNotEmpty())
        }
        assertFalse(UsernameCheckEvaluator.isAllowedFor(site("Telegram"), "ab"))
        assertFalse(UsernameCheckEvaluator.isAllowedFor(site("GitHub"), "-bad"))
        assertTrue(UsernameCheckEvaluator.isAllowedFor(site("GitHub"), "torvalds"))
        assertTrue(UsernameCheckEvaluator.isAllowedFor(site("GitLab"), "any.name_here"))
    }

    @Test
    fun `sorts and reports username results`() {
        fun result(name: String, status: UsernameStatus) = UsernameCheckResult(site(name), "alice", status, site(name).getProfileUrl("alice"))
        val results = listOf(
            result("Vimeo", UsernameStatus.NOT_FOUND), result("Reddit", UsernameStatus.UNCERTAIN),
            result("GitHub", UsernameStatus.FOUND), result("Disqus", UsernameStatus.FOUND)
        )
        assertEquals(listOf("Disqus", "GitHub", "Reddit", "Vimeo"), UsernameCheckEvaluator.sorted(results).map { it.site.name })
        val report = UsernameCheckEvaluator.report("alice", results)
        assertTrue(report.contains("Found on 2 of 4 sites checked"))
        assertTrue(report.contains("GitHub: https://github.com/alice"))
        assertTrue(report.contains("Reddit: https://www.reddit.com/user/alice"))
    }
}
