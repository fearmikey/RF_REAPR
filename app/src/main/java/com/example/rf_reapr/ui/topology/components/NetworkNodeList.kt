package com.example.rf_reapr.ui.topology.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rf_reapr.domain.model.NetworkNode
import com.example.rf_reapr.ui.scanner.SeverityBadge

@Composable
fun NetworkNodeList(
    nodes: List<NetworkNode>,
    onNodeClick: (NetworkNode) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(nodes) { node ->
            NodeListItem(node = node, onClick = { onNodeClick(node) })
        }
    }
}

@Composable
fun NodeListItem(
    node: NetworkNode,
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
                    text = node.hostname ?: "Unknown Device",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "IP: ${node.ipAddress}",
                    style = MaterialTheme.typography.bodySmall
                )
                node.macAddress?.let {
                    Text(
                        text = "MAC: $it",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            SeverityBadge(severity = node.riskLevel)
        }
    }
}
