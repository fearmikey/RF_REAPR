package com.fearmikey.rf_reapr.ui.topology

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fearmikey.rf_reapr.domain.model.NetworkNode
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.domain.scanner.NetworkScanner
import com.fearmikey.rf_reapr.ui.topology.components.DeviceDetailBottomSheet
import com.fearmikey.rf_reapr.ui.topology.components.NetworkNodeList
import com.fearmikey.rf_reapr.ui.topology.components.NetworkNodeListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopologyScreen(
    viewModel: TopologyViewModel,
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPortScanner: (ip: String) -> Unit = {},
    onNavigateToThreatIntel: (ip: String) -> Unit = {}
) {
    val mappedGraph by viewModel.mappedGraph.collectAsStateWithLifecycle()
    val discoveryState by viewModel.discoveryState.collectAsStateWithLifecycle()
    val isPassiveMode by viewModel.isPassiveMode.collectAsStateWithLifecycle()
    val isAuditing by viewModel.isAuditing.collectAsStateWithLifecycle()
    val auditProgress by viewModel.auditProgress.collectAsStateWithLifecycle()
    val deepScanCompleted by viewModel.deepScanCompleted.collectAsStateWithLifecycle()
    val activeNodeScans by viewModel.activeNodeScans.collectAsStateWithLifecycle()
    val nodeResults by viewModel.nodeResults.collectAsStateWithLifecycle()
    val isApiKeySet by viewModel.isApiKeySet.collectAsStateWithLifecycle()
    val showNetworkMismatchDialog by viewModel.showNetworkMismatchDialog.collectAsStateWithLifecycle()
    val localDeviceIp by viewModel.localDeviceIp.collectAsStateWithLifecycle()

    // UI state that should survive rotation / process recreation.
    var isListView by rememberSaveable { mutableStateOf(false) }
    var selectedNodeId by rememberSaveable { mutableStateOf<String?>(null) }
    var showFilterBar by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var riskFilter by rememberSaveable { mutableStateOf(emptySet<RiskLevel>()) }
    var sortOrder by rememberSaveable { mutableStateOf(NodeSortOrder.IP) }

    val allNodes = remember(mappedGraph) { mappedGraph?.nodes?.map { it.node } ?: emptyList() }
    val selectedNode = remember(selectedNodeId, allNodes) { allNodes.find { it.id == selectedNodeId } }

    val isFilterActive = searchQuery.isNotBlank() || riskFilter.isNotEmpty()
    val matchingNodeIds = remember(allNodes, searchQuery, riskFilter) {
        if (searchQuery.isBlank() && riskFilter.isEmpty()) null
        else allNodes.filter { TopologyFilter.matches(it, searchQuery, riskFilter) }.map { it.id }.toSet()
    }
    val listNodes = remember(allNodes, matchingNodeIds, sortOrder) {
        val visible = if (matchingNodeIds == null) allNodes else allNodes.filter { it.id in matchingNodeIds }
        TopologyFilter.sort(visible, sortOrder)
    }
    val riskCounts = remember(allNodes) { allNodes.groupingBy { it.riskLevel }.eachCount() }

    // Nodes with something in flight: per-device scans plus the node currently being audited.
    val scanningNodeIds = remember(activeNodeScans, auditProgress, allNodes) {
        val auditing = auditProgress?.currentIp?.let { ip -> allNodes.find { it.ipAddress == ip }?.id }
        activeNodeScans.keys + listOfNotNull(auditing)
    }
    val isPortScannerBusy = isAuditing || activeNodeScans.values.any { NodeScanKind.PORTS in it }
    val isDiscovering = discoveryState is NetworkScanner.ScanResult.Progress

    var showClearConfirmation by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showDiscoveryWarning by remember { mutableStateOf(false) }
    var showAuditWarning by remember { mutableStateOf(false) }
    var showApiKeyRecommendation by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        if (!isApiKeySet) {
            showApiKeyRecommendation = true
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message, withDismissAction = true)
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
                        selectedNodeId = null
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
                title = {
                    Column {
                        Text("Network Discovery")
                        if (allNodes.isNotEmpty()) {
                            Text(
                                text = if (matchingNodeIds != null) "${matchingNodeIds.size} of ${allNodes.size} devices"
                                else "${allNodes.size} devices",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (mappedGraph != null) {
                        IconButton(onClick = {
                            if (showFilterBar) {
                                // Closing the bar clears filters so nothing stays hidden unexpectedly.
                                searchQuery = ""
                                riskFilter = emptySet()
                            }
                            showFilterBar = !showFilterBar
                        }) {
                            Icon(
                                imageVector = if (showFilterBar) Icons.Default.SearchOff else Icons.Default.Search,
                                contentDescription = if (showFilterBar) "Close search" else "Search and filter devices"
                            )
                        }
                    }
                    // Audit Ports
                    IconButton(
                        onClick = { showAuditWarning = true },
                        enabled = !isPortScannerBusy && !isDiscovering && !isPassiveMode && mappedGraph != null
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
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("About this tool") },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    showInfoDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear all data") },
                                leadingIcon = {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                },
                                enabled = mappedGraph != null,
                                onClick = {
                                    showOverflowMenu = false
                                    showClearConfirmation = true
                                }
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!isPassiveMode && mappedGraph != null && !isDiscovering) {
                FloatingActionButton(onClick = { showDiscoveryWarning = true }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Rescan Network")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            AnimatedVisibility(visible = showFilterBar && mappedGraph != null) {
                TopologyFilterBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    riskFilter = riskFilter,
                    onRiskToggle = { level ->
                        riskFilter = if (level in riskFilter) riskFilter - level else riskFilter + level
                    },
                    riskCounts = riskCounts,
                    showSort = isListView,
                    sortOrder = sortOrder,
                    onSortChange = { sortOrder = it },
                    onClear = {
                        searchQuery = ""
                        riskFilter = emptySet()
                    },
                    isFilterActive = isFilterActive
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                mappedGraph?.let { graph ->
                    if (isListView) {
                        if (listNodes.isEmpty()) {
                            NoMatchesMessage(
                                onClear = {
                                    searchQuery = ""
                                    riskFilter = emptySet()
                                },
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            val nodesState = remember(listNodes) { NetworkNodeListState(listNodes) }
                            val onNodeClickRemembered = remember { { node: NetworkNode -> selectedNodeId = node.id } }
                            NetworkNodeList(
                                state = nodesState,
                                onNodeClick = onNodeClickRemembered,
                                localDeviceIp = localDeviceIp,
                                selectedNodeId = selectedNodeId,
                                activeScanNodeIds = scanningNodeIds
                            )
                        }
                    } else {
                        NetworkMapView(
                            mappedGraph = graph,
                            onNodeClick = { selectedNodeId = it.id },
                            localDeviceIp = localDeviceIp,
                            selectedNodeId = selectedNodeId,
                            matchingNodeIds = matchingNodeIds,
                            activeScanNodeIds = scanningNodeIds
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
                            text = when {
                                isDiscovering -> "Sweeping the local subnet for live devices…"
                                isPassiveMode -> "Passive mode is enabled. Disable it in Settings to run an active network discovery."
                                else -> "No graph data available. Run a network discovery to map your environment."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!isDiscovering) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { showDiscoveryWarning = true },
                                enabled = !isPassiveMode
                            ) {
                                Text("Start Network Discovery")
                            }
                        }
                    }
                }

                // Overlay progress if auditing
                if (isAuditing) {
                    val progress = auditProgress
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 88.dp)
                            .fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Deep Scan: Ports & SNMP", style = MaterialTheme.typography.labelLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                            if (progress != null && progress.total > 0) {
                                LinearProgressIndicator(
                                    progress = { progress.fraction },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "Device ${minOf(progress.completed + 1, progress.total)} of ${progress.total}" +
                                        (progress.currentIp?.let { " · $it" } ?: ""),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            } else {
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
                }

                // Overlay progress if scanning
                val currentDiscovery = discoveryState
                if (currentDiscovery is NetworkScanner.ScanResult.Progress) {
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
                                progress = { currentDiscovery.progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "${currentDiscovery.foundData.size} found",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${(currentDiscovery.progress * 100).toInt()}% complete",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                } else if (currentDiscovery is NetworkScanner.ScanResult.Finished &&
                    !isAuditing && !isPassiveMode && !deepScanCompleted && mappedGraph != null
                ) {
                    Button(
                        onClick = { showAuditWarning = true },
                        enabled = !isPortScannerBusy,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 24.dp, end = 88.dp, bottom = 24.dp)
                            .fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Run Deep Scan (Ports & SNMP)", maxLines = 1)
                    }
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
                            text = "Uses ARP sweeps and active probing to identify all live devices on the local subnet. It identifies manufacturers via MAC addresses and resolves hostnames to build a visual map.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Using the Map",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "• Pinch to zoom, drag to pan, double-tap to zoom in.\n" +
                                "• Use the buttons at the top right to zoom or fit the whole network on screen.\n" +
                                "• Tap a device to open its details, scan it, or reassign its parent.\n" +
                                "• Search matches hostname, IP, MAC, vendor, port number, service, or CVE.",
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
                icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Start Network Discovery?") },
                text = {
                    Text(
                        "Network discovery involves sweeping every IP in the subnet. " +
                        "This generates significant network noise (ARP/TCP) that is easily detected by security systems and can disrupt fragile industrial or medical hardware (OT)." +
                        if (mappedGraph != null) "\n\nThe current map and scan results will be replaced." else ""
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            selectedNodeId = null
                            viewModel.startNetworkDiscovery()
                            showDiscoveryWarning = false
                        }
                    ) {
                        Text("Start Discovery", maxLines = 1)
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
                icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Run Network-Wide Audit?") },
                text = {
                    Text(
                        "This will perform a port scan on EVERY discovered device (${allNodes.size}). " +
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
                        Text("Start Audit", maxLines = 1)
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
                            selectedNodeId = null
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

        selectedNode?.let { node ->
            DeviceDetailBottomSheet(
                node = node,
                allNodes = allNodes,
                onDismiss = { selectedNodeId = null },
                isPassiveMode = isPassiveMode,
                activeScans = activeNodeScans[node.id].orEmpty(),
                isPortScannerBusy = isPortScannerBusy,
                isLocalDevice = localDeviceIp != null && node.ipAddress == localDeviceIp,
                lastResult = nodeResults[node.id],
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
                },
                onCheckExternalExposure = { nodeToScan ->
                    viewModel.checkExternalExposure(nodeToScan)
                },
                onOpenPortScanner = { ip ->
                    selectedNodeId = null
                    onNavigateToPortScanner(ip)
                },
                onOpenThreatIntel = { ip ->
                    selectedNodeId = null
                    onNavigateToThreatIntel(ip)
                }
            )
        }
    }
}

@Composable
private fun TopologyFilterBar(
    query: String,
    onQueryChange: (String) -> Unit,
    riskFilter: Set<RiskLevel>,
    onRiskToggle: (RiskLevel) -> Unit,
    riskCounts: Map<RiskLevel, Int>,
    showSort: Boolean,
    sortOrder: NodeSortOrder,
    onSortChange: (NodeSortOrder) -> Unit,
    onClear: () -> Unit,
    isFilterActive: Boolean
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Host, IP, MAC, vendor, port, CVE…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RiskLevel.entries.reversed().forEach { level ->
                    val count = riskCounts[level] ?: 0
                    FilterChip(
                        selected = level in riskFilter,
                        onClick = { onRiskToggle(level) },
                        label = { Text("${level.displayName()} ($count)") },
                        leadingIcon = {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .background(level.color(), androidx.compose.foundation.shape.CircleShape)
                            )
                        }
                    )
                }
                if (showSort) {
                    var sortMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        AssistChip(
                            onClick = { sortMenuExpanded = true },
                            label = { Text("Sort: ${sortOrder.label}") },
                            leadingIcon = {
                                Icon(
                                    Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = null,
                                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                                )
                            }
                        )
                        DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                            NodeSortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(order.label) },
                                    onClick = {
                                        onSortChange(order)
                                        sortMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                if (isFilterActive) {
                    TextButton(onClick = onClear) { Text("Clear") }
                }
            }
        }
    }
}

@Composable
private fun NoMatchesMessage(onClear: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "No devices match the current filters.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onClear) { Text("Clear filters") }
    }
}
