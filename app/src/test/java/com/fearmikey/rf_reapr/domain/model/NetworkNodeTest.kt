package com.fearmikey.rf_reapr.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkNodeTest {

    @Test
    fun `HIGH_IMPACT_PORTS contains all required ports`() {
        val requiredPorts = listOf(21, 22, 23, 53, 80, 443, 445, 161, 389, 1433, 3306, 3389, 8080, 8443)
        assertEquals(requiredPorts.size, NetworkNode.HIGH_IMPACT_PORTS.size)
        requiredPorts.forEach { port ->
            assertTrue("Port $port should be in HIGH_IMPACT_PORTS", NetworkNode.HIGH_IMPACT_PORTS.contains(port))
        }
    }

    @Test
    fun `isPublicIp returns false for private and local IP addresses`() {
        assertFalse(NetworkNode.isPublicIp("192.168.1.1"))
        assertFalse(NetworkNode.isPublicIp("10.0.0.1"))
        assertFalse(NetworkNode.isPublicIp("172.16.0.1"))
        assertFalse(NetworkNode.isPublicIp("172.31.255.255"))
        assertFalse(NetworkNode.isPublicIp("127.0.0.1"))
        assertFalse(NetworkNode.isPublicIp("100.64.0.1")) // CGNAT
        assertFalse(NetworkNode.isPublicIp("169.254.1.1")) // Link-local
        assertFalse(NetworkNode.isPublicIp("0.0.0.0"))
        assertFalse(NetworkNode.isPublicIp(""))
    }

    @Test
    fun `isPublicIp returns true for public IP addresses`() {
        assertTrue(NetworkNode.isPublicIp("8.8.8.8"))
        assertTrue(NetworkNode.isPublicIp("1.1.1.1"))
        assertTrue(NetworkNode.isPublicIp("203.0.113.1"))
        assertTrue(NetworkNode.isPublicIp("172.32.0.1"))
    }

    @Test
    fun `hasPublicIp properly evaluates node IP address`() {
        val localNode = NetworkNode(id = "1", ipAddress = "192.168.1.10", deviceType = DeviceType.WORKSTATION)
        assertFalse(localNode.hasPublicIp)

        val gatewayLocalNode = NetworkNode(id = "2", ipAddress = "192.168.1.1", deviceType = DeviceType.GATEWAY)
        assertFalse(gatewayLocalNode.hasPublicIp)

        val gatewayPublicNode = NetworkNode(id = "3", ipAddress = "203.0.113.1", deviceType = DeviceType.GATEWAY)
        assertTrue(gatewayPublicNode.hasPublicIp)
    }
}
