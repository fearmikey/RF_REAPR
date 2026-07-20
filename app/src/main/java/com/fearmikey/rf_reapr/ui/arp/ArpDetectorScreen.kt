package com.fearmikey.rf_reapr.ui.arp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fearmikey.rf_reapr.domain.repository.AlertSeverity
import com.fearmikey.rf_reapr.domain.repository.ArpAlert
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArpDetectorScreen(
    viewModel: ArpDetectorViewModel,
    onBack: () -> Unit
) {
    val gatewayInfo by viewModel.gatewayInfo.collectAsState()
    val alerts by viewModel.alerts.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startMonitoring()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ARP Spoofing Detector") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (alerts.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearAlerts() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear Alerts")
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
            // Gateway Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (alerts.any { it.severity == AlertSeverity.CRITICAL }) 
                        MaterialTheme.colorScheme.errorContainer 
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (alerts.any { it.severity == AlertSeverity.CRITICAL }) 
                                Icons.Default.Warning 
                            else Icons.Default.Security,
                            contentDescription = null,
                            tint = if (alerts.any { it.severity == AlertSeverity.CRITICAL }) 
                                MaterialTheme.colorScheme.error 
                            else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (alerts.any { it.severity == AlertSeverity.CRITICAL }) 
                                "Network Security Compromised" 
                            else "Gateway Monitoring Active",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    gatewayInfo?.let { info ->
                        StatusRow("SSID", info.ssid)
                        StatusRow("Gateway IP", info.ipAddress)
                        StatusRow("Gateway MAC", info.macAddress)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Security Alerts",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (alerts.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No suspicious ARP activity detected.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(alerts) { alert ->
                        AlertItem(alert)
                    }
                }
            }
        }
    }
}

@Composable
fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun AlertItem(alert: ArpAlert) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (alert.severity) {
                AlertSeverity.CRITICAL -> MaterialTheme.colorScheme.errorContainer
                AlertSeverity.WARNING -> Color(0xFFFFF4E5)
                AlertSeverity.INFO -> MaterialTheme.colorScheme.secondaryContainer
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = alert.message,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = when (alert.severity) {
                        AlertSeverity.CRITICAL -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = dateFormat.format(Date(alert.timestamp)),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Old: ${alert.oldMac}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            Text(
                text = "New: ${alert.newMac}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}
