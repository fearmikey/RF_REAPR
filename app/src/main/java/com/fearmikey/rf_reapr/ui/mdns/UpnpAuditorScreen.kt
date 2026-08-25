package com.fearmikey.rf_reapr.ui.mdns

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fearmikey.rf_reapr.domain.repository.UpnpDevice
import com.fearmikey.rf_reapr.domain.scanner.NetworkScanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpnpAuditorScreen(
    viewModel: UpnpScannerViewModel,
    onBack: () -> Unit
) {
    val scanResult by viewModel.scanResult.collectAsStateWithLifecycle()
    val isPassiveMode by viewModel.isPassiveMode.collectAsStateWithLifecycle()
    val isScanning = scanResult is NetworkScanner.ScanResult.Progress

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("UPnP/NAT-PMP Auditor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { if (isScanning) viewModel.stopScan() else viewModel.startScan() },
                        enabled = !isPassiveMode
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = if (isPassiveMode) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                            )
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
            Text(
                text = "Discovered UPnP Devices",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            when (scanResult) {
                is NetworkScanner.ScanResult.Idle -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (isPassiveMode) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                ) {
                                    Text(
                                        "Passive Mode (Stealth). UPnP auditing inhibited.",
                                        modifier = Modifier.padding(16.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                            Button(
                                onClick = { viewModel.startScan() },
                                enabled = !isPassiveMode
                            ) {
                                Text("Start Discovery Scan")
                            }
                        }
                    }
                }
                is NetworkScanner.ScanResult.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Error: ${(scanResult as NetworkScanner.ScanResult.Error).message}")
                    }
                }
                else -> {
                    val devices = when (scanResult) {
                        is NetworkScanner.ScanResult.Progress -> (scanResult as NetworkScanner.ScanResult.Progress).foundData
                        is NetworkScanner.ScanResult.Finished -> (scanResult as NetworkScanner.ScanResult.Finished).foundData
                        else -> emptyList()
                    }

                    if (devices.isEmpty() && !isScanning) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No UPnP devices found on local network.")
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(devices) { device ->
                                UpnpDeviceItem(device)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UpnpDeviceItem(device: UpnpDevice) {
    Card(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(device.friendlyName) },
            supportingContent = { Text("${device.ipAddress} • ${device.manufacturer}") },
            leadingContent = { Icon(Icons.Default.Router, contentDescription = null) },
            trailingContent = {
                IconButton(onClick = { /* Show Details */ }) {
                    Icon(Icons.Default.Info, contentDescription = "Details")
                }
            }
        )
    }
}
