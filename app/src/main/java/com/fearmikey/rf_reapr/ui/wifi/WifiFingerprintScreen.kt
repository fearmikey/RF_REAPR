package com.fearmikey.rf_reapr.ui.wifi

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fearmikey.rf_reapr.domain.model.WifiAccessPoint
import com.fearmikey.rf_reapr.ui.theme.WifiOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiFingerprintScreen(
    viewModel: WifiFingerprintViewModel,
    onBackClick: () -> Unit,
) {
    val filteredAccessPoints by viewModel.filteredAccessPoints.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val selectedRange by viewModel.selectedRange.collectAsState()
    val hiddenBssids by viewModel.hiddenBssids.collectAsState()
    
    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { stream ->
                val reader = stream.bufferedReader()
                viewModel.setImportedData(reader.readText())
            }
        }
    }

    // Auto-save scan on exit
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopScan()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WiFi Spectrum Analyzer") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                WifiFingerprintViewModel.FrequencyRange.entries.forEach { range ->
                    NavigationBarItem(
                        selected = selectedRange == range,
                        onClick = { viewModel.setSelectedRange(range) },
                        label = {
                            Text(
                                when (range) {
                                    WifiFingerprintViewModel.FrequencyRange.FREQ_2_4GHZ -> "2.4 GHz"
                                    WifiFingerprintViewModel.FrequencyRange.FREQ_5GHZ -> "5 GHz"
                                    WifiFingerprintViewModel.FrequencyRange.FREQ_6GHZ -> "6 GHz"
                                }
                            )
                        },
                        icon = {
                            Icon(Icons.Default.Wifi, contentDescription = null, tint = if (selectedRange == range) WifiOrange else LocalContentColor.current)
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { viewModel.startScan() },
                enabled = !isScanning,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = WifiOrange, contentColor = Color.Black)
            ) {
                Icon(Icons.Default.Wifi, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Start WiFi Scan")
            }
            
            OutlinedButton(
                onClick = { importLauncher.launch("application/json") },
                enabled = !isScanning,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Import Scan Log (JSON)")
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    WifiChannelGraph(
                        accessPoints = filteredAccessPoints,
                        hiddenBssids = hiddenBssids,
                        range = selectedRange
                    )
                }
                
                item {
                    Text(
                        text = "Access Points (${filteredAccessPoints.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(
                    items = filteredAccessPoints.sortedByDescending { it.signalLevel },
                    key = { it.bssid } // Performance: Stable keys for lazy list
                ) { ap ->
                    WifiAccessPointItem(
                        ap = ap,
                        isHidden = hiddenBssids.contains(ap.bssid),
                        onToggleVisibility = { viewModel.toggleBssidVisibility(ap.bssid) }
                    )
                }
                
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun WifiAccessPointItem(
    ap: WifiAccessPoint,
    isHidden: Boolean,
    onToggleVisibility: () -> Unit
) {
    val isRogue = ap.isRogueSuspect
    val apColor = if (isRogue) MaterialTheme.colorScheme.error else getColorForBssid(ap.bssid)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
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
                tint = if (isHidden) apColor.copy(alpha = 0.3f) else apColor
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ap.ssid.ifEmpty { "[Hidden SSID]" },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isHidden) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "BSSID: ${ap.bssid}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isHidden) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                )
                val channel = ap.getChannel()
                val channelText = if (channel != -1) " | Ch: $channel" else ""
                Text(
                    text = "Signal: ${ap.signalLevel} dBm | Freq: ${ap.frequency} MHz$channelText",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isHidden) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Capabilities: ${ap.capabilities}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isHidden) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
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
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    imageVector = if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (isHidden) "Show on graph" else "Hide from graph",
                    tint = if (isHidden) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
