package com.fearmikey.rf_reapr.ui.network

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fearmikey.rf_reapr.domain.service.CaptureMode
import com.fearmikey.rf_reapr.domain.service.CaptureState
import com.fearmikey.rf_reapr.domain.service.IperfResultParser
import com.fearmikey.rf_reapr.ui.theme.PhysicalRed
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PacketCaptureScreen(
    onBack: () -> Unit,
    onOpenCaptures: () -> Unit = {},
    viewModel: PacketCaptureViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.captureState.collectAsStateWithLifecycle()
    val isCapturing = state.isRunning
    val isRootMode by viewModel.isRootMode.collectAsStateWithLifecycle()
    val hasRoot by viewModel.hasRoot.collectAsStateWithLifecycle()
    val hasTcpdump by viewModel.hasTcpdump.collectAsStateWithLifecycle()
    val isCheckingRoot by viewModel.isCheckingRoot.collectAsStateWithLifecycle()
    val rootFilter by viewModel.rootFilter.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var showRootWarningDialog by remember { mutableStateOf(value = false) }

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startStandardCapture()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Packet Capture") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenCaptures) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Saved captures")
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            errorMessage?.let { message ->
                item(key = "error") {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(message, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                            TextButton(onClick = { viewModel.clearError() }) {
                                Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            }

            item(key = "status") {
                CaptureStatusCard(
                    state = state,
                    isRootMode = isRootMode,
                    rootFilterValid = viewModel.isRootFilterValid,
                    onStart = {
                        if (isRootMode) {
                            viewModel.startRootCapture()
                        } else {
                            val vpnIntent = VpnService.prepare(context)
                            if (vpnIntent != null) vpnLauncher.launch(vpnIntent) else viewModel.startStandardCapture()
                        }
                    },
                    onStop = viewModel::stopCapture
                )
            }

            if (state.packets > 0 || state.dnsQueries.isNotEmpty()) {
                item(key = "breakdown") { TrafficBreakdown(state) }
            }

            // Mode Selection
            item(key = "mode") {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Capture Mode", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("Standard Capture (No Root)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            RadioButton(
                                selected = !isRootMode,
                                onClick = { viewModel.toggleMode(false) },
                                enabled = !isCapturing
                            )
                        }
                        Text(
                            "Records outbound IP packets (IPv4 + IPv6) from other apps via Android's VpnService, " +
                                "with live protocol, destination and DNS-query stats.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!isRootMode) {
                            Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Packets are recorded but not forwarded, so other apps lose connectivity while " +
                                        "capturing and only their outgoing requests (DNS lookups, connection attempts) are seen. " +
                                        "RF-REAPR itself stays online.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Comprehensive Capture (Root)",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (hasRoot || isCheckingRoot) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                            )
                            RadioButton(
                                selected = isRootMode,
                                onClick = { if (hasRoot && hasTcpdump) showRootWarningDialog = true },
                                enabled = !isCapturing && hasRoot && hasTcpdump
                            )
                        }
                        Text(
                            "Captures full two-way traffic on all interfaces using tcpdump. Requires a rooted device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        when {
                            isCheckingRoot -> Text("Checking for root…", style = MaterialTheme.typography.labelSmall)
                            !hasRoot -> Text("Root access not detected on this device.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            !hasTcpdump -> Text("tcpdump binary missing. Root capture unavailable.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                        if (isRootMode) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = rootFilter,
                                onValueChange = viewModel::setRootFilter,
                                label = { Text("Capture filter (BPF, optional)") },
                                placeholder = { Text("e.g. port 53 or host 192.168.1.10") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = !isCapturing,
                                isError = !viewModel.isRootFilterValid,
                                supportingText = if (!viewModel.isRootFilterValid) {
                                    { Text("Only letters, digits, spaces and . : / ( ) ! & | = < > [ ] - are allowed") }
                                } else null,
                                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false)
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("port 53", "tcp port 443", "not port 22", "icmp", "arp").forEach { preset ->
                                    SuggestionChip(onClick = { viewModel.setRootFilter(preset) }, label = { Text(preset) }, enabled = !isCapturing)
                                }
                            }
                        }
                    }
                }
            }

            item(key = "info") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                "Captures are saved as .pcap files. Export them from Saved captures to analyze in Wireshark. " +
                                    "A capture keeps running if you leave this screen; stop it here or from its notification.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            TextButton(onClick = onOpenCaptures, contentPadding = PaddingValues(0.dp)) { Text("Open saved captures") }
                        }
                    }
                }
            }
        }
    }

    if (showRootWarningDialog) {
        AlertDialog(
            onDismissRequest = { showRootWarningDialog = false },
            title = { Text("Root Access Required") },
            text = {
                Text(
                    "Comprehensive mode executes 'su' to run tcpdump directly on the network interfaces. " +
                        "Your device will prompt you to grant root permissions to RF_REAPR.\n\nProceed?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.toggleMode(true)
                        showRootWarningDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PhysicalRed)
                ) { Text("Enable Root Mode") }
            },
            dismissButton = { TextButton(onClick = { showRootWarningDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun CaptureStatusCard(
    state: CaptureState,
    isRootMode: Boolean,
    rootFilterValid: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val isCapturing = state.isRunning
    val now by produceState(System.currentTimeMillis(), isCapturing) {
        while (isCapturing) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val elapsedSec = state.startedAt?.let { ((if (isCapturing) now else System.currentTimeMillis()) - it) / 1000 }

    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.dp)
                        .background(if (isCapturing) PhysicalRed else MaterialTheme.colorScheme.outline, CircleShape)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isCapturing) "Capture in progress" else if (state.startedAt != null) "Last capture" else "Ready to capture",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (isCapturing) PhysicalRed else MaterialTheme.colorScheme.onSurface
                    )
                    state.mode?.let {
                        Text(
                            text = if (it == CaptureMode.ROOT_TCPDUMP) "Root / tcpdump" else "Standard / VPN",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (isCapturing && elapsedSec != null) {
                    Text(
                        "%d:%02d".format(elapsedSec / 60, elapsedSec % 60),
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            if (state.startedAt != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    if (state.mode == CaptureMode.STANDARD_VPN) {
                        StatValue("Packets", "%,d".format(state.packets))
                        StatValue("Data", IperfResultParser.formatBytes(state.bytes))
                    }
                    state.fileSizeBytes?.let { StatValue("File", IperfResultParser.formatBytes(it)) }
                }
                state.filePath?.let {
                    Text(
                        text = it.substringAfterLast('/'),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = onStart,
                    enabled = !isCapturing && (!isRootMode || rootFilterValid),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start")
                }
                FilledTonalButton(
                    onClick = onStop,
                    enabled = isCapturing,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isCapturing) PhysicalRed else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isCapturing) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Stop")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrafficBreakdown(state: CaptureState) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.protocolCounts.isNotEmpty()) {
                Text("Protocols", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.protocolCounts.entries.sortedByDescending { it.value }.forEach { (proto, count) ->
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                            Text(
                                "${proto.label} %,d".format(count),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
            if (state.topDestinations.isNotEmpty()) {
                Text("Top destinations", style = MaterialTheme.typography.titleSmall)
                state.topDestinations.forEach { (dest, count) ->
                    Row {
                        Text(dest, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("%,d".format(count), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (state.dnsQueries.isNotEmpty()) {
                Text("Domains looked up (${state.dnsQueries.size})", style = MaterialTheme.typography.titleSmall)
                state.dnsQueries.take(15).forEach { name ->
                    Text(name, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (state.dnsQueries.size > 15) {
                    Text("+${state.dnsQueries.size - 15} more in the pcap", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StatValue(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
