package com.fearmikey.rf_reapr.ui.topology

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fearmikey.rf_reapr.domain.model.NetworkNode
import com.fearmikey.rf_reapr.domain.scanner.NetworkScanner
import com.fearmikey.rf_reapr.ui.topology.components.DeviceDetailBottomSheet
import com.fearmikey.rf_reapr.ui.topology.components.NetworkNodeList
import com.fearmikey.rf_reapr.ui.topology.components.NetworkNodeListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopologyScreen(
    viewModel: TopologyViewModel,
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val mappedGraph by viewModel.mappedGraph.collectAsState()
    val discoveryState by viewModel.discoveryState.collectAsState()
    val isPassiveMode by viewModel.isPassiveMode.collectAsState()
    val isAuditing by viewModel.isAuditing.collectAsState()
    val isApiKeySet by viewModel.isApiKeySet.collectAsState()
    val showNetworkMismatchDialog by viewModel.showNetworkMismatchDialog.collectAsState()
    val autoPortScan by viewModel.autoPortScan.collectAsState()
    val autoSnmpDiscovery by viewModel.autoSnmpDiscovery.collectAsState()
    
    var isListView by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var selectedNodeId by remember { mutableStateOf<String?>(null) }
    
    val selectedNode = remember(selectedNodeId, mappedGraph) {
        mappedGraph?.nodes?.find { it.node.id == selectedNodeId }?.node
    }

    var showClearConfirmation by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showDiscoveryWarning by remember { mutableStateOf(false) }
    var showAuditWarning by remember { mutableStateOf(false) }
    var showApiKeyRecommendation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!isApiKeySet) {
            showApiKeyRecommendation = true
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Clear All Discovery Data?") },
            text = { Text("This will permanently delete all discovered devices and scan results. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearData()
                        showClearConfirmation = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Network Discovery") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Scan Options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = autoPortScan, onCheckedChange = { viewModel.toggleAutoPortScan() })
                                        Text("Common Port Scan")
                                    }
                                },
                                onClick = { viewModel.toggleAutoPortScan() }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = autoSnmpDiscovery, onCheckedChange = { viewModel.toggleAutoSnmpDiscovery() })
                                        Text("Discover SNMP Services")
                                    }
                                },
                                onClick = { viewModel.toggleAutoSnmpDiscovery() }
                            )
                        }
                    }
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Information")
                    }
                    // Clear Data
                    IconButton(onClick = { showClearConfirmation = true }) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear All Data",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // Audit Ports
                    IconButton(
                        onClick = { showAuditWarning = true },
                        enabled = !isAuditing && discoveryState !is NetworkScanner.ScanResult.Progress && !isPassiveMode
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "Audit High-Impact Ports",
                            tint = if (isAuditing) MaterialTheme.colorScheme.error else if (isPassiveMode) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { isListView = !isListView }) {
                        Icon(
                            imageVector = if (isListView) Icons.Default.Map else Icons.AutoMirrored.Filled.List,
                            contentDescription = if (isListView) "Show Map" else "Show List"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isPassiveMode) {
                FloatingActionButton(onClick = { showDiscoveryWarning = true }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Rescan Network")
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            mappedGraph?.let { graph ->
                if (isListView) {
                    val nodesState = remember(graph) { 
                        NetworkNodeListState(graph.nodes.map { it.node }) 
                    }
                    val onNodeClickRemembered = remember { { node: NetworkNode -> selectedNodeId = node.id } }
                    NetworkNodeList(
                        state = nodesState,
                        onNodeClick = onNodeClickRemembered
                    )
                } else {
                    NetworkMapView(
                        mappedGraph = graph,
                        onNodeClick = { selectedNodeId = it.id }
                    )
                }
            } ?: run {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No graph data available. Run a network discovery to map your environment.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { showDiscoveryWarning = true }) {
                        Text("Start Network Discovery")
                    }
                }
            }

            // INFO DIALOG
            if (showInfoDialog) {
                AlertDialog(
                    onDismissRequest = { showInfoDialog = false },
                    title = { Text("Network Discovery Information") },
                    text = {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Text(
                                text = "Network Discovery",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Uses ARP sweeps and active probing to identify all live devices on the local subnet. If enabled, it automatically performs port scans and SNMP discovery to enrich device data.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "High-Impact Audit",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Scans all discovered devices for high-risk ports (e.g., 22, 23, 80, 443, 3389, 445). This helps identify critical vulnerabilities across the entire network at once.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Risks & Dangers",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "• Detection: Rapid 'horizontal' discovery is a major trigger for IDS/IPS alerts.\n" +
                                       "• ARP Storms: Discovery can generate significant broadcast traffic, potentially lagging slow networks.\n" +
                                       "• OT Impact: Active probing of every IP can cause legacy PLCs or fragile medical devices to malfunction.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showInfoDialog = false }) {
                            Text("Close")
                        }
                    }
                )
            }

            // DISCOVERY WARNING
            if (showDiscoveryWarning) {
                AlertDialog(
                    onDismissRequest = { showDiscoveryWarning = false },
                    title = { Text("Start Network Discovery?") },
                    text = {
                        Text(
                            "Network discovery involves sweeping every IP in the subnet. " +
                            "This generates significant network noise (ARP/TCP) that is easily detected by security systems and can disrupt fragile industrial or medical hardware (OT)."
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.startNetworkDiscovery()
                                showDiscoveryWarning = false
                            }
                        ) {
                            Text("I Understand, Start Discovery")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDiscoveryWarning = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // AUDIT WARNING
            if (showAuditWarning) {
                AlertDialog(
                    onDismissRequest = { showAuditWarning = false },
                    title = { Text("Run Network-Wide Audit?") },
                    text = {
                        Text(
                            "This will perform a port scan on EVERY discovered device. " +
                            "This is HIGHLY aggressive activity that will likely trigger network alarms and carries a significant risk of crashing legacy or fragile Operational Technology (OT)."
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.auditHighImpactPorts()
                                showAuditWarning = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("I Understand, Start Audit")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAuditWarning = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // NETWORK MISMATCH DIALOG
            if (showNetworkMismatchDialog) {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissNetworkMismatchDialog() },
                    title = { Text("Network Change Detected") },
                    text = {
                        Text(
                            "The current network gateway does not match the one from your last scan. " +
                            "Auditing the old network map while connected to a new network will yield incorrect results. " +
                            "Would you like to perform a fresh network discovery first?"
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.confirmNetworkMismatchRescan()
                            }
                        ) {
                            Text("Rescan Network")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.dismissNetworkMismatchDialog() }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showApiKeyRecommendation) {
                AlertDialog(
                    onDismissRequest = { showApiKeyRecommendation = false },
                    title = { Text("Boost Your Scan Results") },
                    text = {
                        Text(
                            "Adding a free NIST NVD API key allows RF_REAPR to fetch vulnerability data more reliably and with higher rate limits.\n\n" +
                            "Without a key, the NIST API may throttle requests, leading to missing CVE data during audits."
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showApiKeyRecommendation = false
                                onNavigateToSettings()
                            }
                        ) {
                            Text("Go to Settings")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showApiKeyRecommendation = false }) {
                            Text("Maybe Later")
                        }
                    }
                )
            }

            // Overlay progress if auditing
            if (isAuditing) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 88.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Auditing High-Impact Ports...", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Checking all discovered devices",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            // Overlay progress if scanning
            if (discoveryState is NetworkScanner.ScanResult.Progress) {
                val progress = (discoveryState as NetworkScanner.ScanResult.Progress).progress
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 88.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Discovering Devices...", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "${(progress * 100).toInt()}% complete",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }
        }

        selectedNode?.let { node ->
            DeviceDetailBottomSheet(
                node = node,
                allNodes = mappedGraph?.nodes?.map { it.node } ?: emptyList(),
                onDismiss = { selectedNodeId = null },
                isPassiveMode = isPassiveMode,
                onTypeChange = { newType ->
                    viewModel.updateDeviceType(node, newType)
                },
                onParentChange = { newParentId ->
                    viewModel.updateParent(node, newParentId)
                },
                onScanPorts = { nodeToScan ->
                    viewModel.scanSingleNodeTrigger(nodeToScan)
                },
                onScanSnmp = { nodeToScan ->
                    viewModel.scanSnmpSingleNodeTrigger(nodeToScan)
                }
            )
        }
    }
}
