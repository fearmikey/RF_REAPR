package com.fearmikey.rf_reapr.ui.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fearmikey.rf_reapr.domain.model.OpenPort
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.domain.service.PortCatalog
import com.fearmikey.rf_reapr.ui.theme.WebGold
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PortScannerScreen(
    viewModel: PortScannerViewModel,
    initialIp: String? = null,
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onUpdateTopology: (Map<String, List<OpenPort>>, String?) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val state by viewModel.state.collectAsStateWithLifecycle()
    val foundPorts by viewModel.foundPorts.collectAsStateWithLifecycle()
    val isPassiveMode by viewModel.isPassiveMode.collectAsStateWithLifecycle()
    val isApiKeySet by viewModel.isApiKeySet.collectAsStateWithLifecycle()

    var showWarningDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var apiKeyHintDismissed by rememberSaveable { mutableStateOf(false) }
    var selectedPortDetails by remember { mutableStateOf<OpenPort?>(null) }

    LaunchedEffect(initialIp) { viewModel.setInitialTarget(initialIp) }

    LaunchedEffect(state.phase) {
        if (state.phase == ScanPhase.DONE && foundPorts.isNotEmpty()) {
            onUpdateTopology(mapOf(state.scannedTarget to foundPorts), null)
        }
    }

    val validationError = remember(state.target, state.preset, state.customSpec) {
        if (state.target.isBlank()) null else viewModel.validate()
    }
    val canStart = !state.isBusy && !isPassiveMode && state.target.isNotBlank() && validationError == null

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("TCP Port Scanner") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (foundPorts.isNotEmpty() && !state.isBusy) {
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(viewModel.reportText()))
                            scope.launch { snackbarHostState.showSnackbar("Report copied") }
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy report")
                        }
                    }
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Information")
                    }
                }
            )
        }
    ) { padding ->
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScrollbar(listState),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isPassiveMode) {
                item {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                        Text(
                            "Passive (stealth) mode is on, so scanning is disabled. Turn it off in Settings to scan.",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            if (!isApiKeySet && !apiKeyHintDismissed) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "Optional: add a free NIST NVD API key for more reliable CVE lookups (higher rate limits).",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { apiKeyHintDismissed = true }) { Text("Dismiss") }
                                TextButton(onClick = onNavigateToSettings) { Text("Settings") }
                            }
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = state.target,
                    onValueChange = viewModel::updateTarget,
                    label = { Text("Target IP or hostname") },
                    placeholder = { Text("192.168.1.1") },
                    singleLine = true,
                    enabled = !state.isBusy,
                    isError = state.target.isNotBlank() && validationError != null,
                    supportingText = {
                        val msg = if (state.target.isNotBlank()) validationError else null
                        Text(msg ?: viewModel.estimateLabel())
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        if (canStart) {
                            keyboardController?.hide()
                            showWarningDialog = true
                        }
                    }),
                    trailingIcon = {
                        if (state.target.isNotEmpty() && !state.isBusy) {
                            IconButton(onClick = { viewModel.updateTarget("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear target")
                            }
                        }
                    }
                )
            }

            item {
                Text("Ports", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PortPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = state.preset == preset,
                            onClick = { viewModel.updatePreset(preset) },
                            enabled = !state.isBusy,
                            label = { Text(preset.label) }
                        )
                    }
                }
                if (state.preset == PortPreset.CUSTOM) {
                    OutlinedTextField(
                        value = state.customSpec,
                        onValueChange = viewModel::updateCustomSpec,
                        label = { Text("Ports and ranges") },
                        placeholder = { Text("22,80,443,8000-8100") },
                        singleLine = true,
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text("Speed", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ScanSpeed.entries.forEachIndexed { index, speed ->
                        SegmentedButton(
                            selected = state.speed == speed,
                            onClick = { viewModel.updateSpeed(speed) },
                            enabled = !state.isBusy,
                            shape = SegmentedButtonDefaults.itemShape(index, ScanSpeed.entries.size)
                        ) { Text(speed.label) }
                    }
                }
                Text(
                    when (state.speed) {
                        ScanSpeed.GENTLE -> "Few parallel connections and long timeouts. Use on fragile devices, slow links or OT/ICS networks."
                        ScanSpeed.NORMAL -> "Balanced for typical home and office LANs."
                        ScanSpeed.FAST -> "Many parallel connections and short timeouts. May miss slow hosts and is noisy for IDS."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                if (state.isBusy) {
                    OutlinedButton(onClick = viewModel::stopScan, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Stop scan")
                    }
                } else {
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            showWarningDialog = true
                        },
                        enabled = canStart,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Start scan")
                    }
                }
            }

            if (state.phase != ScanPhase.IDLE) {
                item { ScanStatusCard(state, foundPorts) }
            }

            if (foundPorts.isEmpty() && state.phase == ScanPhase.DONE) {
                item {
                    Text(
                        "No open TCP ports found. The host may be offline, firewalled, or only running services on other ports.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }

            items(items = foundPorts, key = { it.port }, contentType = { "port" }) { port ->
                AuditResultCard(
                    ipAddress = state.scannedTarget.ifBlank { state.target },
                    port = port,
                    isPassiveMode = isPassiveMode,
                    onClick = { selectedPortDetails = port }
                )
            }
        }
    }

    if (showWarningDialog) {
        AlertDialog(
            onDismissRequest = { showWarningDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Network audit warning") },
            text = {
                Text(
                    "Port scanning is easily detected by IDS/IPS. Aggressive scanning can crash fragile " +
                        "OT/ICS equipment such as PLCs, HMIs, medical devices and building controllers.\n\n" +
                        "Only scan hosts you're authorized to test. ${viewModel.estimateLabel()}."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showWarningDialog = false
                    viewModel.startScan()
                }) { Text("Start scan", maxLines = 1) }
            },
            dismissButton = {
                TextButton(onClick = { showWarningDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("About port scanning") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("How it works", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "A TCP connect scan: RF-REAPR opens a normal connection to each port. If it connects, the port is open and the app reads the service greeting (or sends an HTTP HEAD request) to identify the software. Versions found in banners are checked against the local CVE database and the NIST NVD.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Port sets", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "• Common: ~170 ports used by remote access, databases, file sharing, IoT and industrial services.\n" +
                            "• 1–1024: the well-known port range.\n" +
                            "• All: every TCP port; slow.\n" +
                            "• Custom: lists and ranges, e.g. 22,80,8000-8100.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Risks", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Text(
                        "• Detection: scans are easily flagged by IDS/IPS.\n" +
                            "• Stability: aggressive scanning can crash fragile OT equipment. Use Gentle speed.\n" +
                            "• Policy: unauthorized scans may violate network policies or local law.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showInfoDialog = false }) { Text("Close") } }
        )
    }

    selectedPortDetails?.let { port ->
        PortDetailsDialog(ipAddress = state.scannedTarget.ifBlank { state.target }, port = port) { selectedPortDetails = null }
    }
}

@Composable
private fun ScanStatusCard(state: PortScanUiState, ports: List<OpenPort>) {
    val exposed = ports.count { PortCatalog.info(it.port)?.exposureRisk?.let { r -> r >= RiskLevel.HIGH } == true }
    val vulnCount = ports.sumOf { it.vulnerabilities.size }
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val title = when (state.phase) {
                ScanPhase.SCANNING -> "Scanning ${state.scannedTarget}…"
                ScanPhase.AUDITING -> "Checking banners for known CVEs…"
                ScanPhase.DONE -> "Scan complete: ${state.scannedTarget}"
                ScanPhase.STOPPED -> "Scan stopped: ${state.scannedTarget}"
                ScanPhase.ERROR -> "Scan failed"
                ScanPhase.IDLE -> ""
            }
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            when (state.phase) {
                ScanPhase.SCANNING -> LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                ScanPhase.AUDITING -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                else -> Unit
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("Open", ports.size.toString())
                Stat("Scanned", "${(state.progress * state.portsTotal).toInt()}/${state.portsTotal}")
                Stat("Elapsed", formatDuration(state.elapsedMs))
                Stat("ETA", state.eta ?: "–")
            }
            if (exposed > 0 || vulnCount > 0) {
                Text(
                    buildString {
                        if (exposed > 0) append("$exposed high-risk service${if (exposed == 1) "" else "s"} exposed")
                        if (exposed > 0 && vulnCount > 0) append(" · ")
                        if (vulnCount > 0) append("$vulnCount CVE match${if (vulnCount == 1) "" else "es"}")
                    },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        if (viewportHeight <= 0 || visibleItemsInfo.size >= totalItemsCount) return@drawWithContent

        val firstVisibleItem = visibleItemsInfo.first()
        val totalHeight = (viewportHeight.toFloat() / visibleItemsInfo.size) * totalItemsCount
        val scrollbarHeight = (viewportHeight.toFloat() / totalHeight) * viewportHeight
        val scrollOffset = (firstVisibleItem.index.toFloat() / totalItemsCount) * viewportHeight

        drawRect(
            color = Color.Gray.copy(alpha = 0.5f),
            topLeft = Offset(size.width - width.toPx(), scrollOffset),
            size = Size(width.toPx(), scrollbarHeight),
        )
    }
)

@Composable
fun AuditResultCard(
    ipAddress: String,
    port: OpenPort,
    @Suppress("UNUSED_PARAMETER") isPassiveMode: Boolean = false,
    onClick: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val url = PortCatalog.webUrl(ipAddress, port.port, port.serviceName, port.banner)
    val info = PortCatalog.info(port.port)
    val vulns = port.vulnerabilities
    val hasVulns = vulns.isNotEmpty()
    val allUnconfirmed = hasVulns && vulns.all { !it.isConfirmed }
    val worstVuln = vulns.maxByOrNull { it.severity.ordinal }?.severity
    val exposure = info?.exposureRisk

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = when {
            hasVulns && !allUnconfirmed -> MaterialTheme.colorScheme.errorContainer
            hasVulns -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        tonalElevation = if (hasVulns) 0.dp else 1.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${port.port}/tcp",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(port.serviceName, style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (worstVuln != null) SeverityBadge(worstVuln)
                        else if (exposure != null && exposure >= RiskLevel.MEDIUM) SeverityBadge(exposure)
                    }
                }
                if (url != null) {
                    IconButton(onClick = { uriHandler.openUri(url) }) {
                        Icon(Icons.Default.Language, contentDescription = "Open in browser", tint = WebGold)
                    }
                }
            }

            port.banner?.let { banner ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = banner,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f), MaterialTheme.shapes.extraSmall)
                        .padding(8.dp)
                )
            }

            info?.exposureNote?.let { note ->
                Spacer(Modifier.height(4.dp))
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (hasVulns) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${vulns.size} ${if (allUnconfirmed) "potential " else ""}CVE match${if (vulns.size == 1) "" else "es"}. Tap for details.",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
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
    val clipboard = LocalClipboardManager.current
    val url = PortCatalog.webUrl(ipAddress, port.port, port.serviceName, port.banner)
    val info = PortCatalog.info(port.port)
    val description = info?.description ?: getServiceDescription(port.port, port.serviceName)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Port ${port.port} · ${port.serviceName}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Description", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(text = description, style = MaterialTheme.typography.bodyMedium)

                info?.exposureNote?.let { note ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Why it matters", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                    Text(note, style = MaterialTheme.typography.bodyMedium)
                    info.exposureRisk?.let { SeverityBadge(it) }
                }

                port.banner?.let { banner ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Banner",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { clipboard.setText(AnnotatedString(banner)) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy banner", modifier = Modifier.size(18.dp))
                        }
                    }
                    Text(text = banner, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }

                if (port.vulnerabilities.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Vulnerabilities", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                    port.vulnerabilities.sortedByDescending { it.severity.ordinal }.forEach { vuln ->
                        val annotatedLink = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    color = MaterialTheme.colorScheme.primary,
                                    textDecoration = TextDecoration.Underline,
                                    fontWeight = FontWeight.Bold
                                )
                            ) { append(vuln.cveId) }
                            if (!vuln.isConfirmed) append(" (potential)")
                            append(": ${vuln.description}")
                        }
                        Spacer(Modifier.height(6.dp))
                        SeverityBadge(vuln.severity)
                        Text(
                            text = annotatedLink,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable {
                                uriHandler.openUri("https://nvd.nist.gov/vuln/detail/${vuln.cveId}")
                            }
                        )
                    }

                    if (port.vulnerabilities.any { !it.isConfirmed }) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "* Potential matches are based on the product name without a confirmed version. Verify manually.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (url != null) {
                Button(onClick = { uriHandler.openUri(url) }) { Text("Open in browser") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

private fun getServiceDescription(port: Int, serviceName: String): String = when {
    serviceName.contains("http", ignoreCase = true) -> "Web server. Open it in a browser to see what's being served."
    else -> "Port $port accepted a TCP connection. The exact service depends on the software listening here; check the banner if one was captured."
}

@Composable
fun SeverityBadge(severity: RiskLevel) {
    val color = when (severity) {
        RiskLevel.LOW -> Color(0xFF4CAF50)
        RiskLevel.MEDIUM -> Color(0xFFFFC107)
        RiskLevel.HIGH -> Color(0xFFFF9800)
        RiskLevel.CRITICAL -> Color(0xFFF44336)
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
