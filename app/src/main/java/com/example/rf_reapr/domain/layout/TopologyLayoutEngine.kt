package com.example.rf_reapr.domain.layout

import com.example.rf_reapr.domain.model.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/**
 * Engine to calculate the visual distribution of nodes in a network graph.
 * Uses a Radial Layout algorithm where the Gateway is the center point.
 */
object TopologyLayoutEngine {

    /**
     * Calculates (x, y) coordinates for all nodes in the graph.
     * 
     * @param graph The input [NetworkGraph] containing nodes and edges.
     * @param baseRadius The radius of the first concentric circle.
     * @param gatewayIp The IP of the central node (Gateway).
     * @return A [MappedGraph] with positioned nodes.
     */
    fun calculateRadialLayout(
        graph: NetworkGraph,
        baseRadius: Float = 300f,
        gatewayIp: String? = null,
    ): MappedGraph {
        val mappedNodes = mutableListOf<MappedNode>()
        
        // 1. Identify the center node (Gateway)
        val centerNode = if (gatewayIp != null) {
            graph.nodes.find { it.ipAddress == gatewayIp }
        } else {
            graph.nodes.firstOrNull()
        }

        if (centerNode == null) return MappedGraph(emptyList(), graph.edges)

        // Place center node at (0, 0)
        mappedNodes.add(MappedNode(centerNode, 0f, 0f))

        // 2. Identify all other nodes
        val leafNodes = graph.nodes.filter { it.id != centerNode.id }
        if (leafNodes.isEmpty()) return MappedGraph(mappedNodes, graph.edges)

        // 3. Distribute nodes in concentric circles
        val nodesPerCircle = 8
        leafNodes.forEachIndexed { index, node ->
            val circleIndex = (index / nodesPerCircle) + 1
            val positionInCircle = index % nodesPerCircle
            
            // Calculate how many nodes are in THIS specific layer
            val layerStart = (circleIndex - 1) * nodesPerCircle
            val layerEnd = kotlin.math.min(circleIndex * nodesPerCircle, leafNodes.size)
            val nodesInThisLayer = layerEnd - layerStart

            val angle = (2 * PI * positionInCircle) / nodesInThisLayer
            val currentRadius = baseRadius * circleIndex

            val x = (currentRadius * cos(angle)).toFloat()
            val y = (currentRadius * sin(angle)).toFloat()

            mappedNodes.add(MappedNode(node, x, y))
        }

        return MappedGraph(mappedNodes, graph.edges)
    }
}
