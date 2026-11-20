package com.fearmikey.rf_reapr.ui.iperf

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fearmikey.rf_reapr.domain.service.HostValidator
import com.fearmikey.rf_reapr.domain.service.IperfResultParser
import com.fearmikey.rf_reapr.domain.service.IperfSummary
import com.fearmikey.rf_reapr.iperf.IperfConfig
import com.fearmikey.rf_reapr.ui.components.MiniLineChart

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IperfScreen(
    viewModel: IperfViewModel,
    onBack: () -> Unit
) {
    val interfaces by viewModel.interfaces.collectAsStateWithLifecycle()
    val selectedInterface by viewModel.selectedInterface.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val testOutput by viewModel.testOutput.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
    val elapsed by viewModel.elapsedSeconds.collectAsStateWithLifecycle()

    var showRaw by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copy: (String) -> Unit = {
        clipboard.setText(AnnotatedString(it))
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }
    val hostValid = config.isServerMode || HostValidator.normalize(config.serverHost) != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("iPerf Tester") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "mode") {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !config.isServerMode,
                        onClick = { viewModel.updateConfig { it.copy(isServerMode = false) } },
                        enabled = !isRunning,
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text("Client") }
                    SegmentedButton(
                        selected = config.isServerMode,
                        onClick = { viewModel.updateConfig { it.copy(isServerMode = true) } },
                        enabled = !isRunning,
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text("Server") }
                }
            }

            if (!config.isServerMode) {
                item(key = "client_target") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = config.serverHost,
                            onValueChange = { v -> viewModel.updateConfig { it.copy(serverHost = v) } },
                            label = { Text("iperf3 server") },
                            placeholder = { Text("192.168.1.10") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            enabled = !isRunning,
                            isError = config.serverHost.isNotBlank() && !hostValid,
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false)
                        )
                        PortField(config, isRunning, viewModel)
                    }
                }
                item(key = "client_options") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = !config.reverse,
                                onClick = { viewModel.updateConfig { it.copy(reverse = false) } },
                                enabled = !isRunning,
                                shape = SegmentedButtonDefaults.itemShape(0, 2)
                            ) { Text("Upload") }
                            SegmentedButton(
                                selected = config.reverse,
                                onClick = { viewModel.updateConfig { it.copy(reverse = true) } },
                                enabled = !isRunning,
                                shape = SegmentedButtonDefaults.itemShape(1, 2)
                            ) { Text("Download (-R)") }
                        }
                        ChipRow("Protocol") {
                            FilterChip(selected = !config.udp, onClick = { viewModel.updateConfig { it.copy(udp = false) } }, label = { Text("TCP") }, enabled = !isRunning)
                            FilterChip(selected = config.udp, onClick = { viewModel.updateConfig { it.copy(udp = true) } }, label = { Text("UDP") }, enabled = !isRunning)
                        }
                        if (config.udp) {
                            ChipRow("Bitrate") {
                                IperfUdpBitratePresets.forEach { mbps ->
                                    FilterChip(
                                        selected = config.udpBitrateMbps == mbps,
                                        onClick = { viewModel.updateConfig { it.copy(udpBitrateMbps = mbps) } },
                                        label = { Text(if (mbps >= 1000) "${mbps / 1000} Gbit" else "$mbps Mbit") },
                                        enabled = !isRunning
                                    )
                                }
                            }
                        }
                        ChipRow("Duration") {
                            IperfDurationPresets.forEach { secs ->
                                FilterChip(
                                    selected = config.durationSeconds == secs,
                                    onClick = { viewModel.updateConfig { it.copy(durationSeconds = secs) } },
                                    label = { Text("${secs}s") },
                                    enabled = !isRunning
                                )
                            }
                        }
                        ChipRow("Streams") {
                            IperfStreamPresets.forEach { n ->
                                FilterChip(
                                    selected = config.parallelStreams == n,
                                    onClick = { viewModel.updateConfig { it.copy(parallelStreams = n) } },
                                    label = { Text("$n") },
                                    enabled = !isRunning
                                )
                            }
                        }
                    }
                }
            } else {
                item(key = "server_info") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Runs one test, then stops. From another machine, connect with:",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                PortField(config, isRunning, viewModel)
                            }
                            val addresses = selectedInterface?.ipv4Address?.let { listOf(it) }
                                ?: interfaces.mapNotNull { it.ipv4Address }
                            addresses.forEach { ip ->
                                val cmd = "iperf3 -c $ip" + if (config.port != IperfConfig.DEFAULT_PORT) " -p ${config.port}" else ""
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().clickable { copy(cmd) }
                                ) {
                                    Text(cmd, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy command", modifier = Modifier.size(16.dp))
                                }
                            }
                            if (addresses.isEmpty()) {
                                Text("No Wi-Fi/Ethernet IPv4 address found.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            item(key = "interface") {
                var expanded by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { if (!isRunning) expanded = !expanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                            readOnly = true,
                            enabled = !isRunning,
                            value = selectedInterface?.displayLabel ?: "Default route",
                            onValueChange = {},
                            label = { Text("Bind to interface") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            interfaces.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.displayLabel) },
                                    onClick = {
                                        viewModel.setSelectedInterface(option)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    IconButton(onClick = viewModel::loadInterfaces, enabled = !isRunning) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh interfaces")
                    }
                }
            }

            item(key = "actions") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isRunning) {
                        Button(
                            onClick = viewModel::stopTest,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Stop")
                        }
                        if (config.isServerMode) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("Waiting for a client… ${elapsed}s", style = MaterialTheme.typography.labelMedium)
                        } else {
                            // Native iperf3 only returns output at the end, so show time-based progress.
                            val total = config.durationSeconds + 2
                            LinearProgressIndicator(
                                progress = { (elapsed.toFloat() / total).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                if (elapsed <= config.durationSeconds) "Testing… ${elapsed}s / ${config.durationSeconds}s"
                                else "Collecting results…",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    } else {
                        Button(
                            onClick = viewModel::startTest,
                            enabled = hostValid,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (config.isServerMode) "Start server" else "Start test")
                        }
                    }
                }
            }

            error?.let { message ->
                item(key = "error") {
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }

            result?.let { summary ->
                item(key = "result") { IperfResultCard(summary, onCopy = { copy(summary.toText()) }) }
                if (summary.intervals.size > 1) {
                    item(key = "chart") {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Throughput per interval (Mbit/s)", style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.height(8.dp))
                                MiniLineChart(
                                    values = summary.intervals.map { (it.bitsPerSecond / 1e6).toFloat() },
                                    contentDescription = "Throughput chart",
                                    maxLabel = { "%.0f Mbit/s".format(it) }
                                )
                            }
                        }
                    }
                }
            }

            if (testOutput.isNotBlank()) {
                item(key = "raw_header") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Raw iperf3 output", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { copy(testOutput) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy raw output")
                        }
                        IconButton(onClick = { showRaw = !showRaw }) {
                            Icon(if (showRaw) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = "Toggle raw output")
                        }
                    }
                }
                if (showRaw) {
                    item(key = "raw") {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                        ) {
                            SelectionContainer {
                                Text(
                                    text = testOutput,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .horizontalScroll(rememberScrollState())
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PortField(config: IperfConfig, isRunning: Boolean, viewModel: IperfViewModel) {
    var text by remember(config.port) { mutableStateOf(config.port.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            text = v.filter { it.isDigit() }.take(5)
            text.toIntOrNull()?.takeIf { it in 1..65535 }?.let { p -> viewModel.updateConfig { it.copy(port = p) } }
        },
        label = { Text("Port") },
        modifier = Modifier.width(96.dp),
        singleLine = true,
        enabled = !isRunning,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(72.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

@Composable
private fun IperfResultCard(summary: IperfSummary, onCopy: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${summary.protocol} ${if (summary.reverse) "download" else "upload"}" +
                            (summary.remoteHost?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = summary.headlineBps?.let { IperfResultParser.formatBitrate(it) } ?: "-",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy summary") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                summary.sentBps?.let { Metric("Sender", IperfResultParser.formatBitrate(it)) }
                summary.receivedBps?.let { Metric("Receiver", IperfResultParser.formatBitrate(it)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                (summary.receivedBytes ?: summary.sentBytes)?.let { Metric("Transferred", IperfResultParser.formatBytes(it)) }
                Metric("Streams", summary.numStreams.toString())
                summary.retransmits?.let { Metric("Retransmits", it.toString(), highlight = it > 0) }
            }
            if (summary.isUdp) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    summary.jitterMs?.let { Metric("Jitter", "%.2f ms".format(it)) }
                    summary.lostPercent?.let { Metric("Loss", "%.2f%%".format(it), highlight = it > 0.0) }
                }
            }
            val cpuLocal = summary.cpuLocalPercent
            val cpuRemote = summary.cpuRemotePercent
            if (cpuLocal != null || cpuRemote != null) {
                Text(
                    text = "CPU: this device ${cpuLocal?.let { "%.0f%%".format(it) } ?: "-"} · remote ${cpuRemote?.let { "%.0f%%".format(it) } ?: "-"}" +
                        if ((cpuLocal ?: 0.0) > 85.0) " (CPU-bound; results may understate the link)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, highlight: Boolean = false) {
    Column {
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (highlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
