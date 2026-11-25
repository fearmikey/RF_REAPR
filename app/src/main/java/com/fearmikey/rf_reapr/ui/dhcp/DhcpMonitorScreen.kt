package com.fearmikey.rf_reapr.ui.dhcp

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.VerifiedUser
import com.fearmikey.rf_reapr.domain.service.DeviceStanding
import com.fearmikey.rf_reapr.domain.service.KnownDeviceMatcher
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fearmikey.rf_reapr.domain.model.MonitoredDevice
import com.fearmikey.rf_reapr.domain.model.NetworkEvent
import com.fearmikey.rf_reapr.domain.model.NetworkEventType
import com.fearmikey.rf_reapr.domain.repository.DhcpMonitorRepository.MonitoringStatus
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

private enum class DeviceFilter(val label: String) { ALL("All"), NEW("New"), ONLINE("Online"), OFFLINE("Offline") }

private val OnlineColor = Color(0xFF4CAF50)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DhcpMonitorScreen(
    viewModel: DhcpMonitorViewModel,
    onBack: () -> Unit,
    onOpenKnownDevices: () -> Unit = {},
    onPortScan: (ip: String) -> Unit
) {
    val knownMacs by viewModel.knownMacs.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val isMonitoring by viewModel.isMonitoring.collectAsStateWithLifecycle()
    val isPassiveMode by viewModel.isPassiveMode.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val interval by viewModel.interval.collectAsStateWithLifecycle()
    val notifyOnNewDevice by viewModel.notifyOnNewDevice.collectAsStateWithLifecycle()

    var showInfoDialog by remember { mutableStateOf(false) }
    var showWarningDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }
    var showEvents by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(DeviceFilter.ALL) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it, withDismissAction = true) }
    }

    val onlineCount = devices.count { it.isOnline }
    val newCount = devices.count { it.isNew }
    val filteredDevices = remember(devices, filter) {
        when (filter) {
            DeviceFilter.ALL -> devices
            DeviceFilter.NEW -> devices.filter { it.isNew }
            DeviceFilter.ONLINE -> devices.filter { it.isOnline }
            DeviceFilter.OFFLINE -> devices.filter { !it.isOnline }
        }.sortedByDescending { it.isNew } // surface unacknowledged devices first
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Network Monitor")
                        Text(
                            text = "New-device & DHCP change alerts",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (newCount > 0) {
                        IconButton(onClick = viewModel::clearNotifications) {
                            BadgedBox(badge = { Badge { Text("$newCount") } }) {
                                Icon(Icons.Default.DoneAll, contentDescription = "Acknowledge new devices")
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                            DropdownMenuItem(
                                text = { Text("Alert notifications") },
                                trailingIcon = { Checkbox(checked = notifyOnNewDevice, onCheckedChange = null) },
                                onClick = { viewModel.setNotifyOnNewDevice(!notifyOnNewDevice) }
                            )
                            DropdownMenuItem(
                                text = { Text("Mark all listed devices as known") },
                                leadingIcon = { Icon(Icons.Default.PlaylistAddCheck, contentDescription = null) },
                                enabled = devices.isNotEmpty(),
                                onClick = {
                                    showOverflow = false
                                    viewModel.markAllKnown()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Known devices (${knownMacs.size})") },
                                leadingIcon = { Icon(Icons.Default.VerifiedUser, contentDescription = null) },
                                onClick = {
                                    showOverflow = false
                                    onOpenKnownDevices()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Reset baseline") },
                                leadingIcon = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
                                enabled = devices.isNotEmpty(),
                                onClick = {
                                    showOverflow = false
                                    showResetDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("About this tool") },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                onClick = {
                                    showOverflow = false
                                    showInfoDialog = true
                                }
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                                "Passive Mode (Stealth). Network monitoring inhibited.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            item(key = "status") {
                MonitorStatusCard(
                    status = status,
                    isMonitoring = isMonitoring,
                    isPassiveMode = isPassiveMode,
                    onlineCount = onlineCount,
                    offlineCount = devices.size - onlineCount,
                    newCount = newCount,
                    sweepCount = stats.sweepCount,
                    lastSweepAt = stats.lastSweepAt,
                    interval = interval,
                    onIntervalChange = viewModel::setInterval,
                    onToggle = { enable ->
                        if (enable) showWarningDialog = true else viewModel.stopMonitoring()
                    }
                )
            }

            item(key = "background_watch") {
                BackgroundWatchCard(isPassiveMode = isPassiveMode)
            }

            item(key = "tabs") {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !showEvents,
                        onClick = { showEvents = false },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text("Devices (${devices.size})") }
                    SegmentedButton(
                        selected = showEvents,
                        onClick = { showEvents = true },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text("Events (${events.size})") }
                }
            }

            if (!showEvents) {
                if (devices.isNotEmpty()) {
                    item(key = "filters") {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DeviceFilter.entries.forEach { f ->
                                val count = when (f) {
                                    DeviceFilter.ALL -> devices.size
                                    DeviceFilter.NEW -> newCount
                                    DeviceFilter.ONLINE -> onlineCount
                                    DeviceFilter.OFFLINE -> devices.size - onlineCount
                                }
                                FilterChip(
                                    selected = filter == f,
                                    onClick = { filter = f },
                                    label = { Text("${f.label} ($count)") }
                                )
                            }
                        }
                    }
                }
                if (filteredDevices.isEmpty()) {
                    item(key = "devices_empty") {
                        EmptyMessage(
                            when {
                                devices.isEmpty() && isMonitoring -> "Running the baseline sweep. Devices seen now are treated as known."
                                devices.isEmpty() -> "Start monitoring to record a baseline of devices on this network. You'll be alerted when anything new appears."
                                else -> "No devices match this filter."
                            }
                        )
                    }
                }
                items(filteredDevices, key = { it.ipAddress }) { device ->
                    DeviceMonitorCard(
                        device = device,
                        isPassiveMode = isPassiveMode,
                        // Standing is only shown once the known-device list is in use.
                        standing = if (knownMacs.isEmpty()) null else KnownDeviceMatcher.standing(device.macAddress, knownMacs),
                        onMarkKnown = { viewModel.markKnown(device) },
                        onUnmarkKnown = { viewModel.unmarkKnown(device) },
                        onPortScan = onPortScan
                    )
                }
            } else {
                if (events.isEmpty()) {
                    item(key = "events_empty") { EmptyMessage("No events yet. Joins, departures and MAC changes will appear here.") }
                }
                items(events, key = { "${it.timestamp}_${it.type}_${it.ipAddress}" }) { event ->
                    EventRow(event)
                }
            }
        }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("Network Monitoring Information") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("How it Works", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = "The first sweep records a baseline of active devices. Each later sweep is compared " +
                            "against it: new devices, devices going offline (missed on 2 consecutive sweeps), " +
                            "devices returning, and an IP answering with a different MAC are all logged as events.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Benefits", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "• Rogue Device Detection: flags unauthorized hardware, with an optional notification.\n" +
                            "• Asset Tracking: first/last seen times and online state for every host.\n" +
                            "• Spoofing hints: a MAC change on a known IP can indicate ARP spoofing or DHCP churn.\n" +
                            "• Keeps running while the app is in the background (shown in the task notification).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Limitations", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Android 10+ restricts access to the ARP table, so MAC addresses (and vendor names) " +
                            "may be unavailable on unrooted devices. Hosts that block all probes won't be seen.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Risks & Dangers", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Text(
                        text = "• IDS Flagging: Frequent sweeps can be flagged as 'horizontal scanning' by security systems.\n" +
                            "• OT Disruption: Periodic connection attempts can overwhelm legacy PLCs or medical equipment.\n" +
                            "• Network Load: Shorter intervals generate steady ARP and TCP traffic; prefer 5m+ on sensitive networks.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showInfoDialog = false }) { Text("Close") } }
        )
    }

    if (showWarningDialog) {
        AlertDialog(
            onDismissRequest = { showWarningDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Enable Active Monitoring?") },
            text = {
                Text(
                    "Enabling active monitoring will sweep the subnet every ${interval.label}. " +
                        "This activity is easily detectable by network security (IDS/IPS) and can disrupt fragile Operational Technology (OT) like industrial controllers or medical devices."
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.startMonitoring()
                    showWarningDialog = false
                }) { Text("Enable Monitoring", maxLines = 1) }
            },
            dismissButton = { TextButton(onClick = { showWarningDialog = false }) { Text("Cancel") } }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Baseline?") },
            text = { Text("Forget all ${devices.size} recorded devices and the event history. The next sweep will be treated as the new known-good baseline.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetBaseline()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun MonitorStatusCard(
    status: MonitoringStatus,
    isMonitoring: Boolean,
    isPassiveMode: Boolean,
    onlineCount: Int,
    offlineCount: Int,
    newCount: Int,
    sweepCount: Int,
    lastSweepAt: Long?,
    interval: MonitorInterval,
    onIntervalChange: (MonitorInterval) -> Unit,
    onToggle: (Boolean) -> Unit
) {
    // Ticks once a second while waiting so the countdown stays live.
    val now by produceState(System.currentTimeMillis(), status) {
        while (status is MonitoringStatus.Waiting) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Radar,
                    contentDescription = null,
                    tint = if (isMonitoring) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isMonitoring) "Monitoring active" else "Monitor off",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when (status) {
                            is MonitoringStatus.Scanning -> "Sweeping subnet… ${(status.progress * 100).toInt()}%"
                            is MonitoringStatus.Waiting -> {
                                val secs = ((status.nextSweepAt - now) / 1000).coerceAtLeast(0)
                                "Next sweep in %d:%02d".format(secs / 60, secs % 60)
                            }
                            is MonitoringStatus.Error -> "Last sweep failed: ${status.message}"
                            else -> if (isPassiveMode) "Disabled in Passive Mode" else "Sweeps every ${interval.label} once enabled"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (status is MonitoringStatus.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isMonitoring,
                    onCheckedChange = onToggle,
                    enabled = !isPassiveMode || isMonitoring
                )
            }

            if (status is MonitoringStatus.Scanning) {
                LinearProgressIndicator(progress = { status.progress }, modifier = Modifier.fillMaxWidth())
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatItem("Online", onlineCount.toString(), OnlineColor)
                StatItem("Offline", offlineCount.toString(), MaterialTheme.colorScheme.outline)
                StatItem("New", newCount.toString(), if (newCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                StatItem("Sweeps", sweepCount.toString(), MaterialTheme.colorScheme.onSurface)
            }
            lastSweepAt?.let {
                Text(
                    text = "Last sweep ${DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(it))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Interval", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MonitorInterval.entries.forEach { option ->
                        FilterChip(
                            selected = interval == option,
                            onClick = { onIntervalChange(option) },
                            label = { Text(option.label) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceMonitorCard(
    device: MonitoredDevice,
    isPassiveMode: Boolean,
    standing: DeviceStanding? = null,
    onMarkKnown: () -> Unit = {},
    onUnmarkKnown: () -> Unit = {},
    onPortScan: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    val copy: (String, String) -> Unit = { label, value ->
        clipboard.setText(AnnotatedString(value))
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }

    Box {
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = { menuExpanded = true },
            colors = if (device.isNew) {
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
            } else {
                CardDefaults.cardColors()
            }
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(12.dp)
                        .background(if (device.isOnline) OnlineColor else MaterialTheme.colorScheme.outline, CircleShape)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = device.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (device.isNew) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(text = device.ipAddress, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                    device.macAddress?.let {
                        Text(
                            text = it + (device.manufacturer?.let { v -> " · $v" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "First seen ${relative(device.firstSeen)} · " +
                            if (device.isOnline) "last seen ${relative(device.lastSeen)}" else "offline since ${relative(device.lastSeen)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (device.isNew) Tag("NEW DEVICE", MaterialTheme.colorScheme.error)
                        when (standing) {
                            DeviceStanding.KNOWN -> Tag("KNOWN", MaterialTheme.colorScheme.primary)
                            DeviceStanding.UNKNOWN -> Tag("NOT ON KNOWN LIST", MaterialTheme.colorScheme.error)
                            DeviceStanding.UNIDENTIFIED, null -> Unit
                        }
                        if (device.isRandomizedMac) Tag("RANDOMIZED MAC", MaterialTheme.colorScheme.tertiary)
                        if (!device.isOnline) Tag("OFFLINE", MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text("Port scan") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ManageSearch, contentDescription = null) },
                enabled = !isPassiveMode,
                onClick = {
                    menuExpanded = false
                    onPortScan(device.ipAddress)
                }
            )
            if (device.macAddress != null) {
                if (standing == DeviceStanding.KNOWN) {
                    DropdownMenuItem(
                        text = { Text("Remove from known devices") },
                        leadingIcon = { Icon(Icons.Default.RemoveCircleOutline, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onUnmarkKnown()
                        }
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("Add to known devices") },
                        leadingIcon = { Icon(Icons.Default.VerifiedUser, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onMarkKnown()
                        }
                    )
                }
            }
            DropdownMenuItem(
                text = { Text("Copy IP") },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    copy("IP", device.ipAddress)
                }
            )
            device.macAddress?.let { mac ->
                DropdownMenuItem(
                    text = { Text("Copy MAC") },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        copy("MAC", mac)
                    }
                )
            }
        }
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Text(text = text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
}

@Composable
private fun EventRow(event: NetworkEvent) {
    val (icon, tint) = event.type.visual()
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = event.type.name, tint = tint)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(event.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                event.detail?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(event.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NetworkEventType.visual(): Pair<ImageVector, Color> = when (this) {
    NetworkEventType.BASELINE -> Icons.Default.Flag to MaterialTheme.colorScheme.primary
    NetworkEventType.JOINED -> Icons.AutoMirrored.Filled.Login to MaterialTheme.colorScheme.error
    NetworkEventType.LEFT -> Icons.AutoMirrored.Filled.Logout to MaterialTheme.colorScheme.outline
    NetworkEventType.RETURNED -> Icons.AutoMirrored.Filled.Login to OnlineColor
    NetworkEventType.MAC_CHANGED -> Icons.AutoMirrored.Filled.CompareArrows to MaterialTheme.colorScheme.tertiary
}

@Composable
private fun EmptyMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 24.dp)
    )
}

private fun relative(timestamp: Long): String =
    DateUtils.getRelativeTimeSpanString(timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
