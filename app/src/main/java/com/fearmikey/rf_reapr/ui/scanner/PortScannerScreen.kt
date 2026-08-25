package com.fearmikey.rf_reapr.ui.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.fearmikey.rf_reapr.domain.model.OpenPort
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.ui.theme.WebGold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortScannerScreen(
    viewModel: PortScannerViewModel,
    initialIp: String? = null,
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onUpdateTopology: (Map<String, List<OpenPort>>, String?) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var ipAddress by remember { 
        mutableStateOf(TextFieldValue(initialIp ?: "127.0.0.1", TextRange((initialIp ?: "127.0.0.1").length))) 
    }
    var startPort by remember { mutableStateOf("1") }
    var endPort by remember { mutableStateOf("65535") }
    
    val foundPorts by viewModel.foundPorts.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val isPassiveMode by viewModel.isPassiveMode.collectAsStateWithLifecycle()
    val eta by viewModel.eta.collectAsStateWithLifecycle()
    val isApiKeySet by viewModel.isApiKeySet.collectAsStateWithLifecycle()

    var showWarningDialog by remember { mutableStateOf(false) }
    var showManualPortDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showApiKeyRecommendation by remember { mutableStateOf(false) }
    var selectedPortDetails by remember { mutableStateOf<OpenPort?>(null) }
    var pendingScanType by remember { mutableStateOf<ScanType?>(null) }

    val onPortClick = remember { { port: OpenPort -> selectedPortDetails = port } }

    LaunchedEffect(Unit) {
        if (!isApiKeySet) {
            showApiKeyRecommendation = true
        }
    }

    LaunchedEffect(initialIp) {
        if (initialIp != null) {
            ipAddress = TextFieldValue(initialIp, TextRange(initialIp.length))
        }
    }

    DisposableEffect(viewModel) {
        onDispose {
            viewModel.clearResults()
        }
    }

    LaunchedEffect(isScanning, foundPorts) {
        if (!isScanning && foundPorts.isNotEmpty()) {
            onUpdateTopology(mapOf(ipAddress.text to foundPorts), null)
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
                },
                actions = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Information")
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
                onValueChange = { newValue ->
                    // 1. If deleting, just update and exit to avoid IME stutter
                    if (newValue.text.length < ipAddress.text.length) {
                        ipAddress = newValue
                        return@OutlinedTextField
                    }

                    // 2. Filter input to only allowed characters
                    val text = newValue.text.filter { (it.isDigit() || it == '.') }
                    
                    // 3. Apply auto-formatting (dots) and limits
                    val parts = text.split('.')
                    if (parts.size <= 4) {
                        var formatted = ""
                        var cursorOffset = newValue.selection.start
                        
                        parts.forEachIndexed { index, s ->
                            val sanitized = s.take(3)
                            formatted += sanitized
                            
                            // Auto-add dot if 3 digits entered in an octet (and it's not the 4th octet)
                            if (sanitized.length == 3 && index < 3) {
                                // Only add if we're at the very end of the current input
                                if (index == parts.size - 1) {
                                    formatted += "."
                                    // Adjust cursor to be after the new dot
                                    if (cursorOffset == formatted.length - 1) {
                                        cursorOffset++
                                    }
                                }
                            }
                            
                            // Ensure dots between existing parts are preserved
                            if (index < parts.size - 1 && !formatted.endsWith(".")) {
                                formatted += "."
                            }
                        }
                        
                        ipAddress = newValue.copy(
                            text = formatted,
                            selection = TextRange(cursorOffset.coerceIn(0, formatted.length))
                        )
                    }
                },
                label = { Text("IP Address") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                trailingIcon = {
                    if (ipAddress.text.isNotEmpty()) {
                        IconButton(onClick = { ipAddress = TextFieldValue("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear IP Address"
                            )
                        }
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { 
                        keyboardController?.hide()
                        pendingScanType = ScanType.Common
                        showWarningDialog = true
                    },
                    enabled = !isScanning && !isPassiveMode,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Common Ports")
                }

                Button(
                    onClick = { 
                        keyboardController?.hide()
                        showManualPortDialog = true
                    },
                    enabled = !isScanning && !isPassiveMode,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Manual Selection")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.stopScan() },
                enabled = isScanning,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Stop Scan")
            }
            
            if (isScanning) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Scanning... ${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                    if (eta != null) {
                        Text("ETA: $eta", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            // ... (Alert dialogs omitted for brevity in replace_file_content but I will keep them)
            // Wait, I should include the rest of the logic.

            if (showManualPortDialog) {
                AlertDialog(
                    onDismissRequest = { showManualPortDialog = false },
                    title = { Text("Manual Port Range") },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = startPort,
                                onValueChange = { startPort = it },
                                label = { Text("Start Port") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = endPort,
                                onValueChange = { endPort = it },
                                label = { Text("End Port") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                pendingScanType = ScanType.Full(
                                    startPort.toIntOrNull() ?: 1,
                                    endPort.toIntOrNull() ?: 65535
                                )
                                showManualPortDialog = false
                                showWarningDialog = true
                            }
                        ) {
                            Text("Set Range & Audit")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showManualPortDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showWarningDialog) {
                AlertDialog(
                    onDismissRequest = { showWarningDialog = false },
                    title = { Text("Network Audit Warning") },
                    text = {
                        Text(
                            "Port scanning is easily detectable by network security systems (IDS/IPS). " +
                            "Aggressive scanning can cause malfunctions or crashes in fragile Operational Technology (OT) and Industrial Control Systems (ICS), " +
                            "such as PLCs, HMIs, medical equipment, and building automation controllers. " +
                            "\n\nProceed with caution on production networks."
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                when (val type = pendingScanType) {
                                    is ScanType.Full -> viewModel.startScan(ipAddress.text, type.start, type.end)
                                    is ScanType.Common -> viewModel.startCommonPortsScan(ipAddress.text)
                                    else -> {}
                                }
                                showWarningDialog = false
                            }
                        ) {
                            Text("I Understand, Start Scan")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showWarningDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showInfoDialog) {
                AlertDialog(
                    onDismissRequest = { showInfoDialog = false },
                    title = { Text("Port Scanning Information") },
                    text = {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Text(
                                text = "Common Ports Scan",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Scans ~150 high-priority ports frequently used by common services (SSH, HTTP, Database, Remote Desktop, etc.). This is faster and less intrusive than a full 65k port scan while identifying most common exposure points.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                text = "Benefits",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "• Identifies active services and versions.\n" +
                                       "• Discovers potential entry points for security auditing.\n" +
                                       "• Helps verify firewall and access control rules.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                text = "Dangers & Risks",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "• Detection: Scans are easily flagged by IDS/IPS.\n" +
                                       "• Stability: Aggressive scanning can crash fragile Operational Technology (OT) like PLCs or medical equipment.\n" +
                                       "• Policy: Unauthorized scans may violate network policies or local regulations.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showInfoDialog = false }) {
                            Text("Close")
                        }
                    }
                )
            }

            if (showApiKeyRecommendation) {
                AlertDialog(
                    onDismissRequest = { showApiKeyRecommendation = false },
                    title = { Text("Boost Your Scan Results") },
                    text = {
                        Text(
                            "Adding a free NIST NVD API key allows RF_REAPR to fetch vulnerability data more reliably and with higher rate limits.\n\n" +
                            "Without a key, the NIST API may throttle requests, leading to missing CVE data during audits."
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showApiKeyRecommendation = false
                                onNavigateToSettings()
                            }
                        ) {
                            Text("Go to Settings")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showApiKeyRecommendation = false }) {
                            Text("Maybe Later")
                        }
                    }
                )
            }
            
            PortResultsList(
                foundPorts = foundPorts,
                ipAddress = ipAddress.text,
                isPassiveMode = isPassiveMode,
                onPortClick = onPortClick
            )
        }

        selectedPortDetails?.let { port ->
            PortDetailsDialog(
                ipAddress = ipAddress.text,
                port = port
            ) { selectedPortDetails = null }
            if (isPassiveMode) {
                Text(
                    "Passive Mode (Stealth). Scanning is inhibited.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun PortResultsList(
    foundPorts: List<OpenPort>,
    ipAddress: String,
    isPassiveMode: Boolean,
    onPortClick: (OpenPort) -> Unit
) {
    val scrollState = androidx.compose.foundation.lazy.rememberLazyListState()

    LazyColumn(
        state = scrollState,
        modifier = Modifier
            .fillMaxSize()
            .verticalScrollbar(scrollState)
    ) {
        items(
            items = foundPorts,
            key = { it.port },
            contentType = { "port" }
        ) { port ->
            AuditResultCard(
                ipAddress = ipAddress,
                port = port,
                isPassiveMode = isPassiveMode,
                onClick = { onPortClick(port) }
            )
            if (isPassiveMode) {
                Text(
                    "Passive Mode (Stealth). Scanning is inhibited.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

fun Modifier.verticalScrollbar(
    state: androidx.compose.foundation.lazy.LazyListState,
    width: androidx.compose.ui.unit.Dp = 4.dp
): Modifier = this.then(
    Modifier.drawWithContent {
        drawContent()

    val layoutInfo = state.layoutInfo
    val visibleItemsInfo = layoutInfo.visibleItemsInfo
    if (visibleItemsInfo.isEmpty()) return@drawWithContent

    val totalItemsCount = layoutInfo.totalItemsCount
    val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
    if (viewportHeight <= 0) return@drawWithContent

    val firstVisibleItem = visibleItemsInfo.first()

    val totalHeight = (viewportHeight.toFloat() / visibleItemsInfo.size) * totalItemsCount
    val scrollbarHeight = (viewportHeight.toFloat() / totalHeight) * viewportHeight
    val scrollOffset = (firstVisibleItem.index.toFloat() / totalItemsCount) * viewportHeight

    drawRect(
        color = Color.Gray.copy(alpha = 0.5f),
        topLeft = Offset(size.width - width.toPx(), scrollOffset),
        size = Size(width.toPx(), scrollbarHeight),
    )
})



@Composable
fun AuditResultCard(
    ipAddress: String,
    port: OpenPort,
    isPassiveMode: Boolean,
    onClick: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val isWeb = isWebService(port.port, port.serviceName, port.banner)
    val vulns = port.vulnerabilities
    val hasVulns = vulns.isNotEmpty()
    val allUnconfirmed = hasVulns && vulns.all { !it.isConfirmed }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = when {
            allUnconfirmed -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
            hasVulns -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        tonalElevation = if (hasVulns) 0.dp else 1.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Port: ${port.port}", 
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Service: ${port.serviceName}", 
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                if (isWeb) {
                    IconButton(
                        onClick = { 
                            val protocol = getProtocol(port.port, port.serviceName, port.banner)
                            uriHandler.openUri("$protocol://$ipAddress:${port.port}")
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Open in Browser",
                            tint = WebGold,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            if (port.banner != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                            shape = MaterialTheme.shapes.extraSmall
                        )
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Banner: ${port.banner}",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }
            
            if (hasVulns) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (allUnconfirmed) "Potential vulnerabilities detected" else "Vulnerabilities detected", 
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold, 
                    color = if (allUnconfirmed) MaterialTheme.colorScheme.error.copy(alpha = 0.7f) else MaterialTheme.colorScheme.error
                )
            }
            if (isPassiveMode) {
                Text(
                    "Passive Mode (Stealth). Scanning is inhibited.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun PortDetailsDialog(
    ipAddress: String,
    port: OpenPort,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val isWeb = isWebService(port.port, port.serviceName, port.banner)
    val description = getServiceDescription(port.port, port.serviceName)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Port ${port.port} Details") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(text = "Service: ${port.serviceName}", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Description",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(text = description, style = MaterialTheme.typography.bodyMedium)
                
                if (port.banner != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Banner (Raw Output)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = port.banner,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }

                if (port.vulnerabilities.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Vulnerabilities",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    port.vulnerabilities.forEach { vuln ->
                        val annotatedLink = buildAnnotatedString {
                            append("• ")
                            withStyle(style = SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Bold
                            )) {
                                append(vuln.cveId)
                            }
                            if (!vuln.isConfirmed) {
                                append(" (Potential)")
                            }
                            append(": ${vuln.description}")
                        }
                        Text(
                            text = annotatedLink,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.clickable {
                                uriHandler.openUri("https://cve.mitre.org/cgi-bin/cvename.cgi?name=${vuln.cveId}")
                            }
                        )
                        SeverityBadge(vuln.severity)
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    if (port.vulnerabilities.any { !it.isConfirmed }) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "* Potential vulnerabilities are matched by service name only because no version banner was detected.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (isWeb) {
                Button(
                    onClick = {
                        val protocol = getProtocol(port.port, port.serviceName, port.banner)
                        uriHandler.openUri("$protocol://$ipAddress:${port.port}")
                    }
                ) {
                    Text("Open in Browser")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

private fun isWebService(port: Int, serviceName: String, banner: String?): Boolean {
    val webPorts = listOf(
        80, 443, 444, 8000, 8008, 8080, 8081, 8443, 8880, 8888, 9000, 9090, 9443,
        3000, 3001, 5000, 5001
    )
    val isWebPort = port in webPorts
    val isWebServiceName = serviceName.contains("http", ignoreCase = true) || 
                          serviceName.contains("www", ignoreCase = true)
    
    val isWebBanner = banner?.let {
        it.contains("HTTP/", ignoreCase = true) ||
        it.contains("Server:", ignoreCase = true) ||
        it.contains("<html>", ignoreCase = true) ||
        it.contains("Content-Type:", ignoreCase = true)
    } ?: false

    return isWebPort || isWebServiceName || isWebBanner
}

private fun getProtocol(port: Int, serviceName: String, banner: String?): String {
    val httpsPorts = listOf(443, 444, 8443, 9443)
    val isHttpsPort = port in httpsPorts
    val isHttpsServiceName = serviceName.contains("https", ignoreCase = true)
    val isHttpsBanner = banner?.let {
        it.contains("SSL", ignoreCase = true) ||
        it.contains("TLS", ignoreCase = true) ||
        it.contains("HTTPS", ignoreCase = true)
    } ?: false

    return if (isHttpsPort || isHttpsServiceName || isHttpsBanner) "https" else "http"
}

private fun getServiceDescription(port: Int, serviceName: String): String {
    return when {
        port == 21 || serviceName.contains("ftp", ignoreCase = true) -> "File Transfer Protocol (FTP). Used for transferring files between a client and server. Often used for website maintenance."
        port == 22 || serviceName.contains("ssh", ignoreCase = true) -> "Secure Shell (SSH). Provides a secure encrypted connection for remote command-line access and file transfers."
        port == 23 || serviceName.contains("telnet", ignoreCase = true) -> "Telnet. An older, unencrypted protocol for remote command-line access. HIGHLY INSECURE."
        port == 25 || port == 587 || serviceName.contains("smtp", ignoreCase = true) -> "Simple Mail Transfer Protocol (SMTP). Used for sending emails between servers."
        port == 53 || serviceName.contains("dns", ignoreCase = true) -> "Domain Name System (DNS). Translates human-readable domain names (like google.com) into IP addresses."
        port == 80 || port == 8080 || serviceName.contains("http", ignoreCase = true) -> "Hypertext Transfer Protocol (HTTP). The foundation of the World Wide Web, used for loading unencrypted web pages."
        port == 443 || port == 8443 || serviceName.contains("https", ignoreCase = true) -> "Hypertext Transfer Protocol Secure (HTTPS). The secure, encrypted version of HTTP used for safe web browsing."
        port == 445 || serviceName.contains("microsoft-ds", ignoreCase = true) -> "Server Message Block (SMB). Used for file sharing and printer sharing in Windows environments. Common target for exploits like EternalBlue."
        port == 3306 || serviceName.contains("mysql", ignoreCase = true) -> "MySQL Database Service. A popular open-source relational database management system."
        port == 3389 || serviceName.contains("rdp", ignoreCase = true) -> "Remote Desktop Protocol (RDP). Allows users to remotely connect to and control a Windows computer."
        else -> "Standard network service. The exact function depends on the software listening on this port."
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

sealed class ScanType {
    data class Full(val start: Int, val end: Int) : ScanType()
    object Common : ScanType()
}
