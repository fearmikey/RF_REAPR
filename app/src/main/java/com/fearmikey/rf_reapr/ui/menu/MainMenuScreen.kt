package com.fearmikey.rf_reapr.ui.menu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fearmikey.rf_reapr.ui.navigation.Screen
import androidx.compose.ui.tooling.preview.Preview
import com.fearmikey.rf_reapr.ui.settings.SettingsViewModel
import com.fearmikey.rf_reapr.ui.theme.*
import kotlinx.coroutines.launch

enum class ToolCategory(val title: String, val icon: ImageVector, val tint: Color) {
    NETWORK("Network Auditing", Icons.Default.Router, NetworkGreen),
    WIRELESS("Wireless Auditing", Icons.Default.Wifi, WifiOrange),
    WEB("Web & Infrastructure", Icons.Default.Language, WebGold),
    PHYSICAL("Physical Access", Icons.Default.Nfc, PhysicalRed),
    COMPLIANCE("Compliance & Reporting", Icons.AutoMirrored.Filled.Assignment, ComplianceGold),
    LOGS("Log Exports", Icons.AutoMirrored.Filled.List, LogGrey)
}

data class ToolkitTool(
    val name: String,
    val description: String,
    val icon: ImageVector,
    val route: String,
    val category: ToolCategory,
    val tint: Color? = null,
    val isAggressive: Boolean = false,
    val requiresApiKey: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMenuScreen(
    settingsViewModel: SettingsViewModel? = null,
    onNavigate: (String) -> Unit
) {
    val isPassiveMode by settingsViewModel?.isPassiveMode?.collectAsState() ?: remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val highlightAlpha = remember { Animatable(0f) }
    var showActiveWarning by remember { mutableStateOf(false) }

    val onInhibitedClick: () -> Unit = {
        scope.launch {
            listState.animateScrollToItem(0)
            repeat(3) {
                highlightAlpha.animateTo(0.6f, animationSpec = tween(300))
                highlightAlpha.animateTo(0f, animationSpec = tween(300))
            }
        }
    }

    val allTools = listOf(
        ToolkitTool(
            "Port Scanner",
            "Identify open TCP ports and services.",
            Icons.Default.Search,
            Screen.PortScanner.route,
            ToolCategory.NETWORK,
            isAggressive = true
        ),
        ToolkitTool(
            "Network Topology",
            "Visualize the network graph and risk levels.",
            Icons.AutoMirrored.Filled.List,
            Screen.TopologyMap.route,
            ToolCategory.NETWORK,
            isAggressive = true
        ),
        ToolkitTool(
            "DHCP Monitor",
            "Detect rogue devices and new hardware.",
            Icons.Default.NotificationsActive,
            Screen.DhcpMonitor.route,
            ToolCategory.NETWORK,
            isAggressive = true
        ),
        ToolkitTool(
            "Ping Tool",
            "Send ICMP echo requests to a host or IP.",
            Icons.Default.NetworkCheck,
            Screen.PingTool.route,
            ToolCategory.NETWORK,
            isAggressive = true
        ),
        ToolkitTool(
            "Service Discovery",
            "Discover mDNS/Bonjour services on the network.",
            Icons.Default.SettingsRemote,
            Screen.ServiceDiscovery.route,
            ToolCategory.NETWORK,
            isAggressive = true
        ),
        ToolkitTool(
            "UPnP/NAT-PMP Auditor",
            "Audit router port mapping vulnerabilities.",
            Icons.Default.Router,
            Screen.UpnpAuditor.route,
            ToolCategory.NETWORK,
            isAggressive = true
        ),
        ToolkitTool(
            "DNS Security Auditor",
            "Detect DNS hijacking and security leaks.",
            Icons.Default.LockPerson,
            Screen.DnsAuditor.route,
            ToolCategory.NETWORK,
            isAggressive = true
        ),
        ToolkitTool(
            "Visual Traceroute",
            "Map the path packets take to a destination.",
            Icons.Default.Route,
            Screen.Traceroute.route,
            ToolCategory.NETWORK,
            isAggressive = true
        ),
        ToolkitTool(
            "iPerf Tester",
            "Measure network throughput using iPerf3.",
            Icons.Default.NetworkPing,
            Screen.IperfTester.route,
            ToolCategory.NETWORK,
            isAggressive = true
        ),
        ToolkitTool(
            "ARP Spoofing Detector",
            "Monitor network for ARP spoofing attempts.",
            Icons.Default.NotificationsActive,
            Screen.ArpDetector.route,
            ToolCategory.NETWORK
        ),
        ToolkitTool(
            "Bluetooth Proximity Finder",
            "Consolidated auditor and locator for BLE devices.",
            Icons.Default.Bluetooth,
            Screen.BluetoothProximityFinder.route,
            ToolCategory.WIRELESS,
            BluetoothBlue
        ),
        ToolkitTool(
            "WiFi Spectrum Analyzer",
            "Interactive visualization of WiFi channel overlap and bandwidth.",
            Icons.Default.Wifi,
            Screen.WifiFingerprinter.route,
            ToolCategory.WIRELESS,
            WifiOrange
        ),
        ToolkitTool(
            "SDR Controller",
            "Real-time spectrum analysis via RTL-SDR (rtl_tcp).",
            Icons.Default.Waves,
            Screen.SdrController.route,
            ToolCategory.WIRELESS,
            NetworkGreen
        ),
        ToolkitTool(
            "NFC Scanner",
            "Audit physical access tags and NDEF messages.",
            Icons.Default.Nfc,
            Screen.NfcScanner.route,
            ToolCategory.WIRELESS, // Note: NFC is in Wireless category but using NfcPurple
            NfcPurple
        ),
        ToolkitTool(
            "HID Injector",
            "Deploy keystroke payloads via USB HID emulation.",
            Icons.Default.Usb,
            Screen.HidInjector.route,
            ToolCategory.PHYSICAL,
            isAggressive = true
        ),
        ToolkitTool(
            "Evidence Capture",
            "Securely document physical security findings with metadata.",
            Icons.Default.CameraAlt,
            Screen.EvidenceCapture.route,
            ToolCategory.COMPLIANCE
        ),
        ToolkitTool(
            "Magnetometer",
            "Detect hidden electronics and wiring via magnetic fields.",
            Icons.Default.Waves,
            Screen.Magnetometer.route,
            ToolCategory.PHYSICAL
        ),
        ToolkitTool(
            "Website Inspector",
            "Combined HTTP, TLS, DNS, and RDAP audit tool.",
            Icons.Default.Language,
            Screen.WebsiteInspector.route,
            ToolCategory.WEB,
            isAggressive = true
        ),
        ToolkitTool(
            "Subdomain Enumerator",
            "Map attack surface via DNS brute-force.",
            Icons.Default.Dns,
            Screen.SubdomainFinder.route,
            ToolCategory.WEB,
            isAggressive = true
        ),
        ToolkitTool(
            "TLS Cipher Scanner",
            "Identify weak protocols and supported cipher suites.",
            Icons.Default.Lock,
            Screen.TlsCipherScanner.route,
            ToolCategory.WEB,
            isAggressive = true
        ),
        ToolkitTool(
            "Cloud Asset Discovery",
            "Search for public S3, GCS, and Azure buckets.",
            Icons.Default.Cloud,
            Screen.CloudAssetScanner.route,
            ToolCategory.WEB,
            isAggressive = true
        ),
        ToolkitTool(
            "HaveIBeenPwned Checker",
            "Check if accounts are in known data breaches.",
            Icons.Default.LockPerson,
            Screen.HibpChecker.route,
            ToolCategory.WEB,
            requiresApiKey = true
        ),
        ToolkitTool(
            "Shodan IoT Scanner",
            "Search for exposed IoT devices and open WAN ports.",
            Icons.Default.Search,
            Screen.ShodanScanner.route,
            ToolCategory.WEB,
            isAggressive = true,
            requiresApiKey = true
        ),
        // Compliance Tools
        ToolkitTool(
            "Audit Checklists",
            "NIST, ISO 27001, and SOC2 automated audit checklists.",
            Icons.AutoMirrored.Filled.Assignment,
            Screen.ComplianceChecklists.route,
            ToolCategory.COMPLIANCE
        ),
        ToolkitTool(
            "Report Generator",
            "Compile scan results and evidence into PDF/Word reports.",
            Icons.AutoMirrored.Filled.Assignment,
            Screen.ReportBuilder.route,
            ToolCategory.COMPLIANCE
        ),
        // Log Tools
        ToolkitTool(
            "WiFi Spectrum Logs",
            "Export event logs for WiFi scans.",
            Icons.Default.Wifi,
            Screen.WifiLogs.route,
            ToolCategory.LOGS
        ),
        ToolkitTool(
            "Bluetooth Scanning Logs",
            "Export event logs for BLE scans.",
            Icons.Default.Bluetooth,
            Screen.BleLogs.route,
            ToolCategory.LOGS
        ),
        ToolkitTool(
            "Network Map Logs",
            "Export network topology audit logs.",
            Icons.AutoMirrored.Filled.List,
            Screen.TopologyLogs.route,
            ToolCategory.LOGS
        ),
        ToolkitTool(
            "Port Scanning Logs",
            "Export detailed port scan results.",
            Icons.Default.Search,
            Screen.PortLogs.route,
            ToolCategory.LOGS
        ),
        ToolkitTool(
            "Website Inspector Logs",
            "Export web infrastructure audit logs.",
            Icons.Default.Language,
            Screen.WebLogs.route,
            ToolCategory.LOGS
        ),
        ToolkitTool(
            "Ping Report Logs",
            "Export ICMP ping response logs.",
            Icons.Default.NetworkCheck,
            Screen.PingLogs.route,
            ToolCategory.LOGS
        )
    )

    var expandedCategory by remember { mutableStateOf<ToolCategory?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RF_REAPR Toolkit") },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = highlightAlpha.value))
                            .padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = if (isPassiveMode) "STEALTH" else "DETECTABLE",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isPassiveMode) NetworkGreen else PhysicalRed,
                            fontWeight = FontWeight.Bold
                        )
                        Switch(
                            checked = !isPassiveMode,
                            onCheckedChange = { active ->
                                if (active) {
                                    showActiveWarning = true
                                } else {
                                    settingsViewModel?.setPassiveMode(true)
                                }
                            },
                            modifier = Modifier.padding(horizontal = 8.dp),
                            thumbContent = {
                                Icon(
                                    imageVector = if (isPassiveMode) Icons.Default.Lock else Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PhysicalRed,
                                checkedTrackColor = PhysicalRed.copy(alpha = 0.5f),
                                uncheckedThumbColor = NetworkGreen,
                                uncheckedTrackColor = NetworkGreen.copy(alpha = 0.5f)
                            )
                        )
                    }
                    IconButton(onClick = { onNavigate(Screen.Settings.route) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Security Audit Categories",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Surface(
                        color = (if (isPassiveMode) NetworkGreen else PhysicalRed).copy(alpha = 0.1f),
                        shape = MaterialTheme.shapes.extraSmall,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isPassiveMode) NetworkGreen else PhysicalRed)
                    ) {
                        Text(
                            if (isPassiveMode) "Passive Mode (Stealth)" else "Active Mode (Detectable)",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isPassiveMode) NetworkGreen else PhysicalRed,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            ToolCategory.entries.forEach { category ->
                item {
                    CategoryCard(
                        category = category,
                        isExpanded = expandedCategory == category,
                        onClick = {
                            expandedCategory = if (expandedCategory == category) null else category
                        },
                        tools = allTools.filter { it.category == category },
                        isPassiveMode = isPassiveMode,
                        settingsViewModel = settingsViewModel,
                        onNavigate = onNavigate,
                        onInhibitedClick = onInhibitedClick
                    )
                }
            }
        }
    }

    if (showActiveWarning) {
        AlertDialog(
            onDismissRequest = { showActiveWarning = false },
            title = { Text("Warning: Active Mode") },
            text = {
                Text(
                    "Switching to Active Mode (Detectable) allows the use of aggressive tools. " +
                    "Your activities will be detectable by security systems such as IDS and IPS. " +
                    "Proceed with caution.",
                    textAlign = TextAlign.Start
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        settingsViewModel?.setPassiveMode(false)
                        showActiveWarning = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PhysicalRed)
                ) {
                    Text("Enable Active Mode")
                }
            },
            dismissButton = {
                TextButton(onClick = { showActiveWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun CategoryCard(
    category: ToolCategory,
    isExpanded: Boolean,
    onClick: () -> Unit,
    tools: List<ToolkitTool>,
    isPassiveMode: Boolean,
    settingsViewModel: SettingsViewModel?,
    onNavigate: (String) -> Unit,
    onInhibitedClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column {
            ListItem(
                headlineContent = { Text(category.title, fontWeight = FontWeight.Bold) },
                leadingContent = { Icon(category.icon, contentDescription = null, tint = category.tint) },
                trailingContent = {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            )
            
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                ) {
                    tools.forEach { tool ->
                        ToolItem(tool, isPassiveMode, settingsViewModel, onNavigate, onInhibitedClick)
                        if (tool != tools.last()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ToolItem(
    tool: ToolkitTool,
    isPassiveMode: Boolean,
    settingsViewModel: SettingsViewModel?,
    onNavigate: (String) -> Unit,
    onInhibitedClick: () -> Unit
) {
    val isInhibited = isPassiveMode && tool.isAggressive
    val isApiKeyRequired = tool.requiresApiKey
    
    // Check if the required API key is missing
    val isApiKeyMissing = if (isApiKeyRequired) {
        when (tool.route) {
            Screen.HibpChecker.route -> settingsViewModel?.hibpApiKey?.collectAsState()?.value.isNullOrBlank()
            Screen.ShodanScanner.route -> settingsViewModel?.shodanApiKey?.collectAsState()?.value.isNullOrBlank()
            else -> false
        }
    } else {
        false
    }
    
    val isGreyedOut = isInhibited || isApiKeyMissing
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { 
                if (isInhibited) {
                    onInhibitedClick()
                } else if (isApiKeyMissing) {
                    // Navigate to api keys with highlight param
                    val highlightArg = when (tool.route) {
                        Screen.HibpChecker.route -> "hibp"
                        Screen.ShodanScanner.route -> "shodan"
                        else -> null
                    }
                    onNavigate(Screen.ApiKeys.createRoute(highlightArg))
                } else {
                    onNavigate(tool.route)
                }
            }
            .padding(vertical = 8.dp)
            .alpha(if (isGreyedOut) 0.5f else 1f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Icon(
                imageVector = tool.icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = tool.tint ?: tool.category.tint
            )
            if (isInhibited) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier
                        .size(12.dp)
                        .align(Alignment.BottomEnd),
                    tint = PhysicalRed
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = tool.name, style = MaterialTheme.typography.labelLarge)
            if (isInhibited) {
                 Text(
                    text = "Inhibited in Passive Mode",
                    style = MaterialTheme.typography.bodySmall,
                    color = PhysicalRed
                )
            } else if (isApiKeyMissing) {
                Text(
                    text = "Requires 3rd party API Key (tap to set)",
                    style = MaterialTheme.typography.bodySmall,
                    color = PhysicalRed
                )
            } else {
                 Text(
                    text = tool.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainMenuScreenPreview() {
    RF_REAPRTheme {
        MainMenuScreen(onNavigate = {})
    }
}
