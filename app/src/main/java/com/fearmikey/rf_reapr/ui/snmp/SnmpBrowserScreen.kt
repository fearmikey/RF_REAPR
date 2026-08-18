package com.fearmikey.rf_reapr.ui.snmp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fearmikey.rf_reapr.domain.model.SnmpInterface
import com.fearmikey.rf_reapr.domain.model.SnmpResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnmpBrowserScreen(
    viewModel: SnmpViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SNMP Browser") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = uiState.target,
                        onValueChange = { viewModel.updateTarget(it) },
                        label = { Text("Target IP/Hostname") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.community,
                        onValueChange = { viewModel.updateCommunity(it) },
                        label = { Text("Community String") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("SNMP Version:", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = uiState.version == 0,
                            onClick = { viewModel.updateVersion(0) },
                            label = { Text("V1") }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = uiState.version == 1,
                            onClick = { viewModel.updateVersion(1) },
                            label = { Text("V2c") }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.queryDevice() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isLoading && uiState.target.isNotBlank()
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Query Device")
                        }
                    }
                }
            }

            if (uiState.error != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
            }

            uiState.result?.let { result ->
                Spacer(modifier = Modifier.height(16.dp))
                SnmpResultContent(result)
            }
        }
    }
}

@Composable
fun SnmpResultContent(result: SnmpResult) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("System Information", style = MaterialTheme.typography.titleMedium)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    InfoRow("Name", result.sysName ?: "N/A")
                    InfoRow("Description", result.sysDescr ?: "N/A")
                    InfoRow("Uptime", result.sysUptime ?: "N/A")
                    InfoRow("Contact", result.sysContact ?: "N/A")
                    InfoRow("Location", result.sysLocation ?: "N/A")
                }
            }
        }
        item {
            Text("Interfaces (${result.interfaces.size})", style = MaterialTheme.typography.titleMedium)
        }
        items(result.interfaces) { iface ->
            InterfaceCard(iface)
        }
    }
}

@Composable
fun InterfaceCard(iface: SnmpInterface) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Router, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(iface.name, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    color = if (iface.operStatus == "up") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        iface.operStatus.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            InfoRow("Type", iface.type)
            InfoRow("MTU", iface.mtu.toString())
            InfoRow("MAC", iface.physAddress)
            InfoRow("In Octets", iface.inOctets.toString())
            InfoRow("Out Octets", iface.outOctets.toString())
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            "$label:",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(100.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
