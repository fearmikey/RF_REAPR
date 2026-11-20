package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.repository.DiscoveredService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MdnsServiceCatalogTest {

    @Test
    fun `normalizes the type spellings Android returns`() {
        listOf("_http._tcp", "_http._tcp.", "._http._tcp", "_http._tcp.local.", "_HTTP._TCP.local")
            .forEach { assertEquals(it, "_http._tcp", MdnsServiceCatalog.normalizeType(it)) }
    }

    @Test
    fun `converts meta-query results into browsable types`() {
        assertEquals("_ipp._tcp", MdnsServiceCatalog.typeFromMetaQueryResult("_ipp", "_tcp.local."))
        assertEquals("_matterc._udp", MdnsServiceCatalog.typeFromMetaQueryResult("_matterc", "_udp.local."))
        assertNull(MdnsServiceCatalog.typeFromMetaQueryResult("Living Room TV", "_tcp.local."))
        assertNull(MdnsServiceCatalog.typeFromMetaQueryResult("_ipp", "local."))
    }

    @Test
    fun `describes known types and falls back for unknown ones`() {
        val ssh = MdnsServiceCatalog.describe("_ssh._tcp.")
        assertEquals("SSH", ssh.label)
        assertEquals(ServiceCategory.REMOTE_ACCESS, ssh.category)
        assertNotNull(ssh.securityNote)

        val unknown = MdnsServiceCatalog.describe("_foobar._tcp")
        assertEquals("foobar", unknown.label)
        assertEquals(ServiceCategory.OTHER, unknown.category)
        assertNull(unknown.securityNote)
    }

    @Test
    fun `builds browser urls for web services only`() {
        val http = DiscoveredService("Router", "_http._tcp", "192.168.1.1", 8080, mapOf("path" to "/admin"))
        assertEquals("http://192.168.1.1:8080/admin", MdnsServiceCatalog.browserUrl(http))
        val v6 = DiscoveredService("NAS", "_https._tcp", "fe80::1", 443)
        assertEquals("https://[fe80::1]:443", MdnsServiceCatalog.browserUrl(v6))
        assertNull(MdnsServiceCatalog.browserUrl(DiscoveredService("Printer", "_ipp._tcp", "192.168.1.9", 631)))
    }

    @Test
    fun `groups services by host with friendly names and flagged hosts first`() {
        val services = listOf(
            DiscoveredService("Living Room", "_googlecast._tcp", "192.168.1.30", 8009, mapOf("fn" to "Living Room TV", "md" to "Chromecast")),
            DiscoveredService("HP OfficeJet", "_ipp._tcp", "192.168.1.40", 631, mapOf("ty" to "HP OfficeJet Pro 9010")),
            DiscoveredService("HP OfficeJet", "_pdl-datastream._tcp", "192.168.1.40", 9100),
            DiscoveredService("HP OfficeJet", "_http._tcp", "192.168.1.40", 80)
        )
        val hosts = MdnsServiceCatalog.groupByHost(services)
        assertEquals(2, hosts.size)

        val printer = hosts.first()
        assertEquals("192.168.1.40", printer.address)
        assertEquals("HP OfficeJet Pro 9010", printer.displayName)
        assertEquals(ServiceCategory.PRINTING, printer.primaryCategory)
        assertEquals(2, printer.securityNotes.size) // raw 9100 + plain HTTP

        assertEquals("Living Room TV", hosts[1].displayName)
        assertEquals(ServiceCategory.MEDIA, hosts[1].primaryCategory)
    }
}
