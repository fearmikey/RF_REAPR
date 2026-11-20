package com.fearmikey.rf_reapr.ui.nfc

import android.nfc.NfcAdapter
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fearmikey.rf_reapr.domain.model.NfcTagData
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.ui.scanner.SeverityBadge
import com.fearmikey.rf_reapr.ui.theme.NfcPurple
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcScannerScreen(
    viewModel: NfcScannerViewModel,
    onBack: () -> Unit
) {
    val scanned by viewModel.tagData.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val adapter = remember { NfcAdapter.getDefaultAdapter(context) }

    // Reader mode only while this screen is visible (it used to run app-wide).
    DisposableEffect(Unit) {
        viewModel.setReaderActive(true)
        onDispose { viewModel.setReaderActive(false) }
    }
    // A fresh scan replaces whatever history item was being viewed.
    LaunchedEffect(scanned) { if (scanned != null) viewModel.clearSelection() }

    val tag = selected ?: scanned

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NFC Scanner") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    tag?.let { t ->
                        IconButton(onClick = { clipboard.setText(AnnotatedString(viewModel.reportText(t))) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy report")
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                adapter == null -> item {
                    Text("This device has no NFC hardware.", color = MaterialTheme.colorScheme.error)
                }
                !adapter.isEnabled -> item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("NFC is turned off", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { runCatching { context.startActivity(android.content.Intent(Settings.ACTION_NFC_SETTINGS)) } }) {
                                Text("Open NFC settings")
                            }
                        }
                    }
                }
            }

            if (tag == null) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Nfc, contentDescription = null, modifier = Modifier.size(96.dp), tint = NfcPurple.copy(alpha = 0.7f))
                        Text("Ready to scan", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Hold a tag or card flat against the back of the phone and keep it still for a second.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            } else {
                item { TagInfoCard(tag) }
                item {
                    OutlinedButton(onClick = { viewModel.clearSelection(); viewModel.clearData() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Clear and scan again")
                    }
                }
            }

            if (history.size > 1 || (history.isNotEmpty() && tag == null)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("This session (${history.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = viewModel::clearHistory) { Icon(Icons.Default.Delete, contentDescription = "Clear history") }
                    }
                }
                items(history, key = { it.id + it.timestamp }) { h ->
                    ListItem(
                        modifier = Modifier.clickable { viewModel.show(h) },
                        headlineContent = { Text(h.chipType) },
                        supportingContent = { Text("${h.id} · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(h.timestamp))}", fontFamily = FontFamily.Monospace) },
                        trailingContent = { h.findings.maxByOrNull { it.severity.ordinal }?.let { SeverityBadge(it.severity) } }
                    )
                }
            }
        }
    }
}

@Composable
fun TagInfoCard(tag: NfcTagData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (tag.isVulnerable) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer) else CardDefaults.cardColors()
    ) {
        SelectionContainer {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tag.chipType, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("UID ${tag.id}" + if (tag.randomUid) " (random per tap)" else "", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                if (tag.atqa != null) Text("ATQA ${tag.atqa} · SAK ${tag.sak}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                Text("Technologies: ${tag.techList.joinToString { it.substringAfterLast('.') }}", style = MaterialTheme.typography.bodySmall)
                tag.ndefCapacity?.let {
                    Text(
                        "NDEF: ${tag.ndefUsed ?: 0} of $it bytes used · " + when (tag.writable) { true -> "writable"; false -> "read-only"; null -> "" },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                tag.readError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                if (tag.records.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Records", style = MaterialTheme.typography.labelLarge)
                    tag.records.forEach { r ->
                        Text(r.kind, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(r.value, style = MaterialTheme.typography.bodySmall)
                    }
                } else if (tag.ndefCapacity != null) {
                    Text("NDEF formatted but empty.", style = MaterialTheme.typography.bodySmall)
                }

                if (tag.findings.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Findings", style = MaterialTheme.typography.labelLarge)
                    tag.findings.forEach { f ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(f.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            SeverityBadge(f.severity)
                        }
                        Text(f.detail, style = MaterialTheme.typography.bodySmall, color = if (f.severity >= RiskLevel.MEDIUM) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
