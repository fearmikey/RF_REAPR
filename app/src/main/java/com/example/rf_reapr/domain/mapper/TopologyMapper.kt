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
        }.sortedWith(ipComparator)

        val edges = mutableListOf<NodesEdge>()
        val gateway = nodes.find { it.ipAddress == gatewayIp }
        
        nodes.forEach { node ->
            if (node.id == gateway?.id) return@forEach
            // For raw scan results, we just connect everything to the gateway for now
            // as we don't have device types yet (those are added in discovery)
            if (gateway != null) {
                edges.add(NodesEdge(gateway.id, node.id, "Gateway to Device"))
            }
        }

        return NetworkGraph(nodes, edges)
    }

    fun mapDiscoveredNodesToGraph(
        nodes: List<NetworkNode>,
        gatewayIp: String? = null
    ): NetworkGraph {
        val sortedNodes = nodes.sortedWith(ipComparator)
        val edges = mutableListOf<NodesEdge>()
        
        val gateway = sortedNodes.find { it.ipAddress == gatewayIp || it.deviceType == DeviceType.GATEWAY }
        val switches = sortedNodes.filter { it.deviceType == DeviceType.SWITCH }.sortedWith(ipComparator)
        val aps = sortedNodes.filter { it.deviceType == DeviceType.ACCESS_POINT }.sortedWith(ipComparator)
        
        sortedNodes.forEach { node ->
            if (node.id == gateway?.id) return@forEach
            
            // Prioritize explicit parent if assigned manually
            val explicitParent = sortedNodes.find { it.id == node.parentId }
            
            val parentId = if (explicitParent != null) {
                explicitParent.id
            } else {
                when (node.deviceType) {
                    DeviceType.SWITCH -> {
                        // Find a switch with a "lower" IP (more core) or fallback to gateway
                        val coreSwitch = switches.filter { ipToLong(it.ipAddress) < ipToLong(node.ipAddress) }
                            .minByOrNull { ipToLong(node.ipAddress) - ipToLong(it.ipAddress) }
                        coreSwitch?.id ?: gateway?.id
                    }
                    DeviceType.ACCESS_POINT -> {
                        // Link to the closest switch or gateway
                        val closestSwitch = switches.minByOrNull { Math.abs(ipToLong(node.ipAddress) - ipToLong(it.ipAddress)) }
                        closestSwitch?.id ?: gateway?.id
                    }
                    else -> {
                        // End device: Link to closest AP, then closest Switch, then Gateway
                        val closestAp = aps.minByOrNull { Math.abs(ipToLong(node.ipAddress) - ipToLong(it.ipAddress)) }
                        val closestSwitch = switches.minByOrNull { Math.abs(ipToLong(node.ipAddress) - ipToLong(it.ipAddress)) }
                        
                        val apDist = closestAp?.let { Math.abs(ipToLong(node.ipAddress) - ipToLong(it.ipAddress)) } ?: Long.MAX_VALUE
                        val swDist = closestSwitch?.let { Math.abs(ipToLong(node.ipAddress) - ipToLong(it.ipAddress)) } ?: Long.MAX_VALUE
                        
                        if (apDist < swDist && apDist < 10) { // Arbitrary threshold for "closer" to AP
                            closestAp?.id
                        } else {
                            closestSwitch?.id ?: gateway?.id
                        }
                    }
                }
            }
            
            if (parentId != null) {
                edges.add(NodesEdge(parentId, node.id, "Hierarchical Link"))
            }
        }

        return NetworkGraph(sortedNodes, edges)
    }

    private fun ipToLong(ip: String): Long {
        return try {
            val parts = ip.split(".").map { it.toLong() }
            (parts[0] shl 24) + (parts[1] shl 16) + (parts[2] shl 8) + parts[3]
        } catch (e: Exception) {
            0L
        }
    }

    private val ipComparator = Comparator<NetworkNode> { n1, n2 ->
        val parts1 = n1.ipAddress.split(".").mapNotNull { it.toIntOrNull() }
        val parts2 = n2.ipAddress.split(".").mapNotNull { it.toIntOrNull() }

        for (i in 0 until minOf(parts1.size, parts2.size)) {
            if (parts1[i] != parts2[i]) {
                return@Comparator parts1[i].compareTo(parts2[i])
            }
        }
        parts1.size.compareTo(parts2.size)
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
