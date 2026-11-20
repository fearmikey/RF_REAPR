package com.fearmikey.rf_reapr.ui.mdns

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.domain.repository.UpnpDevice
import com.fearmikey.rf_reapr.domain.service.UpnpFinding
import com.fearmikey.rf_reapr.ui.scanner.SeverityBadge
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpnpAuditorScreen(
    viewModel: UpnpScannerViewModel,
    onBack: () -> Unit
) {
    val result by viewModel.result.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val isPassiveMode by viewModel.isPassiveMode.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("UPnP / NAT-PMP Auditor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (result?.isComplete == true) {
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(viewModel.reportText()))
                            scope.launch { snackbar.showSnackbar("Report copied") }
                        }) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy report") }
                    }
                    IconButton(
                        onClick = { if (isScanning) viewModel.stopScan() else viewModel.startScan() },
                        enabled = !isPassiveMode
                    ) {
                        Icon(
                            if (isScanning) Icons.Default.Stop else Icons.Default.Refresh,
                            contentDescription = if (isScanning) "Stop" else "Run audit"
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isPassiveMode) {
                item {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                        Text(
                            "Passive (stealth) mode is on, so UPnP auditing is disabled.",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            val r = result
            if (r == null) {
                item {
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("What this checks", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "• Finds UPnP devices with SSDP and reads their descriptions (model, firmware server string, services).\n" +
                                    "• Asks the router's UPnP IGD service for its WAN IP and lists every port mapping apps have opened.\n" +
                                    "• Checks whether the gateway answers NAT-PMP.\n\n" +
                                    "Read-only: nothing is added to or removed from the router.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Button(onClick = viewModel::startScan, enabled = !isPassiveMode, modifier = Modifier.fillMaxWidth()) {
                                Text("Run audit")
                            }
                        }
                    }
                }
                return@LazyColumn
            }

            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (r.isComplete) "Audit complete" else r.phase, style = MaterialTheme.typography.titleMedium)
                        if (!r.isComplete) LinearProgressIndicator(Modifier.fillMaxWidth())
                        r.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            SummaryStat("Devices", r.devices.size.toString())
                            SummaryStat("Gateways", r.devices.count { it.isInternetGateway }.toString())
                            SummaryStat("Mappings", r.devices.sumOf { it.portMappings.size }.toString())
                            SummaryStat(
                                "NAT-PMP",
                                when {
                                    !r.isComplete -> "…"
                                    r.natPmp?.resultCode == 0 -> "On"
                                    else -> "Off"
                                }
                            )
                        }
                        r.gatewayIp?.let {
                            Text("Default gateway: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (r.isComplete && r.findings.isNotEmpty()) {
                item { Text("Findings", style = MaterialTheme.typography.titleMedium) }
                items(r.findings) { FindingCard(it) }
            }

            if (r.devices.isNotEmpty()) {
                item { Text("Devices", style = MaterialTheme.typography.titleMedium) }
                items(r.devices, key = { it.location + it.ipAddress }) { device ->
                    UpnpDeviceItem(device, isGateway = device.ipAddress == r.gatewayIp)
                }
            } else if (r.isComplete && r.error == null) {
                item {
                    Text(
                        "No UPnP devices answered. Either UPnP is disabled everywhere on this network (good) or this Wi-Fi blocks multicast between clients.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FindingCard(finding: UpnpFinding) {
    val container = when (finding.severity) {
        RiskLevel.CRITICAL, RiskLevel.HIGH -> MaterialTheme.colorScheme.errorContainer
        RiskLevel.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer
        RiskLevel.LOW -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(colors = CardDefaults.cardColors(containerColor = container)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(finding.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                SeverityBadge(finding.severity)
            }
            Spacer(Modifier.height(4.dp))
            Text(finding.detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun UpnpDeviceItem(device: UpnpDevice, isGateway: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.clickable { expanded = !expanded }) {
            ListItem(
                headlineContent = { Text(device.friendlyName, fontWeight = FontWeight.SemiBold) },
                supportingContent = {
                    Text(
                        buildString {
                            append(device.ipAddress)
                            if (isGateway) append(" · default gateway")
                            if (device.manufacturer != "Unknown") append(" · ${device.manufacturer}")
                            if (device.modelName != "Unknown") append(" ${device.modelName}")
                            device.deviceClass?.let { append("\n$it") }
                        }
                    )
                },
                leadingContent = {
                    Icon(if (device.isInternetGateway) Icons.Default.Router else Icons.Default.Devices, contentDescription = null)
                },
                trailingContent = {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (expanded) "Collapse" else "Expand")
                }
            )
            AnimatedVisibility(expanded) {
                SelectionContainer {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        device.descriptionError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        Detail("Server", device.server)
                        Detail("Model number", device.modelNumber)
                        Detail("Serial", device.serialNumber)
                        Detail("External IP", device.externalIp)
                        Detail("Description", device.location)
                        if (device.portMappings.isNotEmpty()) {
                            Text("Port mappings", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                            device.portMappings.forEach { m ->
                                Text(
                                    "${m.protocol} ${m.externalPort} → ${m.internalClient}:${m.internalPort}" +
                                        (if (m.description.isNotBlank()) "  \"${m.description}\"" else "") +
                                        (if (!m.enabled) "  (disabled)" else ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        } else if (device.mappingsReadable == true) {
                            Text("No port mappings currently open.", style = MaterialTheme.typography.bodySmall)
                        }
                        if (device.services.isNotEmpty()) {
                            Text("Services", style = MaterialTheme.typography.labelLarge)
                            Text(device.services.joinToString { it.shortName }, style = MaterialTheme.typography.bodySmall)
                        }
                        device.presentationUrl?.let { url ->
                            TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Open admin page")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Detail(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row {
        Text("$label: ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
