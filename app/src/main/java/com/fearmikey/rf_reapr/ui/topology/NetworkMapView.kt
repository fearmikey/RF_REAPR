package com.fearmikey.rf_reapr.ui.topology

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fearmikey.rf_reapr.domain.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

private const val MIN_SCALE = 0.15f
private const val MAX_SCALE = 4f
private const val FIT_MAX_SCALE = 1.2f

// Label level-of-detail thresholds. Labels are drawn at a constant on-screen size, so when
// zoomed far out they would overlap; progressively hide them instead.
private const val PRIMARY_LABEL_MIN_SCALE = 0.4f
private const val DETAIL_LABEL_MIN_SCALE = 0.7f
private const val PORTS_LABEL_MIN_SCALE = 1.1f
private const val MAX_PORTS_IN_LABEL = 4

private class NodeLabels(
    val primary: TextLayoutResult,
    val secondary: TextLayoutResult?,
    val ports: TextLayoutResult?,
    val portCount: TextLayoutResult?
)

/**
 * A custom-drawn view that renders the network topology as a hierarchical diagram.
 *
 * Interactions: pan, pinch-to-zoom (anchored at the pinch point), double-tap to zoom,
 * tap to select, plus on-screen zoom / fit-to-screen controls and a legend.
 *
 * @param selectedNodeId node to highlight along with its links.
 * @param matchingNodeIds when non-null, nodes not in this set are dimmed (search/filter).
 * @param activeScanNodeIds nodes with a scan in progress; drawn with a pulsing ring.
 */
@Composable
fun NetworkMapView(
    mappedGraph: MappedGraph,
    onNodeClick: (NetworkNode) -> Unit,
    modifier: Modifier = Modifier,
    localDeviceIp: String? = null,
    selectedNodeId: String? = null,
    matchingNodeIds: Set<String>? = null,
    activeScanNodeIds: Set<String> = emptySet()
) {
    var scale by remember { mutableFloatStateOf(0.8f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var viewportJob by remember { mutableStateOf<Job?>(null) }
    var lastFittedNodeIds by remember { mutableStateOf<Set<String>?>(null) }
    var legendExpanded by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val currentOnNodeClick by rememberUpdatedState(onNodeClick)

    val colors = MaterialTheme.colorScheme
    val onSurfaceColor = colors.onSurface
    val surfaceColor = colors.surface
    val nodeFillColor = colors.surfaceContainerHigh
    val primaryColor = colors.primary
    val edgeColor = onSurfaceColor.copy(alpha = 0.25f)

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = remember(onSurfaceColor) {
        TextStyle(color = onSurfaceColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
    val secondaryLabelStyle = remember(onSurfaceColor) {
        TextStyle(color = onSurfaceColor.copy(alpha = 0.7f), fontSize = 10.sp)
    }
    val badgeStyle = remember {
        TextStyle(color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
    val myDeviceLabelStyle = remember {
        TextStyle(color = MyDeviceColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }

    val painters = DeviceType.entries.associateWith { rememberVectorPainter(it.icon()) }

    // --- Graph-derived caches (recomputed only when the graph changes) ---
    val nodesById = remember(mappedGraph) { mappedGraph.nodes.associateBy { it.node.id } }
    val nodeIds = remember(mappedGraph) { nodesById.keys }

    // Parent -> children, and the Y of the horizontal "bus" line halfway between a parent
    // and its highest child, giving an org-chart style elbow layout instead of diagonals.
    val childrenByParent = remember(mappedGraph) {
        mappedGraph.edges.groupBy({ it.fromNodeId }, { it.toNodeId })
    }
    val busY = remember(mappedGraph, nodesById) {
        childrenByParent.mapNotNull { (parentId, childIds) ->
            val parent = nodesById[parentId] ?: return@mapNotNull null
            val minChildY = childIds.mapNotNull { nodesById[it]?.y }.minOrNull() ?: return@mapNotNull null
            parentId to (parent.y + (minChildY - parent.y) / 2f)
        }.toMap()
    }
    val parentOf = remember(mappedGraph) {
        mappedGraph.edges.associate { it.toNodeId to it.fromNodeId }
    }

    val maxLabelWidthPx = with(density) { 140.dp.roundToPx() }
    val nodeLabels = remember(mappedGraph, labelStyle, secondaryLabelStyle, badgeStyle) {
        mappedGraph.nodes.associate { mappedNode ->
            val node = mappedNode.node
            val labelConstraints = Constraints(maxWidth = maxLabelWidthPx)
            val primary = textMeasurer.measure(
                text = node.displayName,
                style = labelStyle,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                constraints = labelConstraints
            )
            val secondary = if (node.displayName != node.ipAddress) {
                textMeasurer.measure(node.ipAddress, secondaryLabelStyle, maxLines = 1, constraints = labelConstraints)
            } else null
            val ports = if (node.openPorts.isNotEmpty()) {
                val sorted = node.openPorts.map { it.port }.sorted()
                val shown = sorted.take(MAX_PORTS_IN_LABEL).joinToString(", ")
                val extra = sorted.size - MAX_PORTS_IN_LABEL
                textMeasurer.measure(
                    text = if (extra > 0) "Ports: $shown +$extra" else "Ports: $shown",
                    style = secondaryLabelStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    constraints = labelConstraints
                )
            } else null
            val portCount = if (node.openPorts.isNotEmpty()) {
                val count = node.openPorts.size
                textMeasurer.measure(if (count > 99) "99+" else count.toString(), badgeStyle)
            } else null
            node.id to NodeLabels(primary, secondary, ports, portCount)
        }
    }
    val myDeviceLayout = remember(myDeviceLabelStyle) {
        textMeasurer.measure("MY DEVICE", myDeviceLabelStyle)
    }

    // World-space bounding box of the graph, used for fit-to-screen.
    val worldBounds = remember(mappedGraph) {
        if (mappedGraph.nodes.isEmpty()) null else floatArrayOf(
            mappedGraph.nodes.minOf { it.x },
            mappedGraph.nodes.minOf { it.y },
            mappedGraph.nodes.maxOf { it.x },
            mappedGraph.nodes.maxOf { it.y }
        )
    }

    // --- Viewport helpers ---
    fun fitViewport(): Pair<Float, Offset>? {
        val bounds = worldBounds ?: return null
        if (canvasSize == IntSize.Zero) return null
        val padding = with(density) { 56.dp.toPx() }
        val labelReserve = with(density) { 48.dp.toPx() }
        val worldW = (bounds[2] - bounds[0]).coerceAtLeast(1f)
        val worldH = (bounds[3] - bounds[1]).coerceAtLeast(1f)
        val availW = (canvasSize.width - padding * 2).coerceAtLeast(1f)
        val availH = (canvasSize.height - padding * 2 - labelReserve).coerceAtLeast(1f)
        val fitScale = min(availW / worldW, availH / worldH).coerceIn(MIN_SCALE, FIT_MAX_SCALE)
        val centerX = (bounds[0] + bounds[2]) / 2f
        val centerY = (bounds[1] + bounds[3]) / 2f
        // Shift up slightly so the labels under the bottom row stay on screen.
        return fitScale to Offset(-centerX * fitScale, -centerY * fitScale - labelReserve / 2f)
    }

    fun zoomedViewport(focus: Offset, factor: Float): Pair<Float, Offset> {
        val newScale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        val effective = newScale / scale
        val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
        // Keep the world point under `focus` fixed on screen while scaling.
        val newOffset = (focus - center) - (focus - center - offset) * effective
        return newScale to newOffset
    }

    fun animateViewport(target: Pair<Float, Offset>) {
        viewportJob?.cancel()
        val startScale = scale
        val startOffset = offset
        viewportJob = scope.launch {
            animate(0f, 1f, animationSpec = tween(320, easing = FastOutSlowInEasing)) { t, _ ->
                scale = startScale + (target.first - startScale) * t
                offset = lerp(startOffset, target.second, t)
            }
        }
    }

    // Fit the whole graph on first display and whenever the set of nodes changes (e.g. a new
    // discovery), but not on in-place updates like port-scan results, so the user's current
    // pan/zoom is preserved while they work.
    LaunchedEffect(nodeIds, canvasSize) {
        if (canvasSize != IntSize.Zero && lastFittedNodeIds != nodeIds) {
            fitViewport()?.let { (s, o) ->
                scale = s
                offset = o
            }
            lastFittedNodeIds = nodeIds
        }
    }

    // Pulse animation for nodes with a scan in progress. Only runs while something is scanning.
    val scanPulse = if (activeScanNodeIds.isNotEmpty()) {
        val transition = rememberInfiniteTransition(label = "scanPulse")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
            label = "scanPulseValue"
        ).value
    } else 0f

    val riskCounts = remember(mappedGraph) {
        mappedGraph.nodes.groupingBy { it.node.riskLevel }.eachCount()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .semantics { contentDescription = "Network topology map with ${mappedGraph.nodes.size} devices" }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    viewportJob?.cancel()
                    offset += pan
                    if (zoom != 1f) {
                        val (s, o) = zoomedViewport(centroid, zoom)
                        scale = s
                        offset = o
                    }
                }
            }
            .pointerInput(mappedGraph) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        val target = if (scale >= MAX_SCALE * 0.9f) fitViewport() else zoomedViewport(tap, 2f)
                        target?.let { animateViewport(it) }
                    },
                    onTap = { tap ->
                        val centerX = size.width / 2f + offset.x
                        val centerY = size.height / 2f + offset.y
                        val touchRadius = 32.dp.toPx()
                        val hit = mappedGraph.nodes
                            .map { m ->
                                val dx = centerX + m.x * scale - tap.x
                                val dy = centerY + m.y * scale - tap.y
                                m to (dx * dx + dy * dy)
                            }
                            .filter { it.second <= touchRadius * touchRadius }
                            .minByOrNull { it.second }
                            ?.first
                        hit?.let { currentOnNodeClick(it.node) }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val originX = size.width / 2f + offset.x
            val originY = size.height / 2f + offset.y
            fun sx(x: Float) = originX + x * scale
            fun sy(y: Float) = originY + y * scale

            fun isDimmed(id: String) = matchingNodeIds != null && id !in matchingNodeIds

            val thinStroke = 1.5.dp.toPx()
            val boldStroke = 3.dp.toPx()

            // 1. Edges: parent stem -> horizontal bus -> child drop.
            childrenByParent.forEach { (parentId, childIds) ->
                val parent = nodesById[parentId] ?: return@forEach
                val bus = busY[parentId] ?: return@forEach
                val px = sx(parent.x)
                val busScreenY = sy(bus)
                val parentDimmed = isDimmed(parentId)

                drawLine(
                    color = if (parentDimmed) edgeColor.copy(alpha = 0.08f) else edgeColor,
                    start = Offset(px, sy(parent.y)),
                    end = Offset(px, busScreenY),
                    strokeWidth = thinStroke,
                    cap = StrokeCap.Round
                )
                childIds.forEach { childId ->
                    val child = nodesById[childId] ?: return@forEach
                    val cx = sx(child.x)
                    val color = if (parentDimmed || isDimmed(childId)) edgeColor.copy(alpha = 0.08f) else edgeColor
                    drawLine(color, Offset(px, busScreenY), Offset(cx, busScreenY), thinStroke, StrokeCap.Round)
                    drawLine(color, Offset(cx, busScreenY), Offset(cx, sy(child.y)), thinStroke, StrokeCap.Round)
                }
            }

            // 1b. Highlight the selected node's links (to its parent and its children).
            if (selectedNodeId != null) {
                fun drawHighlightedLink(parentId: String, childId: String) {
                    val parent = nodesById[parentId] ?: return
                    val child = nodesById[childId] ?: return
                    val bus = busY[parentId] ?: return
                    val busScreenY = sy(bus)
                    val px = sx(parent.x)
                    val cx = sx(child.x)
                    drawLine(primaryColor, Offset(px, sy(parent.y)), Offset(px, busScreenY), boldStroke, StrokeCap.Round)
                    drawLine(primaryColor, Offset(px, busScreenY), Offset(cx, busScreenY), boldStroke, StrokeCap.Round)
                    drawLine(primaryColor, Offset(cx, busScreenY), Offset(cx, sy(child.y)), boldStroke, StrokeCap.Round)
                }
                parentOf[selectedNodeId]?.let { drawHighlightedLink(it, selectedNodeId) }
                childrenByParent[selectedNodeId]?.forEach { drawHighlightedLink(selectedNodeId, it) }
            }

            // 2. Nodes and labels. Draw the selected node last so it sits on top.
            val cullMargin = 160.dp.toPx()
            val ordered = if (selectedNodeId == null) mappedGraph.nodes
            else mappedGraph.nodes.sortedBy { it.node.id == selectedNodeId }

            ordered.forEach { mappedNode ->
                val node = mappedNode.node
                val center = Offset(sx(mappedNode.x), sy(mappedNode.y))
                if (center.x < -cullMargin || center.x > size.width + cullMargin ||
                    center.y < -cullMargin || center.y > size.height + cullMargin
                ) return@forEach

                val isSelected = node.id == selectedNodeId
                val isLocal = localDeviceIp != null && node.ipAddress == localDeviceIp
                val alpha = if (isDimmed(node.id) && !isSelected) 0.25f else 1f
                val riskColor = node.riskLevel.color()
                val isInfra = node.deviceType.isInfrastructure
                val radius = (if (isInfra) 26.dp else 20.dp).toPx()

                if (isSelected) {
                    val haloRadius = radius + 12.dp.toPx()
                    drawCircle(primaryColor.copy(alpha = 0.18f), haloRadius, center)
                    drawCircle(primaryColor, haloRadius, center, style = Stroke(2.dp.toPx()))
                }
                if (node.id in activeScanNodeIds) {
                    val pulseRadius = radius + (6.dp.toPx() + 18.dp.toPx() * scanPulse)
                    drawCircle(
                        color = primaryColor.copy(alpha = (1f - scanPulse) * 0.8f),
                        radius = pulseRadius,
                        center = center,
                        style = Stroke(2.dp.toPx())
                    )
                }
                if (isLocal) {
                    drawCircle(MyDeviceColor.copy(alpha = alpha), radius + 6.dp.toPx(), center, style = Stroke(3.dp.toPx()))
                }

                // Body: rounded square for infrastructure, circle for endpoints.
                if (isInfra) {
                    val corner = CornerRadius(8.dp.toPx())
                    val topLeft = Offset(center.x - radius, center.y - radius)
                    val bodySize = Size(radius * 2, radius * 2)
                    drawRoundRect(nodeFillColor, topLeft, bodySize, corner, alpha = alpha)
                    drawRoundRect(riskColor, topLeft, bodySize, corner, style = Stroke(boldStroke), alpha = alpha)
                } else {
                    drawCircle(nodeFillColor, radius, center, alpha = alpha)
                    drawCircle(riskColor, radius, center, style = Stroke(boldStroke), alpha = alpha)
                }

                // Device-type icon.
                val iconSize = radius * 1.1f
                painters[node.deviceType]?.let { painter ->
                    translate(center.x - iconSize / 2f, center.y - iconSize / 2f) {
                        with(painter) {
                            draw(Size(iconSize, iconSize), alpha = alpha, colorFilter = ColorFilter.tint(onSurfaceColor))
                        }
                    }
                }

                val labels = nodeLabels[node.id]

                // Open-port count badge (top-right) and SNMP dot (top-left).
                labels?.portCount?.let { countLayout ->
                    val badgeCenter = Offset(center.x + radius * 0.8f, center.y - radius * 0.8f)
                    val badgeRadius = max(9.dp.toPx(), countLayout.size.width / 2f + 4.dp.toPx())
                    drawCircle(riskColor, badgeRadius, badgeCenter, alpha = alpha)
                    drawCircle(surfaceColor, badgeRadius, badgeCenter, style = Stroke(1.5.dp.toPx()), alpha = alpha)
                    drawText(
                        countLayout,
                        topLeft = Offset(badgeCenter.x - countLayout.size.width / 2f, badgeCenter.y - countLayout.size.height / 2f),
                        alpha = alpha
                    )
                }
                if (node.snmpData != null) {
                    val dotCenter = Offset(center.x - radius * 0.8f, center.y - radius * 0.8f)
                    drawCircle(SnmpColor, 5.dp.toPx(), dotCenter, alpha = alpha)
                    drawCircle(surfaceColor, 5.dp.toPx(), dotCenter, style = Stroke(1.5.dp.toPx()), alpha = alpha)
                }

                // Labels, with level-of-detail based on zoom.
                if (labels != null) {
                    val showPrimary = isSelected || isLocal || node.deviceType == DeviceType.GATEWAY ||
                        scale >= PRIMARY_LABEL_MIN_SCALE
                    val showDetail = isSelected || scale >= DETAIL_LABEL_MIN_SCALE
                    val showPorts = isSelected || scale >= PORTS_LABEL_MIN_SCALE

                    val lines = buildList {
                        if (showPrimary) add(labels.primary)
                        if (showDetail) labels.secondary?.let(::add)
                        if (showPorts) labels.ports?.let(::add)
                        if (isLocal && showPrimary) add(myDeviceLayout)
                    }
                    if (lines.isNotEmpty()) {
                        drawLabelBlock(
                            lines = lines,
                            anchor = Offset(center.x, center.y + radius + 6.dp.toPx()),
                            background = surfaceColor.copy(alpha = 0.8f),
                            alpha = alpha
                        )
                    }
                }
            }
        }

        // --- Overlays ---
        MapLegend(
            expanded = legendExpanded,
            onToggle = { legendExpanded = !legendExpanded },
            riskCounts = riskCounts,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
            FilledTonalIconButton(onClick = { animateViewport(zoomedViewport(center, 1.5f)) }) {
                Icon(Icons.Default.Add, contentDescription = "Zoom in")
            }
            FilledTonalIconButton(onClick = { animateViewport(zoomedViewport(center, 1f / 1.5f)) }) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom out")
            }
            FilledTonalIconButton(onClick = { fitViewport()?.let { animateViewport(it) } }) {
                Icon(Icons.Default.FitScreen, contentDescription = "Fit map to screen")
            }
        }
    }
}

/** Draws a centered stack of text lines under [anchor] on a rounded translucent background. */
private fun DrawScope.drawLabelBlock(
    lines: List<TextLayoutResult>,
    anchor: Offset,
    background: Color,
    alpha: Float
) {
    val padH = 4.dp.toPx()
    val padV = 2.dp.toPx()
    val blockWidth = lines.maxOf { it.size.width } + padH * 2
    val blockHeight = lines.sumOf { it.size.height } + padV * 2
    drawRoundRect(
        color = background,
        topLeft = Offset(anchor.x - blockWidth / 2f, anchor.y),
        size = Size(blockWidth, blockHeight),
        cornerRadius = CornerRadius(4.dp.toPx()),
        alpha = alpha
    )
    var y = anchor.y + padV
    lines.forEach { layout ->
        drawText(layout, topLeft = Offset(anchor.x - layout.size.width / 2f, y), alpha = alpha)
        y += layout.size.height
    }
}

@Composable
private fun MapLegend(
    expanded: Boolean,
    onToggle: () -> Unit,
    riskCounts: Map<RiskLevel, Int>,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onToggle,
        modifier = modifier.widthIn(max = 220.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Compact risk summary is always visible; details expand on tap.
                RiskLevel.entries.reversed().forEach { level ->
                    val count = riskCounts[level] ?: 0
                    if (count > 0) {
                        LegendDot(level.color())
                        Text(
                            text = "$count",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 3.dp, end = 8.dp)
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Hide legend" else "Show legend",
                    modifier = Modifier.size(18.dp)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    RiskLevel.entries.reversed().forEach { level ->
                        LegendRow(label = "${level.displayName()} risk (${riskCounts[level] ?: 0})") {
                            LegendDot(level.color())
                        }
                    }
                    LegendRow("Infrastructure") {
                        Box(
                            Modifier
                                .size(10.dp)
                                .border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(2.dp))
                        )
                    }
                    LegendRow("Endpoint") {
                        Box(Modifier.size(10.dp).border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape))
                    }
                    LegendRow("SNMP responding") { LegendDot(SnmpColor) }
                    LegendRow("My device") {
                        Box(Modifier.size(10.dp).border(2.dp, MyDeviceColor, CircleShape))
                    }
                    Text(
                        text = "Badge = open ports · Double-tap to zoom",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendRow(label: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp), contentAlignment = Alignment.Center) { swatch() }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(
        Modifier
            .size(10.dp)
            .background(color, CircleShape)
    )
}
