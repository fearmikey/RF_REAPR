package com.fearmikey.rf_reapr.ui.topology.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsRemote
import com.fearmikey.rf_reapr.domain.service.PortCatalog
import com.fearmikey.rf_reapr.ui.navigation.Screen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fearmikey.rf_reapr.domain.model.DeviceType
import com.fearmikey.rf_reapr.domain.model.NetworkNode
import com.fearmikey.rf_reapr.domain.model.OpenPort
import com.fearmikey.rf_reapr.domain.model.SnmpResult
import com.fearmikey.rf_reapr.ui.scanner.AuditResultCard
import com.fearmikey.rf_reapr.ui.scanner.PortDetailsDialog
import com.fearmikey.rf_reapr.ui.scanner.SeverityBadge
import com.fearmikey.rf_reapr.ui.topology.MyDeviceColor
import com.fearmikey.rf_reapr.ui.topology.NodeScanKind
import com.fearmikey.rf_reapr.ui.topology.SnmpColor
import com.fearmikey.rf_reapr.ui.topology.displayName
import com.fearmikey.rf_reapr.ui.topology.icon
import com.fearmikey.rf_reapr.ui.topology.isInfrastructure
import com.fearmikey.rf_reapr.ui.topology.toSummaryText
import com.fearmikey.rf_reapr.ui.topology.vulnerabilityCount
import kotlin.time.Duration.Companion.milliseconds

private const val SNMP_INTERFACES_PREVIEW = 5

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DeviceDetailBottomSheet(
    node: NetworkNode,
    allNodes: List<NetworkNode> = emptyList(),
    onDismiss: () -> Unit,
    onTypeChange: (DeviceType) -> Unit = {},
    onParentChange: (String?) -> Unit = {},
    onScanPorts: (NetworkNode) -> Unit = {},
    onScanSnmp: (NetworkNode) -> Unit = {},
    onCheckExternalExposure: (NetworkNode) -> Unit = {},
    isPassiveMode: Boolean = false,
    activeScans: Set<NodeScanKind> = emptySet(),
    isPortScannerBusy: Boolean = false,
    isLocalDevice: Boolean = false,
    lastResult: String? = null,
    onOpenPortScanner: ((String) -> Unit)? = null,
    onOpenThreatIntel: ((String) -> Unit)? = null,
    /** Opens another tool's route (Ping, Traceroute, TLS, SNMP) prefilled with this host. */
    onOpenRoute: ((String) -> Unit)? = null,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var selectedPortDetails by remember { mutableStateOf<OpenPort?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    fun copy(label: String, value: String) {
        clipboardManager.setText(AnnotatedString(value))
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }

    // Auto-expand the sheet when ports or SNMP data is discovered
    LaunchedEffect(node.openPorts.size, node.snmpData != null) {
        if (node.openPorts.isNotEmpty() || node.snmpData != null) {
            // Delay slightly to allow the list to be measured and the sheet to update its internal constraints
            kotlinx.coroutines.delay(300.milliseconds)
            if (sheetState.currentValue != SheetValue.Expanded) {
                sheetState.expand()
            }
        }
    }

    val sortedPorts = remember(node.openPorts) {
        node.openPorts.sortedWith(
            compareByDescending<OpenPort> { port -> port.vulnerabilities.maxOfOrNull { it.severity.ordinal } ?: -1 }
                .thenBy { it.port }
        )
    }
    val currentParent = remember(allNodes, node.parentId) { allNodes.find { it.id == node.parentId } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // --- Header ---
            item(key = "header") {
                DeviceHeader(
                    node = node,
                    isLocalDevice = isLocalDevice,
                    onCopy = ::copy
                )
            }

            // --- Summary chips ---
            item(key = "summary") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SummaryChip(Icons.Default.BugReport, "${node.openPorts.size} open port${if (node.openPorts.size == 1) "" else "s"}")
                    val vulnCount = node.vulnerabilityCount
                    if (vulnCount > 0) {
                        SummaryChip(Icons.Default.Security, "$vulnCount CVE${if (vulnCount == 1) "" else "s"}")
                    }
                    if (node.snmpData != null) {
                        SummaryChip(Icons.Default.SettingsRemote, "SNMP")
                    }
                }
            }

            // --- Primary scan actions ---
            item(key = "actions") {
                val portsRunning = NodeScanKind.PORTS in activeScans
                val snmpRunning = NodeScanKind.SNMP in activeScans
                val reconRunning = NodeScanKind.RECON in activeScans
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScanActionButton(
                            label = if (portsRunning) "Scanning…" else "Scan Ports",
                            icon = Icons.Default.BugReport,
                            running = portsRunning,
                            enabled = !isPassiveMode && !portsRunning && !isPortScannerBusy,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            onClick = { onScanPorts(node) },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        ScanActionButton(
                            label = if (snmpRunning) "Querying…" else "Scan SNMP",
                            icon = Icons.Default.SettingsRemote,
                            running = snmpRunning,
                            enabled = !isPassiveMode && !snmpRunning,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            onClick = { onScanSnmp(node) },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        if (node.deviceType == DeviceType.GATEWAY && node.hasPublicIp) {
                            ScanActionButton(
                                label = if (reconRunning) "Checking…" else "Quick Recon",
                                icon = Icons.Default.Language,
                                running = reconRunning,
                                enabled = !reconRunning,
                                containerColor = com.fearmikey.rf_reapr.ui.theme.WebGold.copy(alpha = 0.8f),
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                onClick = { onCheckExternalExposure(node) },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }
                    if (isPortScannerBusy && !portsRunning && !isPassiveMode) {
                        Text(
                            text = "Port scanner busy with another scan",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (lastResult != null && activeScans.isEmpty()) {
                        Text(
                            text = "Last result: $lastResult",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Secondary actions: hand off to the dedicated tools or copy a summary.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (onOpenPortScanner != null) {
                            AssistChip(
                                onClick = { onOpenPortScanner(node.ipAddress) },
                                label = { Text("Full Port Scan") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ManageSearch, null, Modifier.size(AssistChipDefaults.IconSize)) },
                                enabled = !isPassiveMode
                            )
                        }
                        if (onOpenThreatIntel != null && node.hasPublicIp) {
                            AssistChip(
                                onClick = { onOpenThreatIntel(node.ipAddress) },
                                label = { Text("Threat Intel") },
                                leadingIcon = { Icon(Icons.Default.Security, null, Modifier.size(AssistChipDefaults.IconSize)) }
                            )
                        }
                        if (onOpenRoute != null) {
                            val ip = node.ipAddress
                            val tlsPort = node.openPorts.firstOrNull { PortCatalog.isTls(it.port, it.serviceName) }?.port
                            AssistChip(
                                onClick = { onOpenRoute(Screen.PingTool.createRoute(ip)) },
                                label = { Text("Ping") },
                                leadingIcon = { Icon(Icons.Default.NetworkCheck, null, Modifier.size(AssistChipDefaults.IconSize)) }
                            )
                            AssistChip(
                                onClick = { onOpenRoute(Screen.Traceroute.createRoute(ip)) },
                                label = { Text("Traceroute") },
                                leadingIcon = { Icon(Icons.Default.Route, null, Modifier.size(AssistChipDefaults.IconSize)) },
                                enabled = !isPassiveMode
                            )
                            if (tlsPort != null) {
                                AssistChip(
                                    onClick = { onOpenRoute(Screen.TlsCipherScanner.createRoute(if (tlsPort == 443) ip else "$ip:$tlsPort")) },
                                    label = { Text("TLS Audit") },
                                    leadingIcon = { Icon(Icons.Default.Lock, null, Modifier.size(AssistChipDefaults.IconSize)) },
                                    enabled = !isPassiveMode
                                )
                            }
                            AssistChip(
                                onClick = { onOpenRoute(Screen.SnmpBrowser.createRoute(ip)) },
                                label = { Text("SNMP Browser") },
                                leadingIcon = { Icon(Icons.Default.Router, null, Modifier.size(AssistChipDefaults.IconSize)) }
                            )
                        }
                        AssistChip(
                            onClick = { copy("Device summary", node.toSummaryText()) },
                            label = { Text("Copy Summary") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null, Modifier.size(AssistChipDefaults.IconSize)) }
                        )
                    }
                }
            }

            // --- Services & Vulnerabilities ---
            item(key = "services_header") {
                SectionTitle("Services & Vulnerabilities")
            }

            if (node.openPorts.isEmpty() && node.snmpData == null) {
                item(key = "services_empty") {
                    Text(
                        text = when {
                            NodeScanKind.PORTS in activeScans -> "Scanning high-impact ports…"
                            isPassiveMode -> "Passive mode is on. Active port and SNMP scans are disabled."
                            else -> "No open ports or services detected yet. Tap Scan Ports to check common high-impact services."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            node.snmpData?.let { snmp ->
                item(key = "snmp") { SnmpCard(snmp) }
            }

            // No item keys here: scan results aren't guaranteed to have unique port entries.
            items(sortedPorts) { port ->
                AuditResultCard(
                    ipAddress = node.ipAddress,
                    port = port,
                    isPassiveMode = isPassiveMode
                ) { selectedPortDetails = port }
            }

            // --- Topology settings (manual overrides) ---
            item(key = "topology_header") {
                Spacer(Modifier.height(8.dp))
                SectionTitle("Topology")
            }

            item(key = "classification") {
                Column {
                    Text(text = "Device Classification", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DeviceType.entries.forEach { type ->
                            FilterChip(
                                selected = node.deviceType == type,
                                onClick = { onTypeChange(type) },
                                label = { Text(type.displayName(), style = MaterialTheme.typography.labelMedium) },
                                leadingIcon = {
                                    Icon(type.icon(), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
                                }
                            )
                        }
                    }
                }
            }

            if (node.deviceType != DeviceType.GATEWAY) {
                item(key = "parent") {
                    ParentSelector(
                        node = node,
                        allNodes = allNodes,
                        currentParent = currentParent,
                        onParentChange = onParentChange
                    )
                }
            }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceHeader(
    node: NetworkNode,
    isLocalDevice: Boolean,
    onCopy: (label: String, value: String) -> Unit
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = node.deviceType.icon(),
                contentDescription = node.deviceType.displayName(),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = node.hostname ?: node.snmpData?.sysName ?: "Unknown Device",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = node.deviceType.displayName(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (isLocalDevice) {
                    Text(
                        text = "• MY DEVICE",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MyDeviceColor
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            CopyableField(label = "IP", value = node.ipAddress, onCopy = onCopy)
            node.macAddress?.let { CopyableField(label = "MAC", value = it, onCopy = onCopy) }
            node.manufacturer?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        SeverityBadge(severity = node.riskLevel)
    }
}

@Composable
private fun CopyableField(
    label: String,
    value: String,
    onCopy: (label: String, value: String) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(onClickLabel = "Copy $label") { onCopy(label, value) }
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SummaryChip(icon: ImageVector, text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ScanActionButton(
    label: String,
    icon: ImageVector,
    running: Boolean,
    enabled: Boolean,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        if (running) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = LocalContentColor.current
            )
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun SnmpCard(snmp: SnmpResult) {
    var showAllInterfaces by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SettingsRemote, contentDescription = null, modifier = Modifier.size(20.dp), tint = SnmpColor)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "SNMP (community 'public')", style = MaterialTheme.typography.labelLarge, color = SnmpColor)
            }
            Spacer(modifier = Modifier.height(4.dp))
            snmp.sysName?.let { LabeledValue("Name", it, bold = true) }
            snmp.sysDescr?.let { LabeledValue("Description", it) }
            snmp.sysUptime?.let { LabeledValue("Uptime", it) }
            snmp.sysLocation?.let { LabeledValue("Location", it) }
            snmp.sysContact?.let { LabeledValue("Contact", it) }

            if (snmp.interfaces.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Interfaces (${snmp.interfaces.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                val shown = if (showAllInterfaces) snmp.interfaces else snmp.interfaces.take(SNMP_INTERFACES_PREVIEW)
                shown.forEach { iface ->
                    val isUp = iface.operStatus.equals("up", ignoreCase = true) || iface.operStatus == "1"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(
                                    if (isUp) androidx.compose.ui.graphics.Color.Green else MaterialTheme.colorScheme.outline,
                                    CircleShape
                                )
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = buildString {
                                append(iface.name.ifBlank { "if${iface.index}" })
                                if (iface.physAddress.isNotBlank()) append("  ${iface.physAddress}")
                                if (iface.speed > 0) append("  ${formatSpeed(iface.speed)}")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (snmp.interfaces.size > SNMP_INTERFACES_PREVIEW) {
                    TextButton(
                        onClick = { showAllInterfaces = !showAllInterfaces },
                        contentPadding = PaddingValues(horizontal = 0.dp)
                    ) {
                        Text(if (showAllInterfaces) "Show fewer" else "Show all ${snmp.interfaces.size}")
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String, bold: Boolean = false) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (bold) FontWeight.Bold else null
    )
}

private fun formatSpeed(bitsPerSecond: Long): String = when {
    bitsPerSecond >= 1_000_000_000 -> "${bitsPerSecond / 1_000_000_000} Gbps"
    bitsPerSecond >= 1_000_000 -> "${bitsPerSecond / 1_000_000} Mbps"
    bitsPerSecond >= 1_000 -> "${bitsPerSecond / 1_000} Kbps"
    else -> "$bitsPerSecond bps"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParentSelector(
    node: NetworkNode,
    allNodes: List<NetworkNode>,
    currentParent: NetworkNode?,
    onParentChange: (String?) -> Unit
) {
    val infraNodes = remember(allNodes, node.id) {
        allNodes.filter { it.id != node.id && it.deviceType.isInfrastructure }
    }
    var expanded by remember { mutableStateOf(false) }

    fun label(n: NetworkNode) = "${n.hostname ?: n.ipAddress} (${n.deviceType.displayName()})"

    Column {
        Text(text = "Connected Via (Parent)", style = MaterialTheme.typography.labelLarge)
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            OutlinedCard(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = currentParent?.deviceType?.icon() ?: DeviceType.GATEWAY.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = currentParent?.let(::label) ?: "Auto-Detected (IP Proximity)",
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
                        text = { Text(label(infra)) },
                        leadingIcon = { Icon(infra.deviceType.icon(), contentDescription = null) },
                        onClick = { onParentChange(infra.id); expanded = false }
                    )
                }
            }
        }
        if (infraNodes.isEmpty()) {
            Text(
                text = "Classify a device as Switch or Access Point to use it as a parent.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
