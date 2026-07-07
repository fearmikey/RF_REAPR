package com.example.rf_reapr.domain.model

import org.junit.Assert.assertEquals
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
}
