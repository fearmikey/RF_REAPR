package com.fearmikey.rf_reapr.ui.arp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val gatewayInfo by viewModel.gatewayInfo.collectAsStateWithLifecycle()
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    val isMonitoring by viewModel.isMonitoring.collectAsStateWithLifecycle()
    var showInfo by remember { mutableStateOf(false) }

    // Start automatically the first time the screen opens.
    LaunchedEffect(Unit) { viewModel.startMonitoring() }

    val critical = alerts.any { it.severity == AlertSeverity.CRITICAL }
    val warnings = alerts.count { it.severity == AlertSeverity.WARNING }

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
                    IconButton(onClick = { showInfo = true }) { Icon(Icons.Default.Info, contentDescription = "How it works") }
                    if (alerts.isNotEmpty()) {
                        IconButton(onClick = viewModel::clearAlerts) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear alerts")
                        }
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
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            critical -> MaterialTheme.colorScheme.errorContainer
                            warnings > 0 -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (critical || warnings > 0) Icons.Default.Warning else Icons.Default.Security,
                                contentDescription = null,
                                tint = if (critical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = when {
                                        critical -> "Possible man-in-the-middle"
                                        warnings > 0 -> "Network changes detected"
                                        isMonitoring -> "Monitoring: no threats seen"
                                        else -> "Monitoring stopped"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                gatewayInfo?.takeIf { it.checks > 0 }?.let {
                                    Text("${it.checks} checks · every 5 s", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            if (isMonitoring) {
                                FilledTonalIconButton(onClick = viewModel::stopMonitoring) { Icon(Icons.Default.Stop, contentDescription = "Stop monitoring") }
                            } else {
                                FilledTonalIconButton(onClick = viewModel::startMonitoring) { Icon(Icons.Default.PlayArrow, contentDescription = "Start monitoring") }
                            }
                        }
                        if (isMonitoring && (gatewayInfo?.checks ?: 0) == 0) {
                            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        gatewayInfo?.let { info ->
                            StatusRow("Network", info.ssid)
                            info.bssid?.let { StatusRow("Access point", it) }
                            StatusRow("Gateway IP", info.ipAddress.ifBlank { "—" })
                            StatusRow("Gateway MAC", info.macAddress.ifBlank { "—" })
                            StatusRow("First hop", info.firstHop ?: "measuring…")
                            if (info.dnsServers.isNotEmpty()) StatusRow("DNS", info.dnsServers.joinToString())
                            if (info.arpTableAvailable) StatusRow("ARP entries", info.neighborCount.toString())
                        }
                    }
                }
            }

            if (gatewayInfo?.let { !it.arpTableAvailable && it.checks > 0 } == true) {
                item {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                        Text(
                            "Android 10+ hides the ARP table from apps, so gateway MAC monitoring isn't possible on this device. The detector still watches the first hop (an extra local hop means traffic is being forwarded through another device), gateway and DNS changes, and access-point changes. Grant location permission to see the access point.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            item {
                Text("Alerts (${alerts.size})", style = MaterialTheme.typography.titleMedium)
            }

            if (alerts.isEmpty()) {
                item {
                    Text(
                        "No suspicious activity detected yet. Keep this running while you use the network; it keeps working in the background.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(alerts, key = { "${it.timestamp}|${it.message}" }) { alert -> AlertItem(alert) }
            }
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("How detection works") },
            text = {
                Text(
                    "• Gateway MAC change or a MAC shared by two IPs: the classic signs of ARP spoofing (needs a readable ARP table).\n" +
                        "• Extra first hop: a TTL-1 probe toward 1.1.1.1 should expire at your gateway. If another local address answers, something is forwarding your traffic.\n" +
                        "• Gateway or DNS servers changing on the same Wi-Fi can mean a rogue DHCP server.\n" +
                        "• Access point (BSSID) changes on the same SSID can mean roaming, or an evil-twin AP.\n\n" +
                        "Critical alerts post a notification even when the app is in the background.",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Close") } }
        )
    }
}

@Composable
fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun AlertItem(alert: ArpAlert) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    var expanded by remember { mutableStateOf(alert.severity == AlertSeverity.CRITICAL) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = when (alert.severity) {
                AlertSeverity.CRITICAL -> MaterialTheme.colorScheme.errorContainer
                AlertSeverity.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
                AlertSeverity.INFO -> MaterialTheme.colorScheme.secondaryContainer
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = alert.message,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    color = if (alert.severity == AlertSeverity.CRITICAL) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                Text(text = dateFormat.format(Date(alert.timestamp)), style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text("Before: ${alert.oldMac}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            Text("Now: ${alert.newMac}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            AnimatedVisibility(expanded && alert.detail.isNotBlank()) {
                Text(alert.detail, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
