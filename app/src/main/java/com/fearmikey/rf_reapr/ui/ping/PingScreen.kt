package com.fearmikey.rf_reapr.ui.ping

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fearmikey.rf_reapr.domain.repository.PingRepository.PingStatus
import com.fearmikey.rf_reapr.domain.service.HostValidator
import com.fearmikey.rf_reapr.domain.service.PingOutputParser
import com.fearmikey.rf_reapr.domain.service.PingQuality
import com.fearmikey.rf_reapr.domain.service.PingStats
import com.fearmikey.rf_reapr.ui.components.MiniLineChart

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PingScreen(
    viewModel: PingViewModel,
    initialHost: String? = null,
    onBack: () -> Unit
) {
    val host by viewModel.host.collectAsStateWithLifecycle()
    val options by viewModel.options.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isPassiveMode by viewModel.isPassiveMode.collectAsStateWithLifecycle()
    val pingLines by viewModel.pingLines.collectAsStateWithLifecycle()
    val replies by viewModel.replies.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val lastSeq by viewModel.lastSeq.collectAsStateWithLifecycle()
    val gatewayIp by viewModel.gatewayIp.collectAsStateWithLifecycle()

    val isRunning = uiState is PingStatus.Loading || uiState is PingStatus.Progress
    val hostValid = HostValidator.normalize(host) != null
    val keyboardController = LocalSoftwareKeyboardController.current
    var showConsole by rememberSaveable { mutableStateOf(false) }
    var showOptions by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(initialHost) { viewModel.setInitialHost(initialHost) }

    val start: () -> Unit = {
        viewModel.runPing()
        keyboardController?.hide()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ping Tool") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "input") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = viewModel::setHost,
                        label = { Text("Target host or IP") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isRunning && !isPassiveMode,
                        isError = host.isNotBlank() && !hostValid,
                        supportingText = if (host.isNotBlank() && !hostValid) {
                            { Text("Not a valid hostname or IP address") }
                        } else null,
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go,
                            autoCorrectEnabled = false
                        ),
                        keyboardActions = KeyboardActions(onGo = { if (hostValid && !isPassiveMode) start() }),
                        trailingIcon = {
                            if (isRunning) {
                                IconButton(onClick = viewModel::cancelPing) {
                                    Icon(Icons.Default.Stop, contentDescription = "Stop ping", tint = Color.Red)
                                }
                            } else {
                                IconButton(onClick = start, enabled = hostValid && !isPassiveMode) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Run ping")
                                }
                            }
                        }
                    )
                    com.fearmikey.rf_reapr.ui.components.ScopeWarning(host)
                    // Quick targets
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val targets = listOfNotNull(
                            gatewayIp?.let { "Gateway" to it },
                            "Cloudflare" to "1.1.1.1",
                            "Google DNS" to "8.8.8.8",
                            "google.com" to "google.com"
                        )
                        targets.forEach { (label, value) ->
                            SuggestionChip(
                                onClick = { viewModel.setHost(value) },
                                label = { Text(label) },
                                enabled = !isRunning
                            )
                        }
                    }
                }
            }

            if (isPassiveMode) {
                item(key = "passive") {
                    Text(
                        "Passive Mode (Stealth). Pinging inhibited.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            item(key = "options") {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Count: ${options.count ?: "∞"} · Interval: ${options.intervalSec}s · Size: ${options.packetSize} B" +
                                if (options.dontFragment) " · DF" else "",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { showOptions = !showOptions }) {
                            Text(if (showOptions) "Hide options" else "Options")
                        }
                    }
                    AnimatedVisibility(visible = showOptions) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OptionRow("Count") {
                                PingCountPresets.forEach { count ->
                                    FilterChip(
                                        selected = options.count == count,
                                        onClick = { viewModel.updateOptions { it.copy(count = count) } },
                                        label = { Text(count?.toString() ?: "Continuous") },
                                        enabled = !isRunning
                                    )
                                }
                            }
                            OptionRow("Interval") {
                                PingIntervalPresets.forEach { interval ->
                                    FilterChip(
                                        selected = options.intervalSec == interval,
                                        onClick = { viewModel.updateOptions { it.copy(intervalSec = interval) } },
                                        label = { Text("${interval}s") },
                                        enabled = !isRunning
                                    )
                                }
                            }
                            OptionRow("Payload") {
                                PingSizePresets.forEach { size ->
                                    FilterChip(
                                        selected = options.packetSize == size,
                                        onClick = { viewModel.updateOptions { it.copy(packetSize = size) } },
                                        label = { Text("$size B") },
                                        enabled = !isRunning
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Don't fragment (MTU probe)", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "With 1472 B payload, replies confirm a 1500-byte path MTU.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = options.dontFragment,
                                    onCheckedChange = { df -> viewModel.updateOptions { it.copy(dontFragment = df) } },
                                    enabled = !isRunning
                                )
                            }
                        }
                    }
                }
            }

            if (uiState is PingStatus.Loading) {
                item(key = "loading") { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }

            stats?.let { s ->
                item(key = "stats") { PingStatsCard(stats = s, isRunning = isRunning) }
                if (replies.isNotEmpty() || lastSeq > 0) {
                    item(key = "chart") {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Round-trip time (ms)", style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.height(8.dp))
                                MiniLineChart(
                                    values = PingOutputParser.rttSeries(replies, lastSeq),
                                    contentDescription = "Round-trip time chart",
                                    maxLabel = { "%.0f ms".format(it) }
                                )
                                Text(
                                    "Red dots mark lost or failed packets",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            (uiState as? PingStatus.Error)?.let { error ->
                item(key = "error") {
                    Text(error.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (pingLines.isNotEmpty()) {
                item(key = "console_header") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Raw output (${pingLines.size} lines)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { showConsole = !showConsole }) {
                            Icon(
                                if (showConsole) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (showConsole) "Hide raw output" else "Show raw output"
                            )
                        }
                        TextButton(onClick = viewModel::reset, enabled = !isRunning) { Text("Clear") }
                    }
                }
                if (showConsole) {
                    item(key = "console") { PingConsole(pingLines) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionRow(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(64.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

@Composable
private fun PingStatsCard(stats: PingStats, isRunning: Boolean) {
    val quality = stats.quality
    val qualityColor = when (quality) {
        PingQuality.EXCELLENT, PingQuality.GOOD -> Color(0xFF4CAF50)
        PingQuality.FAIR -> Color(0xFFFFA000)
        PingQuality.POOR, PingQuality.UNREACHABLE -> MaterialTheme.colorScheme.error
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Average", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = stats.avgMs?.let { "%.1f ms".format(it) } ?: "-",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = if (isRunning && stats.transmitted < 3) "Measuring…" else quality.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = qualityColor
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Metric("Min", stats.minMs?.let { "%.1f".format(it) } ?: "-")
                Metric("Max", stats.maxMs?.let { "%.1f".format(it) } ?: "-")
                Metric("Jitter", stats.jitterMs?.let { "%.1f".format(it) } ?: "-")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Metric("Sent", stats.transmitted.toString())
                Metric("Received", stats.received.toString())
                Metric(
                    "Loss",
                    "%.0f%%".format(stats.lossPercent),
                    color = if (stats.lossPercent > 0f) MaterialTheme.colorScheme.error else null
                )
                if (stats.errors > 0) Metric("Errors", stats.errors.toString(), MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, color: Color? = null) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color ?: Color.Unspecified)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PingConsole(lines: List<String>) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.size - 1)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .background(Color.Black, shape = MaterialTheme.shapes.medium)
            .padding(8.dp)
    ) {
        LazyColumn(state = listState) {
            items(lines) { line ->
                Text(
                    text = line,
                    color = Color.Green,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }
    }
}
