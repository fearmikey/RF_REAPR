package com.example.rf_reapr.ui.topology

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.example.rf_reapr.domain.model.MappedGraph
import com.example.rf_reapr.domain.model.NetworkNode
import com.example.rf_reapr.domain.model.RiskLevel

/**
 * A custom-drawn view that renders the network topology using a Canvas.
 * Supports Panning, Pinch-to-Zoom, and Tap-to-Select interactions.
 */
@Composable
fun NetworkMapView(
    mappedGraph: MappedGraph,
    onNodeClick: (NetworkNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = Color.White, fontSize = 10.sp)

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale *= zoom
                    offset += pan
                }
            }
            .pointerInput(mappedGraph) {
                detectTapGestures { tapOffset ->
                    // Convert tap coordinates to Canvas space
                    val canvasWidth = size.width.toFloat()
                    val canvasHeight = size.height.toFloat()
                    val centerX = canvasWidth / 2f
                    val centerY = canvasHeight / 2f
                    
                    val clickX = (tapOffset.x - centerX - offset.x) / scale
                    val clickY = (tapOffset.y - centerY - offset.y) / scale
                    
                    // Find node under tap
                    val clickedNode = mappedGraph.nodes.find { mappedNode ->
                        val dx = mappedNode.x - clickX
                        val dy = mappedNode.y - clickY
                        // Radius of hit area is slightly larger than drawing for better UX
                        val distanceSq = dx * dx + dy * dy
                        distanceSq < (25f * 25f) 
                    }
                    
                    clickedNode?.let { onNodeClick(it.node) }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            
            withTransform(
                {
                    // Apply translation and scaling
                    translate(center.x + offset.x, center.y + offset.y)
                    scale(scale, scale, Offset.Zero)
                }
            ) {
                // 1. Draw Edges (Connections between nodes)
                mappedGraph.edges.forEach { edge ->
                    val from = mappedGraph.nodes.find { it.node.id == edge.fromNodeId }
                    val to = mappedGraph.nodes.find { it.node.id == edge.toNodeId }
                    if (from != null && to != null) {
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.5f),
                            start = Offset(from.x, from.y),
                            end = Offset(to.x, to.y),
                            strokeWidth = 2f / scale // Keep stroke consistent across zoom levels
                        )
                    }
                }

                // 2. Draw Nodes (Devices)
                mappedGraph.nodes.forEach { mappedNode ->
                    val color = when (mappedNode.node.riskLevel) {
                        RiskLevel.LOW -> Color.Green
                        RiskLevel.MEDIUM -> Color.Yellow
                        RiskLevel.HIGH -> Color(0xFFFFA500) // Orange
                        RiskLevel.CRITICAL -> Color.Red
                    }

                    drawCircle(
                        color = color,
                        radius = 20f / scale,
                        center = Offset(mappedNode.x, mappedNode.y)
                    )

                    // 3. Draw Labels (IP/Hostname)
                    val label = mappedNode.node.hostname ?: mappedNode.node.ipAddress
                    val textLayoutResult = textMeasurer.measure(label, labelStyle)
                    
                    drawText(
                        textLayoutResult = textLayoutResult,
                        topLeft = Offset(
                            x = mappedNode.x - (textLayoutResult.size.width / 2f),
                            y = mappedNode.y + (25f / scale)
                        )
                    )
                }
            }
        }
    }
}
