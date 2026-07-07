package com.example.rf_reapr.ui.topology

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rf_reapr.domain.model.NetworkNode
import com.example.rf_reapr.domain.scanner.NetworkScanner
import com.example.rf_reapr.ui.topology.components.DeviceDetailBottomSheet
import com.example.rf_reapr.ui.topology.components.NetworkNodeList
import com.example.rf_reapr.ui.topology.components.NetworkNodeListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopologyScreen(
    viewModel: TopologyViewModel,
    onBack: () -> Unit
) {
    val mappedGraph by viewModel.mappedGraph.collectAsState()
    val discoveryState by viewModel.discoveryState.collectAsState()
    val isAuditing by viewModel.isAuditing.collectAsState()
    
    var isListView by remember { mutableStateOf(false) }
    var selectedNode by remember { mutableStateOf<NetworkNode?>(null) }
    var showClearConfirmation by remember { mutableStateOf(false) }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Clear All Topology Data?") },
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
                title = { Text("Network Topology Map") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
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
                        onClick = { viewModel.auditHighImpactPorts() },
                        enabled = !isAuditing && discoveryState !is NetworkScanner.ScanResult.Progress
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "Audit High-Impact Ports",
                            tint = if (isAuditing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
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
            FloatingActionButton(onClick = { viewModel.startNetworkDiscovery() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Rescan Network")
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
                    val onNodeClickRemembered = remember { { node: NetworkNode -> selectedNode = node } }
                    NetworkNodeList(
                        state = nodesState,
                        onNodeClick = onNodeClickRemembered
                    )
                } else {
                    NetworkMapView(
                        mappedGraph = graph,
                        onNodeClick = { selectedNode = it }
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
                    Button(onClick = { viewModel.startNetworkDiscovery() }) {
                        Text("Start Network Discovery")
                    }
                }
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
                onDismiss = { selectedNode = null },
                onTypeChange = { newType ->
                    viewModel.updateDeviceType(node, newType)
                },
                onParentChange = { newParentId ->
                    viewModel.updateParent(node, newParentId)
                }
            )
        }
    }
}
