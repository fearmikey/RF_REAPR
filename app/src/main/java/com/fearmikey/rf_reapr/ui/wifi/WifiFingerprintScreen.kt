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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fearmikey.rf_reapr.domain.model.WifiAccessPoint
import com.fearmikey.rf_reapr.domain.service.ChannelAdvice
import com.fearmikey.rf_reapr.domain.service.WifiSecurityAnalyzer
import com.fearmikey.rf_reapr.domain.service.WifiSecurityFamily
import com.fearmikey.rf_reapr.ui.theme.WifiOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiFingerprintScreen(
    viewModel: WifiFingerprintViewModel,
    onBackClick: () -> Unit,
) {
    val filteredAccessPoints by viewModel.filteredAccessPoints.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val selectedRange by viewModel.selectedRange.collectAsStateWithLifecycle()
    val hiddenBssids by viewModel.hiddenBssids.collectAsStateWithLifecycle()
    val simplifyGraph by viewModel.simplifyGraph.collectAsStateWithLifecycle()
    val scanStatus by viewModel.scanStatus.collectAsStateWithLifecycle()
    val channelAdvice by viewModel.channelAdvice.collectAsStateWithLifecycle()
    val allAccessPoints by viewModel.accessPoints.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

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
                Text(
                    when {
                        !scanStatus.wifiEnabled -> "Wi-Fi is off. Turn it on (or enable Wi-Fi scanning in location settings)."
                        !scanStatus.lastRequestAccepted -> "Android is throttling scans (4 per 2 min); showing cached results. Refreshes every 30 s."
                        scanStatus.lastResultsAt > 0 -> "Live: ${scanStatus.resultCount} APs · refreshes every 30 s"
                        else -> "Scanning for nearby access points…"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (!scanStatus.wifiEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Simplify View",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Group duplicate SSIDs & limit labels to the strongest signals",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = simplifyGraph,
                            onCheckedChange = { viewModel.toggleSimplifyGraph() }
                        )
                    }
                }

                if (allAccessPoints.isNotEmpty()) {
                    item { WifiSecuritySummary(allAccessPoints, channelAdvice) }
                }

                item {
                    WifiChannelGraph(
                        accessPoints = filteredAccessPoints,
                        hiddenBssids = hiddenBssids,
                        range = selectedRange,
                        simplifyGraph = simplifyGraph
                    )
                }
                
                item {
                    Text(
                        text = "Access Points (${filteredAccessPoints.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        placeholder = { Text("Filter by SSID, BSSID or vendor") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                }

                items(
                    items = filteredAccessPoints.sortedWith(compareByDescending<WifiAccessPoint> { it.isRogueSuspect }.thenByDescending { it.signalLevel }),
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
private fun WifiSecuritySummary(aps: List<WifiAccessPoint>, advice: ChannelAdvice?) {
    val securities = remember(aps) { aps.map { WifiSecurityAnalyzer.analyze(it.capabilities) } }
    val open = securities.count { it.family == WifiSecurityFamily.OPEN }
    val wep = securities.count { it.family == WifiSecurityFamily.WEP }
    val wpa1 = securities.count { it.family == WifiSecurityFamily.WPA }
    val wps = securities.count { it.wps }
    val wpa3 = securities.count { it.family == WifiSecurityFamily.WPA3 }
    val rogues = aps.count { it.isRogueSuspect }
    val networks = aps.map { it.ssid }.filter { it.isNotBlank() }.distinct().size
    val hidden = aps.count { it.ssid.isBlank() }

    Card(colors = CardDefaults.cardColors(containerColor = if (rogues > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Security overview", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("${aps.size} radios · $networks networks" + if (hidden > 0) " · $hidden hidden" else "", style = MaterialTheme.typography.bodySmall)
            if (rogues > 0) {
                Text("$rogues possible evil-twin AP${if (rogues == 1) "" else "s"}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            }
            val weak = listOfNotNull(
                open.takeIf { it > 0 }?.let { "$it open" },
                wep.takeIf { it > 0 }?.let { "$it WEP" },
                wpa1.takeIf { it > 0 }?.let { "$it WPA1" },
                wps.takeIf { it > 0 }?.let { "$it with WPS" }
            )
            Text(
                if (weak.isEmpty()) "No open, WEP or WPS networks nearby" else "Weak: ${weak.joinToString()}",
                style = MaterialTheme.typography.bodySmall,
                color = if (weak.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
            )
            if (wpa3 > 0) Text("$wpa3 support WPA3", style = MaterialTheme.typography.bodySmall)
            advice?.let {
                Text(
                    "Best ${it.band.label} channel: ${it.bestChannel} (${it.reason})",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
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
                val security = remember(ap.capabilities) { WifiSecurityAnalyzer.analyze(ap.capabilities) }
                val extras = listOfNotNull(
                    "${ap.bandwidth} MHz",
                    ap.standard,
                    ap.vendor,
                    if (ap.isConnected) "Connected" else null
                )
                Text(
                    text = extras.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isHidden) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Text(
                        text = security.label + if (security.wps) " · WPS" else "",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = when (security.risk) {
                            com.fearmikey.rf_reapr.domain.model.RiskLevel.HIGH, com.fearmikey.rf_reapr.domain.model.RiskLevel.CRITICAL -> MaterialTheme.colorScheme.error
                            com.fearmikey.rf_reapr.domain.model.RiskLevel.MEDIUM -> Color(0xFFFF9800)
                            else -> Color(0xFF4CAF50)
                        }
                    )
                }
                security.issues.forEach { issue ->
                    Text("• $issue", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (isRogue) {
                    Text(
                        text = "POSSIBLE EVIL TWIN" + (ap.rogueReason?.let { ": $it" } ?: ""),
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
