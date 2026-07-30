package com.fearmikey.rf_reapr.ui.topology.components

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fearmikey.rf_reapr.domain.model.DeviceType
import com.fearmikey.rf_reapr.domain.model.NetworkNode
import com.fearmikey.rf_reapr.ui.scanner.AuditResultCard
import com.fearmikey.rf_reapr.ui.scanner.PortDetailsDialog
import com.fearmikey.rf_reapr.ui.scanner.SeverityBadge
import com.fearmikey.rf_reapr.domain.model.OpenPort

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DeviceDetailBottomSheet(
    node: NetworkNode,
    allNodes: List<NetworkNode> = emptyList(),
    onDismiss: () -> Unit,
    onTypeChange: (DeviceType) -> Unit = {},
    onParentChange: (String?) -> Unit = {},
    onScanPorts: (NetworkNode) -> Unit = {},
    isPassiveMode: Boolean = false
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var selectedPortDetails by remember { mutableStateOf<OpenPort?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    // Auto-expand the sheet when ports are discovered
    LaunchedEffect(node.openPorts.size) {
        if (node.openPorts.isNotEmpty()) {
            // Delay slightly to allow the LazyColumn to be measured and the sheet to update its internal constraints
            kotlinx.coroutines.delay(300)
            if (sheetState.currentValue != SheetValue.Expanded) {
                sheetState.expand()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = node.hostname ?: "Unknown Device",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            clipboardManager.setText(AnnotatedString(node.ipAddress))
                            Toast.makeText(context, "IP copied: ${node.ipAddress}", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text(text = "IP: ${node.ipAddress}", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy IP",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    node.macAddress?.let {
                        Text(text = "MAC: $it", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                SeverityBadge(severity = node.riskLevel)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onScanPorts(node) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    enabled = !isPassiveMode,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Scan Ports", 
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Device Type Manual Override ---
            Text(text = "Device Classification", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DeviceType.values().forEach { type ->
                    FilterChip(
                        selected = node.deviceType == type,
                        onClick = { onTypeChange(type) },
                        label = { Text(type.name, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Parent Selection ---
            if (node.deviceType != DeviceType.GATEWAY) {
                Text(text = "Connected Via (Parent)", style = MaterialTheme.typography.labelLarge)
                val infraNodes = allNodes.filter { 
                    it.id != node.id && (it.deviceType == DeviceType.SWITCH || it.deviceType == DeviceType.ACCESS_POINT || it.deviceType == DeviceType.GATEWAY)
                }
                
                var expanded by remember { mutableStateOf(false) }
                val currentParent = allNodes.find { it.id == node.parentId }

                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    OutlinedCard(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentParent?.let { "${it.hostname ?: it.ipAddress} (${it.deviceType})" } ?: "Auto-Detected (IP Proximity)",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Auto-Detected") },
                            onClick = { onParentChange(null); expanded = false }
                        )
                        infraNodes.forEach { infra ->
                            DropdownMenuItem(
                                text = { Text("${infra.hostname ?: infra.ipAddress} (${infra.deviceType})") },
                                onClick = { onParentChange(infra.id); expanded = false }
                            )
                        }
                    }
                }
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
                    modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(node.openPorts) { port ->
                        AuditResultCard(
                            ipAddress = node.ipAddress,
                            port = port,
                            isPassiveMode = isPassiveMode,
                            onClick = { selectedPortDetails = port }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }

        selectedPortDetails?.let { port ->
            PortDetailsDialog(
                ipAddress = node.ipAddress,
                port = port,
                onDismiss = { selectedPortDetails = null }
            )
        }
    }
}
