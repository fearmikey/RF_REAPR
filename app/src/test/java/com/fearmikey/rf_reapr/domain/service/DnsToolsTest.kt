package com.fearmikey.rf_reapr.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsToolsTest {

    @Test
    fun `parses doh json`() {
        val json = """
            {"Status":0,"TC":false,"RD":true,"RA":true,"AD":true,"CD":false,
             "Question":[{"name":"example.com.","type":1}],
             "Answer":[{"name":"example.com.","type":1,"TTL":3600,"data":"93.184.216.34"},
                       {"name":"example.com.","type":1,"TTL":3600,"data":"93.184.216.35"}]}
        """.trimIndent()
        val r = DohResponseParser.parse(json, "example.com", DnsRecordTypes.A, "Cloudflare", 21)
        assertEquals("NOERROR", r.statusName)
        assertTrue(r.authenticated)
        assertEquals(2, r.answers.size)
        assertEquals("example.com", r.answers[0].name)
        assertEquals("A", r.answers[0].typeName)
        assertNull(r.error)
    }

    @Test
    fun `parses nxdomain with authority`() {
        val json = """{"Status":3,"Authority":[{"name":"com.","type":6,"TTL":900,"data":"a.gtld-servers.net. nstld.verisign-grs.com. 1 1800 900 604800 86400"}],"Comment":"no such domain"}"""
        val r = DohResponseParser.parse(json, "nope.example", DnsRecordTypes.A, "Google", 5)
        assertEquals("NXDOMAIN", r.statusName)
        assertEquals(1, r.authority.size)
        assertEquals("no such domain", r.error)
        assertEquals("Malformed response", DohResponseParser.parse("<html>", "x", 1, "G", 1).error)
    }

    @Test
    fun `builds reverse lookup names`() {
        assertEquals("4.4.8.8.in-addr.arpa", DnsInput.reverseName("8.8.4.4"))
        assertEquals(
            "1.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.8.b.d.0.1.0.0.2.ip6.arpa",
            DnsInput.reverseName("2001:db8::1")
        )
        assertNull(DnsInput.reverseName("example.com"))
        assertEquals("example.com", DnsInput.normalize("https://Example.com/x"))
    }

    @Test
    fun `formats ttl`() {
        assertEquals("45s", DnsInput.formatTtl(45))
        assertEquals("5m 0s", DnsInput.formatTtl(300))
        assertEquals("1d 0h", DnsInput.formatTtl(86400))
    }

    @Test
    fun `evaluates spf and dmarc`() {
        val strong = EmailSecurityAnalyzer.analyze(
            listOf("\"v=spf1 include:_spf.google.com -all\"", "\"google-site-verification=abc\""),
            listOf("\"v=DMARC1; p=reject; rua=mailto:d@example.com\"")
        )
        assertEquals("-all", strong.spfAll)
        assertEquals("reject", strong.dmarcPolicy)
        assertTrue(strong.findings.isEmpty())

        val weak = EmailSecurityAnalyzer.analyze(listOf("v=spf1 a mx ~all"), listOf("v=DMARC1; p=none"))
        assertEquals("~all", weak.spfAll)
        assertEquals(2, weak.findings.size)

        val missing = EmailSecurityAnalyzer.analyze(emptyList(), emptyList())
        assertNull(missing.spfRecord)
        assertFalse(missing.findings.isEmpty())

        val plus = EmailSecurityAnalyzer.analyze(listOf("v=spf1 all"), listOf("v=DMARC1; p=quarantine"))
        assertEquals("+all", plus.spfAll)
    }
}
