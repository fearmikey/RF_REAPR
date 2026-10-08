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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fearmikey.rf_reapr.ui.navigation.Screen
import androidx.compose.ui.tooling.preview.Preview
import com.fearmikey.rf_reapr.ui.settings.SettingsViewModel
import com.fearmikey.rf_reapr.ui.theme.*
import kotlinx.coroutines.launch

enum class ToolCategory(val title: String, val icon: ImageVector, val tint: Color) {
    NETWORK_AUDIT("Network Auditing", Icons.Default.Security, NetworkGreen),
    NETWORK_RECON("Network Discovery", Icons.Default.Router, NetworkGreen),
    NETWORK_DIAG("Network Diagnostics", Icons.Default.Build, NetworkGreen),
    MONITORING("Intrusion Detection", Icons.Default.Visibility, NetworkGreen),
    WIRELESS_PHYSICAL("Wireless & Physical", Icons.Default.Wifi, WifiOrange),
    WEB("Web & Infrastructure", Icons.Default.Language, WebGold),
    COMPLIANCE("Reporting & Logs", Icons.AutoMirrored.Filled.Assignment, ComplianceGold)
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
    val uriHandler = LocalUriHandler.current
    val isPassiveMode by settingsViewModel?.isPassiveMode?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }
    val appLaunchCount by settingsViewModel?.appLaunchCount?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(0) }
    val supportDialogNeverAsk by settingsViewModel?.supportDialogNeverAsk?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val highlightAlpha = remember { Animatable(0f) }
    var showActiveWarning by remember { mutableStateOf(false) }
    var showSupportDialog by remember { mutableStateOf(false) }

    LaunchedEffect(appLaunchCount, supportDialogNeverAsk) {
        if (settingsViewModel != null && appLaunchCount >= 3 && !supportDialogNeverAsk) {
            val lastShown = settingsViewModel.getSupportDialogLastShownLaunch()
            if (lastShown != appLaunchCount) {
                showSupportDialog = true
            }
        }
    }

    val allTools = remember {
        listOf(
            ToolkitTool(
                "Port Scanner",
                "Identify open TCP ports and services.",
                Icons.Default.Search,
                Screen.PortScanner.route,
                ToolCategory.NETWORK_AUDIT,
                isAggressive = true
            ),
            ToolkitTool(
                "Network Discovery",
                "Map connected devices and automatically audit services.",
                Icons.AutoMirrored.Filled.List,
                Screen.TopologyMap.route,
                ToolCategory.NETWORK_RECON,
                isAggressive = true
            ),
            ToolkitTool(
                "DHCP Monitor",
                "Detect rogue devices and new hardware.",
                Icons.Default.NotificationsActive,
                Screen.DhcpMonitor.route,
                ToolCategory.NETWORK_RECON,
                isAggressive = true
            ),
            ToolkitTool(
                "Ping Tool",
                "Send ICMP echo requests to a host or IP.",
                Icons.Default.NetworkCheck,
                Screen.PingTool.route,
                ToolCategory.NETWORK_DIAG,
                isAggressive = true
            ),
            ToolkitTool(
                "Service Discovery",
                "Discover mDNS/Bonjour services on the network.",
                Icons.Default.SettingsRemote,
                Screen.ServiceDiscovery.route,
                ToolCategory.NETWORK_RECON,
                isAggressive = true
            ),
            ToolkitTool(
                "UPnP/NAT-PMP Auditor",
                "Audit router port mapping vulnerabilities.",
                Icons.Default.Router,
                Screen.UpnpAuditor.route,
                ToolCategory.NETWORK_AUDIT,
                isAggressive = true
            ),
            ToolkitTool(
                "DNS Security Auditor",
                "Detect DNS hijacking and security leaks.",
                Icons.Default.LockPerson,
                Screen.DnsAuditor.route,
                ToolCategory.NETWORK_AUDIT,
                isAggressive = true
            ),
            ToolkitTool(
                "Visual Traceroute",
                "Map the path packets take to a destination.",
                Icons.Default.Route,
                Screen.Traceroute.route,
                ToolCategory.NETWORK_DIAG,
                isAggressive = true
            ),
            ToolkitTool(
                "iPerf Tester",
                "Measure network throughput using iPerf3.",
                Icons.Default.NetworkPing,
                Screen.IperfTester.route,
                ToolCategory.NETWORK_DIAG,
                isAggressive = true
            ),
            ToolkitTool(
                "Packet Capture",
                "Capture network traffic to a PCAP file.",
                Icons.Default.Waves,
                Screen.PacketCapture.route,
                ToolCategory.NETWORK_DIAG,
                isAggressive = true
            ),
            ToolkitTool(
                "Subnet Calculator",
                "Calculate CIDR network boundaries and host ranges.",
                Icons.Default.Calculate,
                Screen.SubnetCalculator.route,
                ToolCategory.NETWORK_DIAG
            ),
            ToolkitTool(
                "MAC OUI Lookup",
                "Look up device manufacturers by MAC address offline.",
                Icons.Default.Memory,
                Screen.MacLookup.route,
                ToolCategory.NETWORK_RECON
            ),
            ToolkitTool(
                "DNS Query Tool",
                "Perform manual DNS record lookups (A, MX, TXT, etc).",
                Icons.Default.Dns,
                Screen.DnsQuery.route,
                ToolCategory.NETWORK_DIAG
            ),
            ToolkitTool(
                "Wake-on-LAN Injector",
                "Send Magic Packets to wake devices on the network.",
                Icons.Default.PowerSettingsNew,
                Screen.WolInjector.route,
                ToolCategory.NETWORK_DIAG
            ),
            ToolkitTool(
                "Captive Portal Detector",
                "Detect HTTP interception and walled gardens.",
                Icons.Default.Sensors,
                Screen.CaptivePortalDetector.route,
                ToolCategory.NETWORK_AUDIT
            ),
            ToolkitTool(
                "SNMP Browser",
                "Query routers and switches for system info and traffic.",
                Icons.Default.Router,
                Screen.SnmpBrowser.route,
                ToolCategory.NETWORK_AUDIT,
                isAggressive = true
            ),
            ToolkitTool(
                "ARP Spoofing Detector",
                "Monitor network for ARP spoofing attempts.",
                Icons.Default.NotificationsActive,
                Screen.ArpDetector.route,
                ToolCategory.MONITORING
            ),
            ToolkitTool(
                "Bluetooth Proximity Finder",
                "Consolidated auditor and locator for BLE devices.",
                Icons.Default.Bluetooth,
                Screen.BluetoothProximityFinder.route,
                ToolCategory.WIRELESS_PHYSICAL,
                BluetoothBlue
            ),
            ToolkitTool(
                "WiFi Spectrum Analyzer",
                "Interactive visualization of WiFi channel overlap and bandwidth.",
                Icons.Default.Wifi,
                Screen.WifiFingerprinter.route,
                ToolCategory.WIRELESS_PHYSICAL,
                WifiOrange
            ),
            ToolkitTool(
                "SDR Controller",
                "Real-time spectrum analysis via RTL-SDR (rtl_tcp).",
                Icons.Default.Waves,
                Screen.SdrController.route,
                ToolCategory.WIRELESS_PHYSICAL,
                NetworkGreen
            ),
            ToolkitTool(
                "NFC Scanner",
                "Audit physical access tags and NDEF messages.",
                Icons.Default.Nfc,
                Screen.NfcScanner.route,
                ToolCategory.WIRELESS_PHYSICAL,
                NfcPurple
            ),
            ToolkitTool(
                "HID Injector",
                "Deploy keystroke payloads via USB HID emulation.",
                Icons.Default.Usb,
                Screen.HidInjector.route,
                ToolCategory.WIRELESS_PHYSICAL,
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
                ToolCategory.WIRELESS_PHYSICAL
            ),
            ToolkitTool(
                "USB OTG Auditor",
                "Enumerate and inspect connected USB devices and interfaces.",
                Icons.Default.Usb,
                Screen.UsbOtgAuditor.route,
                ToolCategory.WIRELESS_PHYSICAL,
                NfcPurple
            ),
            ToolkitTool(
                "Cellular Tower Recon",
                "Map local LTE/5G cell towers and signal strength.",
                Icons.Default.CellTower,
                Screen.CellularRecon.route,
                ToolCategory.WIRELESS_PHYSICAL,
                NetworkGreen
            ),
            ToolkitTool(
                "WPS PIN Calculator",
                "Generate default WPS PINs for known vulnerable routers.",
                Icons.Default.Password,
                Screen.WpsPinCalculator.route,
                ToolCategory.WIRELESS_PHYSICAL,
                WifiOrange,
                isAggressive = true
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
                "Cloud Recon (Shodan)",
                "Search for exposed devices by IP, domain, or IoT query.",
                Icons.Default.Language,
                Screen.ShodanScanner.route,
                ToolCategory.WEB,
                isAggressive = true,
                requiresApiKey = false
            ),
            ToolkitTool(
                "Hash Calculator",
                "Generate and verify MD5, SHA-1, and SHA-256 hashes.",
                Icons.Default.EnhancedEncryption,
                Screen.HashCalculator.route,
                ToolCategory.WEB
            ),
            ToolkitTool(
                "Certificate Decoder",
                "Decode and inspect PEM-encoded X.509 certificates.",
                Icons.Default.VpnKey,
                Screen.CertDecoder.route,
                ToolCategory.WEB
            ),
            ToolkitTool(
                "Reverse Shell Cheatsheet",
                "Offline reference for generating reverse shell payloads.",
                Icons.Default.Terminal,
                Screen.ReverseShellCheatsheet.route,
                ToolCategory.WEB,
                isAggressive = true
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
                ToolCategory.COMPLIANCE
            ),
            ToolkitTool(
                "Bluetooth Scanning Logs",
                "Export event logs for BLE scans.",
                Icons.Default.Bluetooth,
                Screen.BleLogs.route,
                ToolCategory.COMPLIANCE
            ),
            ToolkitTool(
                "Network Discovery Logs",
                "Export network discovery and audit logs.",
                Icons.AutoMirrored.Filled.List,
                Screen.TopologyLogs.route,
                ToolCategory.COMPLIANCE
            ),
            ToolkitTool(
                "Port Scanning Logs",
                "Export detailed port scan results.",
                Icons.Default.Search,
                Screen.PortLogs.route,
                ToolCategory.COMPLIANCE
            ),
            ToolkitTool(
                "Website Inspector Logs",
                "Export web infrastructure audit logs.",
                Icons.Default.Language,
                Screen.WebLogs.route,
                ToolCategory.COMPLIANCE
            ),
            ToolkitTool(
                "Ping Report Logs",
                "Export ICMP ping response logs.",
                Icons.Default.NetworkCheck,
                Screen.PingLogs.route,
                ToolCategory.COMPLIANCE
            ),
            ToolkitTool(
                "iPerf Tester Logs",
                "Export network throughput logs.",
                Icons.Default.NetworkPing,
                Screen.IperfLogs.route,
                ToolCategory.COMPLIANCE
            ),
            ToolkitTool(
                "SNMP Browser Logs",
                "Export SNMP query results.",
                Icons.Default.Router,
                Screen.SnmpLogs.route,
                ToolCategory.COMPLIANCE
            ),
            ToolkitTool(
                "Packet Capture Logs",
                "Export raw PCAP files.",
                Icons.Default.Waves,
                Screen.PacketCaptureLogs.route,
                ToolCategory.COMPLIANCE
            )
        )
    }

    val toolsByCategory = remember(allTools) {
        allTools.groupBy { it.category }
    }

    val onInhibitedClick: () -> Unit = {
        scope.launch {
            listState.animateScrollToItem(0)
            repeat(3) {
                highlightAlpha.animateTo(0.6f, animationSpec = tween(300))
                highlightAlpha.animateTo(0f, animationSpec = tween(300))
            }
        }
    }

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
                item(key = category.name) {
                    CategoryCard(
                        category = category,
                        isExpanded = expandedCategory == category,
                        onClick = {
                            expandedCategory = if (expandedCategory == category) null else category
                        },
                        tools = toolsByCategory[category] ?: emptyList(),
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

    if (showSupportDialog) {
        AlertDialog(
            onDismissRequest = {
                settingsViewModel?.setSupportDialogLastShownLaunch(appLaunchCount)
                showSupportDialog = false
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = PhysicalRed,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Support RF-REAPR",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Enjoying RF-REAPR? It is 100% free, open source, and privacy-first with zero ads. If you'd like to support ongoing development, consider buying me a coffee!",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            settingsViewModel?.setSupportDialogNeverAsk(true)
                            showSupportDialog = false
                            uriHandler.openUri("https://buymeacoffee.com/ximw7nxi1j")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Buy Me a Coffee")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            settingsViewModel?.setSupportDialogLastShownLaunch(appLaunchCount)
                            showSupportDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Ask me later")
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    TextButton(
                        onClick = {
                            settingsViewModel?.setSupportDialogNeverAsk(true)
                            showSupportDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Don't ask again",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {}
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
                    val lastIndex = tools.size - 1
                    tools.forEachIndexed { index, tool ->
                        ToolItem(tool, isPassiveMode, settingsViewModel, onNavigate, onInhibitedClick)
                        if (index != lastIndex) {
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
            Screen.HibpChecker.route -> settingsViewModel?.hibpApiKey?.collectAsStateWithLifecycle()?.value.isNullOrBlank()
            Screen.ShodanScanner.route -> settingsViewModel?.shodanApiKey?.collectAsStateWithLifecycle()?.value.isNullOrBlank()
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
