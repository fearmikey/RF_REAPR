package com.example.rf_reapr.domain.mapper

import com.example.rf_reapr.domain.model.*

/**
 * Maps raw scanning results into a structured network topology graph.
 */
object TopologyMapper {
    
    /**
     * Converts a map of IP addresses and their detected open ports into a [NetworkGraph].
     */
    fun mapScanResultsToGraph(
        scanResults: Map<String, List<OpenPort>>,
        gatewayIp: String? = null,
    ): NetworkGraph {
        val nodes = scanResults.map { (ip, ports) ->
            NetworkNode(
                id = ip,
                ipAddress = ip,
                openPorts = ports,
                riskLevel = calculateHighestRisk(ports),
                // In future versions, MAC and Hostname will be resolved here
                macAddress = null,
                hostname = if (ip == "127.0.0.1") "localhost" else null
            )
        }

        val edges = mutableListOf<NodesEdge>()
        if (gatewayIp != null) {
            nodes.forEach { node ->
                if (node.ipAddress != gatewayIp) {
                    edges.add(NodesEdge(gatewayIp, node.id, "Gateway to Device"))
                }
            }
        }

        return NetworkGraph(nodes, edges)
    }

    fun mapDiscoveredNodesToGraph(
        nodes: List<NetworkNode>,
        gatewayIp: String? = null
    ): NetworkGraph {
        val edges = mutableListOf<NodesEdge>()
        if (gatewayIp != null) {
            nodes.forEach { node ->
                if (node.ipAddress != gatewayIp) {
                    edges.add(NodesEdge(gatewayIp, node.id, "Gateway to Device"))
                }
            }
        }
        return NetworkGraph(nodes, edges)
    }

    /**
     * Determines the overall risk level of a node based on the vulnerabilities found in its ports.
     */
    private fun calculateHighestRisk(ports: List<OpenPort>): RiskLevel {
        val allVulnerabilities = ports.flatMap { it.vulnerabilities }
        if (allVulnerabilities.isEmpty()) return RiskLevel.LOW
        
        return allVulnerabilities
            .asSequence()
            .map { it.severity }
            .maxByOrNull { it.ordinal } ?: RiskLevel.LOW
    }
}
