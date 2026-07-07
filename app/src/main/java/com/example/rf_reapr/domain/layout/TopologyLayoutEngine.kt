package com.example.rf_reapr.domain.layout

import com.example.rf_reapr.domain.model.*

/**
 * Engine to calculate the visual distribution of nodes in a network graph.
 * Uses a Recursive Tree-Depth Layout with infrastructure staggering and clustered centering.
 */
object TopologyLayoutEngine {

    private const val INTRA_CLUSTER_SPACING = 220f
    private const val INTER_CLUSTER_BUFFER = 200f
    private const val VERTICAL_TIER_DISTANCE = 400f
    private const val START_Y = -600f
    private const val STAGGER_OFFSET = 150f

    /**
     * Calculates (x, y) coordinates for all nodes in the graph using a Depth-Based Hierarchical Layout.
     */
    fun calculateRadialLayout(
        graph: NetworkGraph,
        baseRadius: Float = 300f, // Unused but kept for API compatibility
        gatewayIp: String? = null,
    ): MappedGraph {
        if (graph.nodes.isEmpty()) return MappedGraph(emptyList(), graph.edges)

        val mappedNodes = mutableMapOf<String, MappedNode>()
        
        // 1. Identify Root (Gateway)
        val gateway = graph.nodes.find { it.deviceType == DeviceType.GATEWAY || it.ipAddress == gatewayIp }
            ?: graph.nodes.first()

        // 2. Build Adjacency Map
        val childrenMap = graph.edges.groupBy { it.fromNodeId }
            .mapValues { entry -> entry.value.map { edge -> edge.toNodeId } }

        // 3. Recursive Layout Function
        fun layoutSubtree(nodeId: String, depth: Int, startX: Float): Float {
            val node = graph.nodes.find { it.id == nodeId } ?: return 0f
            val children = childrenMap[nodeId] ?: emptyList()
            
            val yPos = START_Y + (depth * VERTICAL_TIER_DISTANCE)
            
            if (children.isEmpty()) {
                // Leaf Node: Apply vertical stagger for end-devices
                // Note: We'll refine staggered Y later in the final mapping, 
                // here we just return the width occupied by this node.
                mappedNodes[nodeId] = MappedNode(node, startX, yPos)
                return INTRA_CLUSTER_SPACING
            }

            // Calculate width needed for all children subtrees
            var currentChildX = startX
            var totalWidth = 0f
            
            children.forEach { childId ->
                val childWidth = layoutSubtree(childId, depth + 1, currentChildX)
                currentChildX += childWidth + (if (children.size > 1) INTER_CLUSTER_BUFFER else 0f)
                totalWidth += childWidth + (if (children.size > 1) INTER_CLUSTER_BUFFER else 0f)
            }
            
            // Remove the trailing buffer
            if (children.size > 1) totalWidth -= INTER_CLUSTER_BUFFER

            // Center parent above its children
            val parentX = startX + totalWidth / 2f - (INTRA_CLUSTER_SPACING / 2f)
            mappedNodes[nodeId] = MappedNode(node, parentX, yPos)
            
            return totalWidth
        }

        // 4. Initial Layout Pass
        layoutSubtree(gateway.id, 0, 0f)

        // 5. Final Adjustment Pass (Center globally and apply end-device staggering)
        val allNodesList = mappedNodes.values.toList()
        val minX = allNodesList.minOf { it.x }
        val maxX = allNodesList.maxOf { it.x }
        val globalOffsetX = -(minX + maxX) / 2f

        val finalNodes = allNodesList.map { mNode ->
            var finalY = mNode.y
            
            // Apply 3-level vertical stagger only to end-devices (leaves that aren't infra)
            if (mNode.node.deviceType != DeviceType.GATEWAY && 
                mNode.node.deviceType != DeviceType.SWITCH && 
                mNode.node.deviceType != DeviceType.ACCESS_POINT) {
                
                // Use a stable index for staggering based on parent's child order
                val parentId = graph.edges.find { it.toNodeId == mNode.node.id }?.fromNodeId
                val siblings = childrenMap[parentId] ?: emptyList()
                val siblingIndex = siblings.indexOf(mNode.node.id)
                
                if (siblingIndex != -1) {
                    finalY += (siblingIndex % 3) * STAGGER_OFFSET
                }
            }

            mNode.copy(x = mNode.x + globalOffsetX, y = finalY)
        }

        return MappedGraph(finalNodes, graph.edges)
    }
}
