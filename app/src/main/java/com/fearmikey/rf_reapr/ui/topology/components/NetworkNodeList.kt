package com.fearmikey.rf_reapr.ui.topology.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fearmikey.rf_reapr.domain.model.NetworkNode
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.ui.scanner.SeverityBadge

@Immutable
data class NetworkNodeListState(
    val nodes: List<NetworkNode>
)

@Composable
fun NetworkNodeList(
    state: NetworkNodeListState,
    onNodeClick: (NetworkNode) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = state.nodes,
            key = { it.id },
            contentType = { "network_node" }
        ) { node ->
            NodeListItem(node = node, onClick = { onNodeClick(node) })
        }
    }
}

@Composable
fun NodeListItem(
    node: NetworkNode,
    onClick: () -> Unit
) {
    NodeListItemContent(
        hostname = node.hostname,
        ipAddress = node.ipAddress,
        macAddress = node.macAddress,
        manufacturer = node.manufacturer,
        riskLevel = node.riskLevel,
        hasSnmp = node.snmpData != null,
        onClick = onClick
    )
}

@Composable
private fun NodeListItemContent(
    hostname: String?,
    ipAddress: String,
    macAddress: String?,
    manufacturer: String?,
    riskLevel: RiskLevel,
    hasSnmp: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Computer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = hostname ?: "Unknown Device",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "IP: $ipAddress",
                    style = MaterialTheme.typography.bodySmall
                )
                macAddress?.let {
                    Text(
                        text = "MAC: $it",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                manufacturer?.let {
                    Text(
                        text = "Manufacturer: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                if (hasSnmp) {
                    Text(
                        text = "SNMP Service Detected",
                        style = MaterialTheme.typography.labelSmall,
                        color = androidx.compose.ui.graphics.Color(0xFF00ACC1),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            SeverityBadge(severity = riskLevel)
        }
    }
}
