package com.fearmikey.rf_reapr.ui.dns

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockPerson
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fearmikey.rf_reapr.domain.service.CheckStatus
import com.fearmikey.rf_reapr.domain.service.DnsCheck
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsAuditorScreen(
    viewModel: DnsAuditorViewModel,
    onBackClick: () -> Unit
) {
    val result by viewModel.auditResult.collectAsStateWithLifecycle()
    val isPassiveMode by viewModel.isPassiveMode.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("DNS Security Auditor") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (result != null && !isLoading) {
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(viewModel.reportText()))
                            scope.launch { snackbar.showSnackbar("Report copied") }
                        }) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy report") }
                        IconButton(onClick = viewModel::runAudit, enabled = !isPassiveMode) {
                            Icon(Icons.Default.Refresh, contentDescription = "Run again")
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
                item {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                        Text(
                            "Passive (stealth) mode is on, so DNS auditing is disabled.",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            val r = result
            when {
                isLoading -> item {
                    Card {
                        Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("Running DNS tests…")
                            Text(
                                "Comparing your resolver with DNS-over-HTTPS and checking hijacking, DNSSEC and rebinding.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                r == null -> item {
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("What this checks", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "• Answer integrity: names with fixed worldwide addresses (one.one.one.one, dns.google) must resolve correctly.\n" +
                                    "• NXDOMAIN hijacking: made-up names must return \"not found\".\n" +
                                    "• DNSSEC: a domain with broken signatures should be rejected.\n" +
                                    "• Rebinding protection: public names pointing at private IPs.\n" +
                                    "• Encrypted DNS (Private DNS) status.\n" +
                                    "• Cross-check of popular domains with Cloudflare/Google DNS-over-HTTPS.\n\n" +
                                    "Contacts Cloudflare and Google DoH, and resolves nip.io and dnssec-failed.org test names.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            Button(onClick = viewModel::runAudit, enabled = !isPassiveMode, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.LockPerson, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Start DNS audit")
                            }
                        }
                    }
                }
                else -> {
                    item { VerdictCard(r.overall, r.checks, r.durationMs) }
                    items(r.checks, key = { it.id }) { CheckRow(it) }
                }
            }
        }
    }
}

@Composable
private fun VerdictCard(overall: CheckStatus, checks: List<DnsCheck>, durationMs: Long) {
    val (title, body) = when (overall) {
        CheckStatus.PASS -> "DNS looks healthy" to "No signs of tampering were found."
        CheckStatus.WARN -> "DNS needs attention" to "Some checks found weaknesses or filtering. See below."
        CheckStatus.FAIL -> "DNS tampering detected" to "At least one answer was modified. Avoid sensitive logins on this network until it's resolved."
        else -> "Audit inconclusive" to "Not enough tests completed to reach a verdict."
    }
    val container = when (overall) {
        CheckStatus.FAIL -> MaterialTheme.colorScheme.errorContainer
        CheckStatus.WARN -> MaterialTheme.colorScheme.tertiaryContainer
        CheckStatus.PASS -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(colors = CardDefaults.cardColors(containerColor = container)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val (icon, tint) = statusVisual(overall)
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(body, style = MaterialTheme.typography.bodySmall)
                val counts = CheckStatus.entries.mapNotNull { s -> checks.count { it.status == s }.takeIf { it > 0 }?.let { "$it ${s.name.lowercase()}" } }
                Text("${counts.joinToString(" · ")} · ${durationMs / 1000.0}s", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun statusVisual(status: CheckStatus): Pair<ImageVector, Color> = when (status) {
    CheckStatus.PASS -> Icons.Default.CheckCircle to Color(0xFF4CAF50)
    CheckStatus.INFO -> Icons.Default.Info to MaterialTheme.colorScheme.primary
    CheckStatus.WARN -> Icons.Default.Warning to Color(0xFFFF9800)
    CheckStatus.FAIL -> Icons.Default.Error to MaterialTheme.colorScheme.error
    CheckStatus.ERROR -> Icons.AutoMirrored.Filled.HelpOutline to MaterialTheme.colorScheme.outline
}

@Composable
private fun CheckRow(check: DnsCheck) {
    var expanded by remember { mutableStateOf(check.status == CheckStatus.FAIL) }
    val (icon, tint) = statusVisual(check.status)
    Card(Modifier.fillMaxWidth().clickable(enabled = check.detail.isNotBlank()) { expanded = !expanded }) {
        Row(Modifier.padding(12.dp)) {
            Icon(icon, contentDescription = check.status.name, tint = tint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(check.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(check.summary, style = MaterialTheme.typography.bodySmall)
                AnimatedVisibility(expanded && check.detail.isNotBlank()) {
                    Text(
                        check.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}
