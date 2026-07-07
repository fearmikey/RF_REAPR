package com.example.rf_reapr.ui.topology

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.example.rf_reapr.domain.model.*

/**
 * A custom-drawn view that renders the network topology using a clean hierarchical diagram.
 * Supports Panning, Pinch-to-Zoom, and Tap-to-Select interactions.
 */
@Composable
fun NetworkMapView(
    mappedGraph: MappedGraph,
    onNodeClick: (NetworkNode) -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(0.8f) } // Slightly zoomed out by default
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    val textMeasurer = rememberTextMeasurer()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    
    val labelStyle = remember(onSurfaceColor) {
        TextStyle(color = onSurfaceColor, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    }
    val secondaryLabelStyle = remember(onSurfaceColor) {
        TextStyle(color = onSurfaceColor.copy(alpha = 0.7f), fontSize = 11.sp)
    }
    
    val edgeColor = remember(onSurfaceColor) { onSurfaceColor.copy(alpha = 0.2f) }

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
                    val canvasWidth = size.width.toFloat()
                    val canvasHeight = size.height.toFloat()
                    val centerX = canvasWidth / 2f
                    val centerY = canvasHeight / 2f
                    
                    val clickX = (tapOffset.x - centerX - offset.x) / scale
                    val clickY = (tapOffset.y - centerY - offset.y) / scale
                    
                    val clickedNode = mappedGraph.nodes.find { mappedNode ->
                        val dx = mappedNode.x - clickX
                        val dy = mappedNode.y - clickY
                        val distanceSq = dx * dx + dy * dy
                        distanceSq < (40f * 40f) 
                    }
                    
                    clickedNode?.let { onNodeClick(it.node) }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)

            withTransform(
                {
                    translate(center.x + offset.x, center.y + offset.y)
                    scale(scale, scale, Offset.Zero)
                }
            ) {
                // 1. Draw Edges
                mappedGraph.edges.forEach { edge ->
                    val from = mappedGraph.nodes.find { it.node.id == edge.fromNodeId }
                    val to = mappedGraph.nodes.find { it.node.id == edge.toNodeId }
                    if (from != null && to != null) {
                        drawLine(
                            color = edgeColor,
                            start = Offset(from.x, from.y),
                            end = Offset(to.x, to.y),
                            strokeWidth = 3f / scale
                        )
                    }
                }

                // 2. Draw Nodes
                mappedGraph.nodes.forEach { mappedNode ->
                    val nodeColor = when (mappedNode.node.riskLevel) {
                        RiskLevel.LOW -> Color.Green
                        RiskLevel.MEDIUM -> Color.Yellow
                        RiskLevel.HIGH -> Color(0xFFFFA500)
                        RiskLevel.CRITICAL -> Color.Red
                    }

                    when (mappedNode.node.deviceType) {
                        DeviceType.GATEWAY -> {
                            val size = 50f / scale
                            drawRect(
                                color = nodeColor,
                                topLeft = Offset(mappedNode.x - size/2, mappedNode.y - size/2),
                                size = androidx.compose.ui.geometry.Size(size, size),
                                style = Stroke(width = 4f / scale)
                            )
                            drawCircle(color = nodeColor.copy(alpha = 0.3f), radius = size/2, center = Offset(mappedNode.x, mappedNode.y))
                        }
                        DeviceType.SWITCH -> {
                            val width = 60f / scale
                            val height = 30f / scale
                            drawRect(
                                color = nodeColor,
                                topLeft = Offset(mappedNode.x - width/2, mappedNode.y - height/2),
                                size = androidx.compose.ui.geometry.Size(width, height)
                            )
                        }
                        DeviceType.ACCESS_POINT -> {
                            val size = 40f / scale
                            drawCircle(color = nodeColor, radius = 8f / scale, center = Offset(mappedNode.x, mappedNode.y + 10f/scale))
                            drawArc(
                                color = nodeColor,
                                startAngle = 210f,
                                sweepAngle = 120f,
                                useCenter = false,
                                topLeft = Offset(mappedNode.x - size/2, mappedNode.y - size/2),
                                size = androidx.compose.ui.geometry.Size(size, size),
                                style = Stroke(width = 3f / scale)
                            )
                            val innerSize = size * 0.6f
                            drawArc(
                                color = nodeColor,
                                startAngle = 210f,
                                sweepAngle = 120f,
                                useCenter = false,
                                topLeft = Offset(mappedNode.x - innerSize/2, mappedNode.y - innerSize/2 + 5f/scale),
                                size = androidx.compose.ui.geometry.Size(innerSize, innerSize),
                                style = Stroke(width = 2f / scale)
                            )
                        }
                        else -> {
                            drawCircle(
                                color = nodeColor,
                                radius = 20f / scale,
                                center = Offset(mappedNode.x, mappedNode.y)
                            )
                        }
                    }

                    // 3. Draw Labels
                    var labelYOffset = 30f / scale
                    
                    val primaryLabel = mappedNode.node.hostname ?: mappedNode.node.ipAddress
                    val primaryTextLayout = textMeasurer.measure(primaryLabel, labelStyle)
                    
                    drawText(
                        textLayoutResult = primaryTextLayout,
                        topLeft = Offset(
                            x = mappedNode.x - (primaryTextLayout.size.width / 2f),
                            y = mappedNode.y + labelYOffset
                        )
                    )
                    labelYOffset += primaryTextLayout.size.height

                    // If hostname is used as primary, show IP as secondary
                    if (mappedNode.node.hostname != null) {
                        val secondaryTextLayout = textMeasurer.measure(mappedNode.node.ipAddress, secondaryLabelStyle)
                        drawText(
                            textLayoutResult = secondaryTextLayout,
                            topLeft = Offset(
                                x = mappedNode.x - (secondaryTextLayout.size.width / 2f),
                                y = mappedNode.y + labelYOffset
                            )
                        )
                        labelYOffset += secondaryTextLayout.size.height
                    }

                    // 4. Ports
                    val openPorts = mappedNode.node.openPorts

                    if (openPorts.isNotEmpty()) {
                        val portsLabel = "Ports: ${openPorts.joinToString(", ") { it.port.toString() }}"
                        val portsTextLayoutResult = textMeasurer.measure(
                            text = portsLabel,
                            style = secondaryLabelStyle
                        )
                        drawText(
                            textLayoutResult = portsTextLayoutResult,
                            topLeft = Offset(
                                x = mappedNode.x - (portsTextLayoutResult.size.width / 2f),
                                y = mappedNode.y + labelYOffset
                            )
                        )
                    }
                }
            }
        }
    }
}
