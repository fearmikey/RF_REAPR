package com.example.rf_reapr.ui.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rf_reapr.domain.model.OpenPort
import com.example.rf_reapr.domain.model.RiskLevel
import com.example.rf_reapr.domain.model.Vulnerability
import com.example.rf_reapr.domain.scanner.NetworkScanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortScannerScreen(
    viewModel: PortScannerViewModel,
    onBack: () -> Unit,
    onUpdateTopology: (Map<String, List<OpenPort>>, String?) -> Unit
) {
    var ipAddress by remember { mutableStateOf("127.0.0.1") }
    var startPort by remember { mutableStateOf("1") }
    var endPort by remember { mutableStateOf("1024") }
    
    val scanState by viewModel.scanState.collectAsState()

    LaunchedEffect(scanState) {
        if (scanState is NetworkScanner.ScanResult.Finished) {
            val results = (scanState as NetworkScanner.ScanResult.Finished).foundData
            // For now, we only scan one IP, so we map it. 
            // In a real subnet scanner, we'd have multiple IPs.
            onUpdateTopology(mapOf(ipAddress to results), null)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TCP Port Scanner") },
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OutlinedTextField(
                value = ipAddress,
                onValueChange = { ipAddress = it },
                label = { Text("IP Address") },
                modifier = Modifier.fillMaxWidth(),
            )
            
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = startPort,
                    onValueChange = { startPort = it },
                    label = { Text("Start Port") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = endPort,
                    onValueChange = { endPort = it },
                    label = { Text("End Port") },
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { 
                        viewModel.startScan(
                            ipAddress, 
                            startPort.toIntOrNull() ?: 1, 
                            endPort.toIntOrNull() ?: 1024
                        ) 
                    },
                    enabled = scanState !is NetworkScanner.ScanResult.Progress
                ) {
                    Text("Start Audit")
                }

                Button(
                    onClick = { viewModel.startCommonPortsScan(ipAddress) },
                    enabled = scanState !is NetworkScanner.ScanResult.Progress
                ) {
                    Text("Common Ports")
                }

                Button(
                    onClick = { viewModel.stopScan() },
                    enabled = scanState is NetworkScanner.ScanResult.Progress,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Stop Scan")
                }
            }
            
            if (scanState is NetworkScanner.ScanResult.Progress) {
                val progress = (scanState as NetworkScanner.ScanResult.Progress).progress
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Scanning & Auditing... ${(progress * 100).toInt()}%")
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            val foundPorts = when (val state = scanState) {
                is NetworkScanner.ScanResult.Progress -> state.foundData
                is NetworkScanner.ScanResult.Finished -> state.foundData
                else -> emptyList()
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(foundPorts) { port ->
                    AuditResultCard(port.port, port.serviceName, port.banner, port.vulnerabilities)
                }
            }
        }
    }
}

@Composable
fun AuditResultCard(port: Int, service: String, banner: String?, vulnerabilities: List<Vulnerability>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (vulnerabilities.isNotEmpty()) 
                MaterialTheme.colorScheme.errorContainer 
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Port: $port", fontWeight = FontWeight.Bold)
                Text("Service: $service", style = MaterialTheme.typography.bodyMedium)
            }

            if (banner != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Banner: $banner",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(6.dp),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }
            
            if (vulnerabilities.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Vulnerabilities Found:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                vulnerabilities.forEach { vuln ->
                    Text(
                        text = "• ${vuln.cveId}: ${vuln.description}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    SeverityBadge(vuln.severity)
                }
            } else {
                Text("No known vulnerabilities detected.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun SeverityBadge(severity: RiskLevel) {
    val color = when (severity) {
        RiskLevel.LOW -> Color.Green
        RiskLevel.MEDIUM -> Color.Yellow
        RiskLevel.HIGH -> Color(0xFFFFA500) // Orange
        RiskLevel.CRITICAL -> Color.Red
    }
    Box(
        modifier = Modifier
            .padding(top = 4.dp)
            .background(color = color.copy(alpha = 0.2f), shape = MaterialTheme.shapes.small)
    ) {
        Text(
            text = severity.name,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontWeight = FontWeight.Bold
        )
    }
}
