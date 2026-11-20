package com.fearmikey.rf_reapr.ui.topology.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fearmikey.rf_reapr.domain.model.NetworkNode
import com.fearmikey.rf_reapr.ui.scanner.SeverityBadge
import com.fearmikey.rf_reapr.ui.topology.MyDeviceColor
import com.fearmikey.rf_reapr.ui.topology.SnmpColor
import com.fearmikey.rf_reapr.ui.topology.displayName
import com.fearmikey.rf_reapr.ui.topology.icon
import com.fearmikey.rf_reapr.ui.topology.vulnerabilityCount

@Immutable
data class NetworkNodeListState(
    val nodes: List<NetworkNode>
)

@Composable
fun NetworkNodeList(
    state: NetworkNodeListState,
    onNodeClick: (NetworkNode) -> Unit,
    modifier: Modifier = Modifier,
    localDeviceIp: String? = null,
    selectedNodeId: String? = null,
    activeScanNodeIds: Set<String> = emptySet()
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        // Extra bottom padding keeps the last card clear of the FAB / progress overlays.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 160.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = state.nodes,
            key = { it.id },
            contentType = { "network_node" }
        ) { node ->
            NodeListItem(
                node = node,
                isLocalDevice = localDeviceIp != null && node.ipAddress == localDeviceIp,
                isSelected = node.id == selectedNodeId,
                isScanning = node.id in activeScanNodeIds,
                onClick = { onNodeClick(node) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NodeListItem(
    node: NetworkNode,
    onClick: () -> Unit,
    isLocalDevice: Boolean = false,
    isSelected: Boolean = false,
    isScanning: Boolean = false
) {
    val border = when {
        isSelected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        isLocalDevice -> BorderStroke(2.dp, MyDeviceColor)
        else -> null
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = border,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isScanning) {
                    CircularProgressIndicator(modifier = Modifier.fillMaxSize(), strokeWidth = 2.dp)
                }
                Icon(
                    imageVector = node.deviceType.icon(),
                    contentDescription = node.deviceType.displayName(),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.hostname ?: node.snmpData?.sysName ?: "Unknown Device",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = node.ipAddress,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace
                )
                node.macAddress?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                node.manufacturer?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FlowRow(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetaLabel(node.deviceType.displayName(), MaterialTheme.colorScheme.primary)
                    if (isLocalDevice) MetaLabel("MY DEVICE", MyDeviceColor)
                    if (node.openPorts.isNotEmpty()) {
                        MetaLabel("${node.openPorts.size} port${if (node.openPorts.size == 1) "" else "s"}", MaterialTheme.colorScheme.onSurface)
                    }
                    val vulns = node.vulnerabilityCount
                    if (vulns > 0) {
                        MetaLabel("$vulns CVE${if (vulns == 1) "" else "s"}", MaterialTheme.colorScheme.error)
                    }
                    if (node.snmpData != null) MetaLabel("SNMP", SnmpColor)
                }
            }
            SeverityBadge(severity = node.riskLevel)
        }
    }
}

@Composable
private fun MetaLabel(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = color
    )
}
