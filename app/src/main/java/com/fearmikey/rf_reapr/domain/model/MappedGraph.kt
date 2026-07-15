package com.fearmikey.rf_reapr.domain.model

data class MappedNode(
    val node: NetworkNode,
    val x: Float,
    val y: Float
)

data class MappedGraph(
    val nodes: List<MappedNode>,
    val edges: List<NodesEdge>
)
