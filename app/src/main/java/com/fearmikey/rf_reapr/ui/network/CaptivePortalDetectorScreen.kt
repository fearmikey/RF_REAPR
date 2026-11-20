package com.fearmikey.rf_reapr.ui.network

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fearmikey.rf_reapr.domain.service.CaptivePortalAnalyzer
import com.fearmikey.rf_reapr.domain.service.PortalProbeResult
import com.fearmikey.rf_reapr.domain.service.PortalStatus
import com.fearmikey.rf_reapr.domain.service.ProbeVerdict

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptivePortalDetectorScreen(viewModel: CaptivePortalViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Captive Portal Detector") },
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Checks whether this network intercepts traffic (hotel/airport sign-in pages, transparent proxies, TLS inspection) by fetching well-known check URLs from Google, Apple, Mozilla and Microsoft and comparing the exact responses.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            state.system?.let { sys ->
                item {
                    Card {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Sensors, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Android's view: ${sys.transport}", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    when {
                                        sys.captivePortalFlag -> "Flagged as captive portal (sign-in required)"
                                        sys.validated -> "Validated: internet access confirmed"
                                        else -> "Not validated: no confirmed internet access"
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = viewModel::runTest,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isTesting
                ) {
                    if (state.isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Testing ${CaptivePortalAnalyzer.PROBES.size} endpoints…")
                    } else {
                        Text(if (state.verdict == null) "Run test" else "Run again")
                    }
                }
            }

            state.verdict?.let { v ->
                item {
                    val (icon, container) = when (v.status) {
                        PortalStatus.OPEN -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primaryContainer
                        PortalStatus.CAPTIVE_PORTAL -> Icons.Default.Warning to MaterialTheme.colorScheme.tertiaryContainer
                        PortalStatus.INTERCEPTED -> Icons.Default.Lock to MaterialTheme.colorScheme.errorContainer
                        PortalStatus.OFFLINE -> Icons.Default.WifiOff to MaterialTheme.colorScheme.errorContainer
                        PortalStatus.PARTIAL -> Icons.Default.Warning to MaterialTheme.colorScheme.secondaryContainer
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = container)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(v.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(v.detail, style = MaterialTheme.typography.bodyMedium)
                            v.loginUrl?.let { url ->
                                Spacer(Modifier.height(8.dp))
                                FilledTonalButton(onClick = { runCatching { uriHandler.openUri(url) } }) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Open sign-in page")
                                }
                                Text(url, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                            }
                        }
                    }
                }
            }

            if (state.results.isNotEmpty()) {
                item { Text("Endpoint results", style = MaterialTheme.typography.titleMedium) }
                items(state.results) { ProbeRow(it) }
            }
        }
    }
}

@Composable
private fun ProbeRow(r: PortalProbeResult) {
    val verdict = CaptivePortalAnalyzer.verdictFor(r)
    val (label, color) = when (verdict) {
        ProbeVerdict.OK -> "OK" to Color(0xFF4CAF50)
        ProbeVerdict.REDIRECTED -> "Redirected" to Color(0xFFFF9800)
        ProbeVerdict.MODIFIED -> "Modified" to Color(0xFFFF9800)
        ProbeVerdict.TLS_INTERCEPTED -> "TLS intercepted" to MaterialTheme.colorScheme.error
        ProbeVerdict.FAILED -> "Failed" to MaterialTheme.colorScheme.outline
    }
    Card {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (verdict == ProbeVerdict.OK) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(r.probe.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(label, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            SelectionContainer {
                Column {
                    Text(r.probe.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val line = buildString {
                        r.code?.let { append("HTTP $it (expected ${r.probe.expectedCode})") }
                        r.error?.let { append(it) }
                        append(" · ${r.elapsedMs} ms")
                    }
                    Text(line, style = MaterialTheme.typography.bodySmall)
                    r.location?.let { Text("→ $it", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                    r.proxyHeaders.forEach { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                    if (verdict == ProbeVerdict.MODIFIED && !r.bodySnippet.isNullOrBlank()) {
                        Text(
                            r.bodySnippet.take(160).replace(Regex("\\s+"), " "),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 3
                        )
                    }
                }
            }
        }
    }
}
