package com.example.rf_reapr.domain.mapper

import com.example.rf_reapr.domain.model.NetworkNode
import com.example.rf_reapr.domain.model.OpenPort
import org.junit.Assert.assertEquals
import org.junit.Test

class TopologyMapperTest {

    @Test
    fun `mapDiscoveredNodesToGraph should sort nodes by IP address numerically`() {
        val nodes = listOf(
            NetworkNode(id = "192.168.1.10", ipAddress = "192.168.1.10"),
            NetworkNode(id = "192.168.1.2", ipAddress = "192.168.1.2"),
            NetworkNode(id = "10.0.0.1", ipAddress = "10.0.0.1"),
            NetworkNode(id = "192.168.1.1", ipAddress = "192.168.1.1")
        )

        val result = TopologyMapper.mapDiscoveredNodesToGraph(nodes)

        assertEquals("10.0.0.1", result.nodes[0].ipAddress)
        assertEquals("192.168.1.1", result.nodes[1].ipAddress)
        assertEquals("192.168.1.2", result.nodes[2].ipAddress)
        assertEquals("192.168.1.10", result.nodes[3].ipAddress)
    }

    @Test
    fun `mapScanResultsToGraph should sort nodes by IP address numerically`() {
        val scanResults = mapOf<String, List<OpenPort>>(
            "192.168.1.10" to emptyList(),
            "192.168.1.2" to emptyList(),
            "10.0.0.1" to emptyList(),
            "192.168.1.1" to emptyList()
        )

        val result = TopologyMapper.mapScanResultsToGraph(scanResults)

        assertEquals("10.0.0.1", result.nodes[0].ipAddress)
        assertEquals("192.168.1.1", result.nodes[1].ipAddress)
        assertEquals("192.168.1.2", result.nodes[2].ipAddress)
        assertEquals("192.168.1.10", result.nodes[3].ipAddress)
    }
}
