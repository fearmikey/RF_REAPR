package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortCatalogTest {

    @Test
    fun `parses lists and ranges`() {
        val result = PortCatalog.parsePortSpec("443, 22,80-82 22") as PortCatalog.ParseResult.Ok
        assertEquals(listOf(22, 80, 81, 82, 443), result.ports)
    }

    @Test
    fun `rejects invalid specs`() {
        assertTrue(PortCatalog.parsePortSpec("") is PortCatalog.ParseResult.Invalid)
        assertTrue(PortCatalog.parsePortSpec("0") is PortCatalog.ParseResult.Invalid)
        assertTrue(PortCatalog.parsePortSpec("70000") is PortCatalog.ParseResult.Invalid)
        assertTrue(PortCatalog.parsePortSpec("100-10") is PortCatalog.ParseResult.Invalid)
        assertTrue(PortCatalog.parsePortSpec("ssh") is PortCatalog.ParseResult.Invalid)
        assertTrue(PortCatalog.parsePortSpec("1-2-3") is PortCatalog.ParseResult.Invalid)
    }

    @Test
    fun `builds web urls`() {
        assertEquals("http://10.0.0.1", PortCatalog.webUrl("10.0.0.1", 80, "HTTP", null))
        assertEquals("https://10.0.0.1:8443", PortCatalog.webUrl("10.0.0.1", 8443, "HTTPS-Alt", null))
        assertEquals("http://[fe80::1]:8080", PortCatalog.webUrl("fe80::1", 8080, "HTTP-Proxy", null))
        assertNull(PortCatalog.webUrl("10.0.0.1", 22, "SSH", "SSH-2.0-OpenSSH_9.0"))
    }

    @Test
    fun `exposure risk picks the worst port`() {
        assertEquals(RiskLevel.CRITICAL, PortCatalog.exposureRisk(listOf(22, 6379, 23)))
        assertNull(PortCatalog.exposureRisk(listOf(22, 443)))
        assertTrue(PortCatalog.COMMON_PORTS.containsAll(listOf(22, 80, 443, 3389, 5555)))
        assertEquals(PortCatalog.COMMON_PORTS.sorted(), PortCatalog.COMMON_PORTS)
    }
}
