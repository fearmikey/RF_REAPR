package com.fearmikey.rf_reapr.ui.topology

import com.fearmikey.rf_reapr.domain.model.DeviceType
import com.fearmikey.rf_reapr.domain.model.NetworkNode
import com.fearmikey.rf_reapr.domain.model.OpenPort
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.domain.model.Vulnerability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopologyFilterTest {

    private val router = NetworkNode(
        id = "192.168.1.1",
        ipAddress = "192.168.1.1",
        hostname = "gateway.lan",
        macAddress = "AA:BB:CC:00:11:22",
        manufacturer = "Netgear",
        deviceType = DeviceType.GATEWAY,
        openPorts = listOf(OpenPort(80, "http"), OpenPort(443, "https"))
    )
    private val nas = NetworkNode(
        id = "192.168.1.20",
        ipAddress = "192.168.1.20",
        hostname = "nas",
        riskLevel = RiskLevel.CRITICAL,
        deviceType = DeviceType.SERVER,
        openPorts = listOf(
            OpenPort(22, "ssh"),
            OpenPort(445, "microsoft-ds", vulnerabilities = listOf(Vulnerability("CVE-2017-0144", "EternalBlue", RiskLevel.CRITICAL)))
        )
    )
    private val phone = NetworkNode(
        id = "192.168.1.100",
        ipAddress = "192.168.1.100",
        riskLevel = RiskLevel.LOW,
        deviceType = DeviceType.MOBILE
    )
    private val camera = NetworkNode(
        id = "192.168.1.9",
        ipAddress = "192.168.1.9",
        hostname = "Camera",
        riskLevel = RiskLevel.MEDIUM,
        deviceType = DeviceType.IOT,
        openPorts = listOf(OpenPort(554, "rtsp"))
    )
    private val all = listOf(phone, nas, router, camera)

    @Test
    fun `blank query and no risk filter matches everything`() {
        assertTrue(all.all { TopologyFilter.matches(it, "  ", emptySet()) })
    }

    @Test
    fun `matches text fields case-insensitively`() {
        assertTrue(TopologyFilter.matches(router, "NETGEAR", emptySet()))
        assertTrue(TopologyFilter.matches(router, "aa:bb", emptySet()))
        assertTrue(TopologyFilter.matches(router, "gateway", emptySet()))
        assertTrue(TopologyFilter.matches(phone, "1.100", emptySet()))
        assertTrue(TopologyFilter.matches(phone, "mobile", emptySet()))
        assertFalse(TopologyFilter.matches(phone, "netgear", emptySet()))
    }

    @Test
    fun `matches port numbers services and CVEs`() {
        assertTrue(TopologyFilter.matches(nas, "445", emptySet()))
        assertTrue(TopologyFilter.matches(nas, ":22", emptySet()))
        assertTrue(TopologyFilter.matches(nas, "ssh", emptySet()))
        assertTrue(TopologyFilter.matches(nas, "cve-2017", emptySet()))
        assertFalse(TopologyFilter.matches(router, "ssh", emptySet()))
    }

    @Test
    fun `risk filter restricts results and combines with query`() {
        val criticalOnly = setOf(RiskLevel.CRITICAL)
        assertEquals(listOf(nas), all.filter { TopologyFilter.matches(it, "", criticalOnly) })
        assertTrue(all.none { TopologyFilter.matches(it, "netgear", criticalOnly) })
    }

    @Test
    fun `sort by IP uses numeric octet order`() {
        val sorted = TopologyFilter.sort(all, NodeSortOrder.IP).map { it.ipAddress }
        assertEquals(listOf("192.168.1.1", "192.168.1.9", "192.168.1.20", "192.168.1.100"), sorted)
    }

    @Test
    fun `sort by risk puts highest risk first`() {
        val sorted = TopologyFilter.sort(all, NodeSortOrder.RISK)
        assertEquals(nas, sorted.first())
        assertEquals(camera, sorted[1])
    }

    @Test
    fun `sort by open ports puts busiest hosts first`() {
        val sorted = TopologyFilter.sort(all, NodeSortOrder.OPEN_PORTS)
        assertEquals(listOf(router, nas), sorted.take(2)) // tie broken by IP
        assertEquals(phone, sorted.last())
    }

    @Test
    fun `sort by name puts unnamed hosts last`() {
        val sorted = TopologyFilter.sort(all, NodeSortOrder.NAME)
        assertEquals(listOf(camera, router, nas, phone), sorted)
    }

    @Test
    fun `summary text includes ports and CVEs`() {
        val summary = nas.toSummaryText()
        assertTrue(summary.contains("IP: 192.168.1.20"))
        assertTrue(summary.contains("445/microsoft-ds"))
        assertTrue(summary.contains("CVE-2017-0144 (Critical)"))
    }
}
