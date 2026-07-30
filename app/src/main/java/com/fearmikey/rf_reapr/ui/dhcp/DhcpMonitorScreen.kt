package com.fearmikey.rf_reapr.ui.dhcp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fearmikey.rf_reapr.domain.model.MonitoredDevice
import com.fearmikey.rf_reapr.domain.repository.DhcpMonitorRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DhcpMonitorScreen(
    viewModel: DhcpMonitorViewModel,
    onBack: () -> Unit,
    onDeviceClick: (MonitoredDevice) -> Unit
) {
    val status by viewModel.status.collectAsState()
    val isPassiveMode by viewModel.isPassiveMode.collectAsState()
    val devices by viewModel.devices.collectAsState()

    var showInfoDialog by remember { mutableStateOf(false) }
    var showWarningDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DHCP/Network Monitor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Information")
                    }
                    if (devices.any { it.isNew }) {
                        IconButton(onClick = { viewModel.clearNotifications() }) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = "Clear", tint = Color.Red)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (status is DhcpMonitorRepository.MonitoringStatus.Scanning) "Monitoring Active..." else "Monitor Inactive",
                     )
                Switch(
                    checked = status !is DhcpMonitorRepository.MonitoringStatus.Idle,
                    onCheckedChange = { isChecked ->
                        if (isChecked) {
                            if (!isPassiveMode) {
                                showWarningDialog = true
                            }
                        } else {
                            viewModel.stopMonitoring()
                        }
                    },
                    enabled = !isPassiveMode
                )
            }

            if (isPassiveMode) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            "Passive Mode (Stealth). Network monitoring inhibited.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            if (status is DhcpMonitorRepository.MonitoringStatus.Scanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Monitored Devices", style = MaterialTheme.typography.labelLarge)
            
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(devices) { device ->
                    DeviceMonitorCard(device, onClick = { onDeviceClick(device) })
                }
            }
        }

        if (showInfoDialog) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                title = { Text("Network Monitoring Information") },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            text = "How it Works",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "The monitor periodically scans the local subnet to identify active devices. It compares results against known IPs to detect new hardware joining the network.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Benefits",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "• Rogue Device Detection: Instantly flags unauthorized hardware.\n" +
                                   "• Asset Tracking: Maintains a live list of active network nodes.\n" +
                                   "• Proactive Awareness: Alerts you to network changes as they happen.",
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
                            text = "• IDS Flagging: Frequent sweeps can be flagged as 'horizontal scanning' by security systems.\n" +
                                   "• OT Disruption: Periodic connection attempts can overwhelm legacy PLCs or medical equipment.\n" +
                                   "• Network Load: While minimal, repeated sweeps generate steady ARP and TCP traffic.",
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

        if (showWarningDialog) {
            AlertDialog(
                onDismissRequest = { showWarningDialog = false },
                title = { Text("Enable Active Monitoring?") },
                text = {
                    Text(
                        "Enabling active monitoring will perform periodic network sweeps. " +
                        "This activity is easily detectable by network security (IDS/IPS) and can disrupt fragile Operational Technology (OT) like industrial controllers or medical devices."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.startMonitoring()
                            showWarningDialog = false
                        }
                    ) {
                        Text("I Understand, Enable")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showWarningDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun DeviceMonitorCard(device: MonitoredDevice, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = if (device.isNew) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Radar,
                contentDescription = null,
                tint = if (device.isNew) Color.Red else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = device.hostname ?: "Unknown Device",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (device.isNew) FontWeight.Bold else FontWeight.Normal
                )
                Text(text = "IP: ${device.ipAddress}", style = MaterialTheme.typography.bodySmall)
                if (device.isNew) {
                    Text(
                        text = "NEW DEVICE DETECTED",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Red,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
