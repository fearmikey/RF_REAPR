package com.example.rf_reapr.ui.scanner

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rf_reapr.domain.model.BleDevice
import com.example.rf_reapr.domain.scanner.NetworkScanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleScannerScreen(
    viewModel: BleScannerViewModel,
    onBack: () -> Unit
) {
    val scanState by viewModel.scanState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BLE Auditor") },
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = { viewModel.startScan() },
                enabled = scanState !is NetworkScanner.ScanResult.Progress,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Bluetooth, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Start BLE Scan")
            }

            if (scanState is NetworkScanner.ScanResult.Progress) {
                Button(
                    onClick = { viewModel.stopScan() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("Stop Scan")
                }
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Scanning for nearby IoT devices...")
            }

            Spacer(modifier = Modifier.height(16.dp))

            val devices = when (val state = scanState) {
                is NetworkScanner.ScanResult.Progress -> state.foundData
                is NetworkScanner.ScanResult.Finished -> state.foundData
                else -> emptyList()
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(devices) { device ->
                    BleDeviceCard(device)
                }
            }
        }
    }
}

@Composable
fun BleDeviceCard(device: BleDevice) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (device.serviceUuids.isNotEmpty()) 
                MaterialTheme.colorScheme.secondaryContainer 
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = device.name ?: "Unknown Device",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${device.rssi} dBm",
                    style = MaterialTheme.typography.bodySmall,
                    color = getRssiColor(device.rssi)
                )
            }
            Text(text = "MAC: ${device.address}", style = MaterialTheme.typography.bodySmall)
            
            if (device.serviceUuids.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Services Detected:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                device.serviceUuids.forEach { uuid ->
                    Text(text = "• $uuid", style = MaterialTheme.typography.bodySmall)
                }
            }
            
            val distance = estimateDistance(device.rssi)
            Text(
                text = "Estimated Distance: ${"%.1f".format(distance)}m",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.End),
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }
    }
}

fun estimateDistance(rssi: Int): Double {
    // Basic Path Loss Model: d = 10 ^ ((Measured Power - RSSI) / (10 * n))
    // Measured power at 1m is typically -59 to -65 dBm. n (path loss exponent) is ~2.0 in free space.
    val txPower = -59.0
    return Math.pow(10.0, (txPower - rssi) / 20.0)
}

@Composable
fun getRssiColor(rssi: Int): Color {
    return when {
        rssi > -60 -> Color.Green
        rssi > -80 -> Color.Yellow
        else -> Color.Red
    }
}
