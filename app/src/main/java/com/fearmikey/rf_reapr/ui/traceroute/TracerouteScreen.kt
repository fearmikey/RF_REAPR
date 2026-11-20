package com.fearmikey.rf_reapr.ui.traceroute

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fearmikey.rf_reapr.R
import com.fearmikey.rf_reapr.domain.repository.TracerouteHop

private val GoodLatency = Color(0xFF4CAF50)
private val FairLatency = Color(0xFFFFA000)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracerouteScreen(
    viewModel: TracerouteViewModel,
    initialTarget: String? = null,
    onPingHost: (String) -> Unit = {},
    onBackClick: () -> Unit
) {
    var hostInput by rememberSaveable { mutableStateOf(initialTarget?.takeIf { it.isNotBlank() } ?: "8.8.8.8") }
    val result by viewModel.result.collectAsStateWithLifecycle()
    val hops by viewModel.hops.collectAsStateWithLifecycle()
    val isPassiveMode by viewModel.isPassiveMode.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val maxHops by viewModel.maxHops.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val copy: (String) -> Unit = { text ->
        clipboardManager.setText(AnnotatedString(text))
        Toast.makeText(context, context.getString(R.string.copy_to_clipboard_success, text.lineSequence().first()), Toast.LENGTH_SHORT).show()
    }
    // Scale latency bars against the slowest hop seen so far.
    val maxLatency = hops.mapNotNull { it.avgMs }.maxOrNull()?.coerceAtLeast(1f) ?: 1f

    // Auto-scroll to bottom when new hops arrive
    LaunchedEffect(hops.size) {
        if (hops.isNotEmpty()) {
            listState.animateScrollToItem(hops.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Visual Traceroute") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (hops.isNotEmpty()) {
                        IconButton(onClick = { result?.let { copy(it.toReport()) } }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy report")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = hostInput,
                onValueChange = { hostInput = it },
                label = { Text("Target Host / IP") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isPassiveMode,
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Search,
                    autoCorrectEnabled = false
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (hostInput.isNotBlank() && !isPassiveMode) {
                            viewModel.startTraceroute(hostInput)
                            keyboardController?.hide()
                        }
                    }
                ),
                trailingIcon = {
                    if (isScanning) {
                        IconButton(onClick = { viewModel.stopTraceroute() }) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.Red)
                        }
                    } else {
                        IconButton(
                            onClick = {
                                viewModel.startTraceroute(hostInput)
                                keyboardController?.hide()
                            },
                            enabled = hostInput.isNotBlank() && !isPassiveMode
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Scan")
                        }
                    }
                }
            )
            com.fearmikey.rf_reapr.ui.components.ScopeWarning(hostInput)

            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("8.8.8.8", "1.1.1.1", "google.com").forEach { target ->
                    SuggestionChip(onClick = { hostInput = target }, label = { Text(target) }, enabled = !isScanning)
                }
                VerticalDivider(modifier = Modifier.height(24.dp))
                Text("Max hops", style = MaterialTheme.typography.labelMedium)
                TracerouteMaxHopPresets.forEach { preset ->
                    FilterChip(
                        selected = maxHops == preset,
                        onClick = { viewModel.setMaxHops(preset) },
                        label = { Text("$preset") },
                        enabled = !isScanning
                    )
                }
            }

            if (isPassiveMode) {
                Text(
                    "Passive Mode (Stealth). Traceroute inhibited.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            result?.let { r ->
                Spacer(Modifier.height(8.dp))
                when {
                    r.error != null -> Text(r.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    isScanning -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = "Tracing path to ${r.target}" + (r.targetIp?.takeIf { it != r.target }?.let { " ($it)" } ?: "") +
                                "… hop ${hops.size} of max $maxHops",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    r.isComplete -> SummaryBanner(reached = r.reachedDestination, hops = hops, target = r.target)
                    else -> Text(
                        "Stopped after ${hops.size} hops",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .scrollbar(listState),
                state = listState,
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(hops, key = { it.hopNumber }) { hop ->
                    HopItem(
                        hop = hop,
                        maxLatency = maxLatency,
                        isLast = hop == hops.lastOrNull(),
                        onCopyClick = copy,
                        onPingClick = onPingHost
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryBanner(reached: Boolean, hops: List<TracerouteHop>, target: String) {
    val finalHop = hops.lastOrNull { it.isFinal }
    val localHops = hops.count { it.isPrivate }
    Surface(
        color = if (reached) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (reached) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = if (reached) "Reached $target in ${hops.size} hops" else "$target not reached",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = buildString {
                        finalHop?.avgMs?.let { append("End-to-end %.1f ms · ".format(it)) }
                        append("$localHops local hop${if (localHops == 1) "" else "s"}")
                        if (!reached) append(" · the target or a firewall may be dropping ICMP")
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

fun Modifier.scrollbar(
    state: LazyListState,
    width: Dp = 4.dp
): Modifier = drawWithContent {
    drawContent()
    val layoutInfo = state.layoutInfo
    val visibleItemsInfo = layoutInfo.visibleItemsInfo
    val totalItemsCount = layoutInfo.totalItemsCount

    if (visibleItemsInfo.isEmpty() || totalItemsCount <= visibleItemsInfo.size) return@drawWithContent

    val viewportHeight = size.height

    val totalHeight = (totalItemsCount.toFloat() / visibleItemsInfo.size) * viewportHeight
    val scrollbarHeight = (viewportHeight / totalHeight) * viewportHeight
    val scrollbarOffset = (state.firstVisibleItemIndex.toFloat() / totalItemsCount) * viewportHeight

    drawRoundRect(
        color = Color.Gray.copy(alpha = 0.5f),
        topLeft = Offset(size.width - width.toPx(), scrollbarOffset),
        size = Size(width.toPx(), scrollbarHeight),
        cornerRadius = CornerRadius(width.toPx() / 2, width.toPx() / 2)
    )
}

@Composable
private fun latencyColor(ms: Float?): Color = when {
    ms == null -> MaterialTheme.colorScheme.outline
    ms < 50f -> GoodLatency
    ms < 150f -> FairLatency
    else -> MaterialTheme.colorScheme.error
}

@Composable
fun HopItem(
    hop: TracerouteHop,
    maxLatency: Float,
    isLast: Boolean,
    onCopyClick: (String) -> Unit,
    onPingClick: (String) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val color = latencyColor(hop.avgMs)

    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // Timeline rail: hop number badge with a connector to the next hop.
        Column(
            modifier = Modifier.width(36.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .size(28.dp)
                    .background(if (hop.ip == null) MaterialTheme.colorScheme.surfaceVariant else color.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = hop.hopNumber.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
            Card(
                onClick = { if (hop.ip != null) menuExpanded = true },
                enabled = hop.ip != null,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (hop.isFinal) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = hop.ip ?: "* * *  (no reply)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                color = if (hop.ip == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                            )
                            hop.hostname?.let {
                                Text(text = it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        when {
                            hop.isMeasuring -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            hop.avgMs != null -> Text(
                                text = "%.1f ms".format(hop.avgMs),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = color
                            )
                            hop.ip != null -> Text(
                                "no direct reply",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (hop.avgMs != null) {
                        LinearProgressIndicator(
                            progress = { (hop.avgMs!! / maxLatency).coerceIn(0.02f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = color,
                            trackColor = color.copy(alpha = 0.15f),
                            drawStopIndicator = {}
                        )
                    }
                    if (hop.ip != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            HopTag(if (hop.isPrivate) "LOCAL" else "PUBLIC", if (hop.isPrivate) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary)
                            if (hop.isFinal) HopTag("DESTINATION", MaterialTheme.colorScheme.primary)
                            val min = hop.minMs
                            val max = hop.maxMs
                            if (min != null && max != null && hop.rttSamples.size > 1) {
                                HopTag("%.0f–%.0f ms".format(min, max), MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            hop.lossPercent?.takeIf { it > 0f }?.let {
                                HopTag("%.0f%% loss".format(it), MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                hop.ip?.let { ip ->
                    DropdownMenuItem(
                        text = { Text("Copy IP") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onCopyClick(ip)
                        }
                    )
                    hop.hostname?.let { name ->
                        DropdownMenuItem(
                            text = { Text("Copy hostname") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onCopyClick(name)
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Ping this hop") },
                        leadingIcon = { Icon(Icons.Default.NetworkCheck, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onPingClick(ip)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HopTag(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
}
