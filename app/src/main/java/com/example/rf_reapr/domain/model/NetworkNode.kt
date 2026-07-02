package com.example.rf_reapr.domain.model

/**
 * Represents a device discovered on the network.
 */
data class NetworkNode(
    val id: String,
    val ipAddress: String,
    val macAddress: String? = null,
    val hostname: String? = null,
    val riskLevel: RiskLevel = RiskLevel.LOW,
    val openPorts: List<OpenPort> = emptyList()
)

/**
 * Represents a connection between two nodes (e.g., Gateway to Device).
 */
data class NodesEdge(
    val fromNodeId: String,
    val toNodeId: String,
    val connectionType: String = "Subnet Link"
)

/**
 * Container for the entire network graph structure.
 */
data class NetworkGraph(
    val nodes: List<NetworkNode> = emptyList(),
    val edges: List<NodesEdge> = emptyList()
)
