package com.example.rf_reapr.ui.menu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rf_reapr.ui.navigation.Screen
import androidx.compose.ui.tooling.preview.Preview
import com.example.rf_reapr.ui.theme.RF_REAPRTheme

enum class ToolCategory(val title: String, val icon: ImageVector) {
    NETWORK("Network Auditing", Icons.Default.Router),
    WIRELESS("Wireless Auditing", Icons.Default.Wifi),
    WEB("Web & Infrastructure", Icons.Default.Language),
    PHYSICAL("Physical Access", Icons.Default.Nfc),
    COMPLIANCE("Compliance & Reporting", Icons.AutoMirrored.Filled.Assignment),
    LOGS("Log Exports", Icons.AutoMirrored.Filled.List)
}

data class ToolkitTool(
    val name: String,
    val description: String,
    val icon: ImageVector,
    val route: String,
    val category: ToolCategory
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMenuScreen(onNavigate: (String) -> Unit) {
    val allTools = listOf(
        ToolkitTool(
            "Port Scanner",
            "Identify open TCP ports and services.",
            Icons.Default.Search,
            Screen.PortScanner.route,
            ToolCategory.NETWORK
        ),
        ToolkitTool(
            "Network Topology",
            "Visualize the network graph and risk levels.",
            Icons.AutoMirrored.Filled.List,
            Screen.TopologyMap.route,
            ToolCategory.NETWORK
        ),
        ToolkitTool(
            "DHCP Monitor",
            "Detect rogue devices and new hardware.",
            Icons.Default.NotificationsActive,
            Screen.DhcpMonitor.route,
            ToolCategory.NETWORK
        ),
        ToolkitTool(
            "Ping Tool",
            "Send ICMP echo requests to a host or IP.",
            Icons.Default.NetworkCheck,
            Screen.PingTool.route,
            ToolCategory.NETWORK
        ),
        ToolkitTool(
            "Bluetooth Proximity Finder",
            "Consolidated auditor and locator for BLE devices.",
            Icons.Default.Bluetooth,
            Screen.BluetoothProximityFinder.route,
            ToolCategory.WIRELESS
        ),
        ToolkitTool(
            "WiFi Spectrum Analyzer",
            "Interactive visualization of WiFi channel overlap and bandwidth.",
            Icons.Default.Wifi,
            Screen.WifiFingerprinter.route,
            ToolCategory.WIRELESS
        ),
        ToolkitTool(
            "NFC Scanner",
            "Audit physical access tags and NDEF messages.",
            Icons.Default.Nfc,
            Screen.NfcScanner.route,
            ToolCategory.WIRELESS
        ),
        ToolkitTool(
            "HID Injector",
            "Deploy keystroke payloads via USB HID emulation.",
            Icons.Default.Usb,
            Screen.HidInjector.route,
            ToolCategory.PHYSICAL
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
            ToolCategory.WEB
        ),
        // Compliance Tools
        ToolkitTool(
            "Audit Checklists",
            "NIST, ISO 27001, and SOC2 automated audit checklists.",
            Icons.AutoMirrored.Filled.Assignment,
            Screen.ComplianceChecklists.route,
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
                    IconButton(onClick = { onNavigate(Screen.Settings.route) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Security Audit Categories",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
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
                        onNavigate = onNavigate
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryCard(
    category: ToolCategory,
    isExpanded: Boolean,
    onClick: () -> Unit,
    tools: List<ToolkitTool>,
    onNavigate: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column {
            ListItem(
                headlineContent = { Text(category.title, fontWeight = FontWeight.Bold) },
                leadingContent = { Icon(category.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
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
                        ToolItem(tool, onNavigate)
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
fun ToolItem(tool: ToolkitTool, onNavigate: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigate(tool.route) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = tool.icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = tool.name, style = MaterialTheme.typography.labelLarge)
            Text(
                text = tool.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
