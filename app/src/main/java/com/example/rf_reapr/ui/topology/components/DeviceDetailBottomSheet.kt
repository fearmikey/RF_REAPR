package com.example.rf_reapr.ui.topology.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rf_reapr.domain.model.NetworkNode
import com.example.rf_reapr.ui.scanner.AuditResultCard
import com.example.rf_reapr.ui.scanner.SeverityBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailBottomSheet(
    node: NetworkNode,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Computer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = node.hostname ?: "Unknown Device",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = "IP: ${node.ipAddress}", style = MaterialTheme.typography.bodyMedium)
                    node.macAddress?.let {
                        Text(text = "MAC: $it", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                SeverityBadge(severity = node.riskLevel)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Services & Vulnerabilities",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            if (node.openPorts.isEmpty()) {
                Text(
                    text = "No open ports detected on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(node.openPorts) { port ->
                        AuditResultCard(
                            port = port.port,
                            service = port.serviceName,
                            banner = port.banner,
                            vulnerabilities = port.vulnerabilities
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
