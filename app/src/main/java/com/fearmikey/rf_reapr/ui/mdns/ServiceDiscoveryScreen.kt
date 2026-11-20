package com.fearmikey.rf_reapr.ui.mdns

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fearmikey.rf_reapr.domain.repository.DiscoveredService
import com.fearmikey.rf_reapr.domain.service.MdnsHost
import com.fearmikey.rf_reapr.domain.service.MdnsServiceCatalog
import com.fearmikey.rf_reapr.domain.service.ServiceCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceDiscoveryScreen(
    viewModel: ServiceDiscoveryViewModel,
    onPortScan: (ip: String) -> Unit = {},
    onBackClick: () -> Unit
) {
    val services by viewModel.services.collectAsStateWithLifecycle()
    val isPassiveMode by viewModel.isPassiveMode.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val secondsRemaining by viewModel.secondsRemaining.collectAsStateWithLifecycle()
    val hasCompletedScan by viewModel.hasCompletedScan.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var groupByDevice by rememberSaveable { mutableStateOf(true) }
    var categoryFilter by rememberSaveable { mutableStateOf<ServiceCategory?>(null) }
    var showInfo by remember { mutableStateOf(false) }

    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val copy: (String, String) -> Unit = { label, value ->
        clipboard.setText(AnnotatedString(value))
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }
    val openUrl: (String) -> Unit = { url ->
        try {
            uriHandler.openUri(url)
        } catch (_: Exception) {
            Toast.makeText(context, "No browser available", Toast.LENGTH_SHORT).show()
        }
    }

    val filtered = remember(services, query, categoryFilter) {
        services.filter { s ->
            val info = MdnsServiceCatalog.describe(s.type)
            (categoryFilter == null || info.category == categoryFilter) && matchesQuery(s, info.label, query)
        }
    }
    val hosts = remember(filtered) { MdnsServiceCatalog.groupByHost(filtered) }
    val allHostCount = remember(services) { services.map { it.host }.distinct().size }
    val presentCategories = remember(services) {
        services.map { MdnsServiceCatalog.describe(it.type).category }.distinct().sortedBy { it.ordinal }
    }
    val flaggedCount = remember(filtered) { filtered.count { MdnsServiceCatalog.describe(it.type).securityNote != null } }

    LaunchedEffect(Unit) { viewModel.startIfIdle() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Service Discovery")
                        if (services.isNotEmpty()) {
                            Text(
                                text = "${services.size} services · $allHostCount devices",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showInfo = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Information")
                    }
                    if (isScanning) {
                        IconButton(onClick = viewModel::stopDiscovery) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop discovery")
                        }
                    } else {
                        IconButton(onClick = viewModel::startDiscovery, enabled = !isPassiveMode) {
                            Icon(Icons.Default.Refresh, contentDescription = "Scan again")
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isPassiveMode) {
                item(key = "passive") {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                "Passive Mode (Stealth). Active service discovery is inhibited to avoid detection.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            if (isScanning) {
                item(key = "scanning") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = "Listening for mDNS/Bonjour announcements…" +
                                (secondsRemaining?.let { " auto-stops in ${it}s" } ?: ""),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (services.isNotEmpty()) {
                item(key = "controls") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Name, IP, service, TXT value…") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { query = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = groupByDevice,
                                onClick = { groupByDevice = true },
                                shape = SegmentedButtonDefaults.itemShape(0, 2)
                            ) { Text("By device") }
                            SegmentedButton(
                                selected = !groupByDevice,
                                onClick = { groupByDevice = false },
                                shape = SegmentedButtonDefaults.itemShape(1, 2)
                            ) { Text("By service") }
                        }
                        if (presentCategories.size > 1) {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                presentCategories.forEach { category ->
                                    FilterChip(
                                        selected = categoryFilter == category,
                                        onClick = { categoryFilter = if (categoryFilter == category) null else category },
                                        label = { Text(category.label) },
                                        leadingIcon = {
                                            Icon(category.icon(), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
                                        }
                                    )
                                }
                            }
                        }
                        if (flaggedCount > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "$flaggedCount service${if (flaggedCount == 1) "" else "s"} worth reviewing",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            when {
                services.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        title = when {
                            isPassiveMode -> "Discovery disabled in Passive Mode"
                            isScanning -> "Searching…"
                            hasCompletedScan -> "No services found"
                            else -> "No services discovered yet"
                        },
                        subtitle = when {
                            isScanning -> "Devices announce services periodically; results appear as they're resolved."
                            hasCompletedScan -> "Some networks (guest/isolated Wi-Fi) block multicast traffic between clients."
                            else -> "Tap refresh to browse for printers, casting, smart home, and file sharing services."
                        }
                    )
                }
                filtered.isEmpty() -> item(key = "no_matches") {
                    EmptyState(title = "No services match", subtitle = "Try a different search or category.")
                }
                groupByDevice -> items(hosts, key = { "host_${it.address}" }) { host ->
                    HostCard(
                        host = host,
                        isPassiveMode = isPassiveMode,
                        onCopy = copy,
                        onOpenUrl = openUrl,
                        onPortScan = onPortScan
                    )
                }
                else -> {
                    val byType = filtered.groupBy { MdnsServiceCatalog.normalizeType(it.type) }
                        .toList()
                        .sortedWith(compareBy({ MdnsServiceCatalog.describe(it.first).category.ordinal }, { it.first }))
                    items(byType, key = { "type_${it.first}" }) { (type, instances) ->
                        ServiceTypeCard(type = type, instances = instances, onCopy = copy, onOpenUrl = openUrl)
                    }
                }
            }
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("Service Discovery") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Browses multicast DNS (mDNS / Bonjour / DNS-SD) on the local link. It first asks the " +
                            "network which service types exist, then resolves each instance to an address, port " +
                            "and TXT metadata (model names, firmware, paths).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Why it matters", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Advertised services reveal device inventory without port scanning: printers, cameras, " +
                            "NAS shares, remote desktops, developer debugging endpoints and IoT hubs. Flagged " +
                            "entries are commonly misconfigured or unauthenticated.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Noise level",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "Low. mDNS queries are normal multicast traffic, but they are visible to every device on " +
                            "the segment. Browsing stops automatically after ${ServiceDiscoveryViewModel.SCAN_DURATION_SECONDS} seconds.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Close") } }
        )
    }
}

private fun matchesQuery(service: DiscoveredService, typeLabel: String, query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return service.name.contains(q, true) ||
        service.type.contains(q, true) ||
        typeLabel.contains(q, true) ||
        service.addresses.any { it.contains(q, true) } ||
        service.port.toString() == q ||
        service.attributes.values.any { it.contains(q, true) }
}

private fun ServiceCategory.icon(): ImageVector = when (this) {
    ServiceCategory.REMOTE_ACCESS -> Icons.Default.Terminal
    ServiceCategory.FILE_SHARING -> Icons.Default.Folder
    ServiceCategory.PRINTING -> Icons.Default.Print
    ServiceCategory.WEB -> Icons.Default.Language
    ServiceCategory.MEDIA -> Icons.Default.Cast
    ServiceCategory.SMART_HOME -> Icons.Default.Home
    ServiceCategory.APPLE -> Icons.Default.PhoneIphone
    ServiceCategory.DEVICE_INFO -> Icons.Default.Info
    ServiceCategory.OTHER -> Icons.Default.Devices
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HostCard(
    host: MdnsHost,
    isPassiveMode: Boolean,
    onCopy: (String, String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onPortScan: (String) -> Unit
) {
    var expanded by rememberSaveable(host.address) { mutableStateOf(false) }
    val notes = host.securityNotes
    val webUrl = remember(host) { host.services.firstNotNullOfOrNull { MdnsServiceCatalog.browserUrl(it) } }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    host.primaryCategory.icon(),
                    contentDescription = host.primaryCategory.label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = host.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    host.address?.let { address ->
                        Text(
                            text = address,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.clickable(onClickLabel = "Copy IP") { onCopy("IP", address) }
                        )
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Hide details" else "Show TXT records"
                    )
                }
            }

            // Compact service list
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                host.services.forEach { s ->
                    val info = MdnsServiceCatalog.describe(s.type)
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (info.securityNote != null) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Text(
                            text = "${info.label} :${s.port}",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            notes.forEach { (service, note) ->
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${MdnsServiceCatalog.describe(service.type).label}: $note",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider()
                    host.services.forEach { s -> ServiceDetail(s, onCopy) }
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                host.address?.let { address ->
                    AssistChip(
                        onClick = { onPortScan(address) },
                        enabled = !isPassiveMode,
                        label = { Text("Port Scan") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ManageSearch, null, Modifier.size(AssistChipDefaults.IconSize)) }
                    )
                }
                webUrl?.let { url ->
                    AssistChip(
                        onClick = { onOpenUrl(url) },
                        label = { Text("Open Web UI") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(AssistChipDefaults.IconSize)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ServiceDetail(service: DiscoveredService, onCopy: (String, String) -> Unit) {
    val info = MdnsServiceCatalog.describe(service.type)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = service.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(
            text = "${info.label} · ${service.type} · port ${service.port}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (service.addresses.size > 1) {
            Text(
                text = service.addresses.joinToString(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }
        service.attributes.toSortedMap().forEach { (key, value) ->
            Text(
                text = if (value.isEmpty()) key else "$key = $value",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clickable(onClickLabel = "Copy TXT value") { onCopy(key, value.ifEmpty { key }) }
            )
        }
    }
}

@Composable
private fun ServiceTypeCard(
    type: String,
    instances: List<DiscoveredService>,
    onCopy: (String, String) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val info = MdnsServiceCatalog.describe(type)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(info.category.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(info.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(type, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                }
                Badge { Text("${instances.size}") }
            }
            info.securityNote?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            instances.sortedBy { it.name.lowercase() }.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(s.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = "${s.host ?: "unresolved"}:${s.port}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    MdnsServiceCatalog.browserUrl(s)?.let { url ->
                        IconButton(onClick = { onOpenUrl(url) }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open ${s.name}")
                        }
                    }
                    s.host?.let { h ->
                        IconButton(onClick = { onCopy("Address", "$h:${s.port}") }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy address")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Devices, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
