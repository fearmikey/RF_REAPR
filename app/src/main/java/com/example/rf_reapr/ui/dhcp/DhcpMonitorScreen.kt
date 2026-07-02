package com.example.rf_reapr.ui.dhcp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rf_reapr.domain.model.MonitoredDevice
import com.example.rf_reapr.domain.repository.DhcpMonitorRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DhcpMonitorScreen(
    viewModel: DhcpMonitorViewModel,
    onBack: () -> Unit
) {
    val status by viewModel.status.collectAsState()
    val devices by viewModel.devices.collectAsState()

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
                    style = MaterialTheme.typography.titleMedium
                )
                Switch(
                    checked = status !is DhcpMonitorRepository.MonitoringStatus.Idle,
                    onCheckedChange = { if (it) viewModel.startMonitoring() else viewModel.stopMonitoring() }
                )
            }

            if (status is DhcpMonitorRepository.MonitoringStatus.Scanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Monitored Devices", style = MaterialTheme.typography.labelLarge)
            
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(devices) { device ->
                    DeviceMonitorCard(device)
                }
            }
        }
    }
}

@Composable
fun DeviceMonitorCard(device: MonitoredDevice) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
