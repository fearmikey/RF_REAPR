package com.example.rf_reapr.ui.wifi

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rf_reapr.domain.model.WifiAccessPoint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiFingerprintScreen(
    viewModel: WifiFingerprintViewModel,
    onBackClick: () -> Unit,
) {
    val accessPoints by viewModel.accessPoints.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startScan()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WiFi Fingerprinter") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = { viewModel.startScan() },
                enabled = !isScanning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Wifi, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Start WiFi Scan")
            }

            if (isScanning) {
                Button(
                    onClick = { viewModel.stopScan() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text("Stop Scan")
                }
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Scanning for nearby Access Points...")
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(accessPoints.sortedByDescending { it.signalLevel }) { ap ->
                    WifiAccessPointItem(ap)
                }
            }
        }
    }
}

@Composable
fun WifiAccessPointItem(ap: WifiAccessPoint) {
    val isRogue = ap.isRogueSuspect
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = if (isRogue) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isRogue) Icons.Default.Warning else Icons.Default.Wifi,
                contentDescription = null,
                tint = if (isRogue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = ap.ssid.ifEmpty { "[Hidden SSID]" },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "BSSID: ${ap.bssid}",
                    style = MaterialTheme.typography.bodySmall
                )
                val channel = ap.getChannel()
                val channelText = if (channel != -1) " | Ch: $channel" else ""
                Text(
                    text = "Signal: ${ap.signalLevel} dBm | Freq: ${ap.frequency} MHz$channelText",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Capabilities: ${ap.capabilities}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (isRogue) {
                    Text(
                        text = "POTENTIAL ROGUE AP / EVIL TWIN",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
