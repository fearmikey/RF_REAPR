package com.fearmikey.rf_reapr.ui.findings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fearmikey.rf_reapr.domain.model.Finding
import com.fearmikey.rf_reapr.domain.model.FindingStatus
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.ui.components.ExportFormat
import com.fearmikey.rf_reapr.ui.components.exportFileName
import com.fearmikey.rf_reapr.ui.components.writeTextToUri
import com.fearmikey.rf_reapr.ui.scanner.SeverityBadge
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindingsScreen(viewModel: FindingsViewModel, onBack: () -> Unit) {
    val findings by viewModel.findings.collectAsStateWithLifecycle()
    val allFindings by viewModel.all.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val total by viewModel.totalCount.collectAsStateWithLifecycle()
    val projectName by viewModel.projectName.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<ExportFormat?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val format = pendingExport
        pendingExport = null
        if (uri != null && format != null) {
            scope.launch {
                val error = writeTextToUri(context, uri, viewModel.buildExport(format))
                snackbar.showSnackbar(error ?: "Exported ${format.label}")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Findings")
                        Text(
                            projectName ?: "No active project",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showExportMenu = true }) { Icon(Icons.Default.FileDownload, contentDescription = "Export") }
                        DropdownMenu(expanded = showExportMenu, onDismissRequest = { showExportMenu = false }) {
                            ExportFormat.entries.forEach { format ->
                                DropdownMenuItem(text = { Text(format.label) }, onClick = {
                                    showExportMenu = false
                                    pendingExport = format
                                    val suffix = if (format == ExportFormat.HOSTS_CSV) "hosts" else "findings"
                                    exportLauncher.launch(exportFileName(projectName ?: "rf-reapr", suffix, format.extension))
                                })
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add finding") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "filters") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = filter.query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Search title, target, tool…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RiskLevel.entries.reversed().forEach { sev ->
                            FilterChip(selected = sev in filter.severities, onClick = { viewModel.toggleSeverity(sev) }, label = { Text(sev.name) })
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FindingStatus.entries.forEach { st ->
                            FilterChip(selected = st in filter.statuses, onClick = { viewModel.toggleStatus(st) }, label = { Text(st.label) })
                        }
                    }
                    Text(
                        "Showing ${findings.size} of $total",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (findings.isEmpty()) {
                item(key = "empty") {
                    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text(if (total == 0) "No findings yet" else "No findings match the filters", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Port Scanner, TLS, DNS, UPnP, SNMP, ARP, NFC, USB and Cellular tools record findings here automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            items(findings, key = { it.id }) { f -> FindingRow(f) { selectedId = f.id } }
        }
    }

    allFindings.firstOrNull { it.id == selectedId }?.let { f ->
        FindingDetailSheet(
            finding = f,
            onDismiss = { selectedId = null },
            onStatus = { viewModel.setStatus(f, it) },
            onNotes = { viewModel.setNotes(f, it) },
            onDelete = { viewModel.delete(f); selectedId = null }
        )
    }

    if (showAdd) {
        AddFindingDialog(
            onDismiss = { showAdd = false },
            onAdd = { title, target, sev, detail, fix ->
                viewModel.addManual(title, target, sev, detail, fix)
                showAdd = false
            }
        )
    }
}

@Composable
private fun FindingRow(f: Finding, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SeverityBadge(f.severity)
                Text(f.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            Text(f.target, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            Text(
                "${f.tool} · ${f.status.label} · seen ${f.occurrences}× · last ${formatTime(f.lastSeen)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FindingDetailSheet(
    finding: Finding,
    onDismiss: () -> Unit,
    onStatus: (FindingStatus) -> Unit,
    onNotes: (String) -> Unit,
    onDelete: () -> Unit
) {
    var notes by remember(finding.id) { mutableStateOf(finding.notes.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = { if (notes != finding.notes.orEmpty()) onNotes(notes); onDismiss() }) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SeverityBadge(finding.severity)
                Text(finding.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(finding.target, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${finding.tool} · first ${formatTime(finding.firstSeen)} · last ${formatTime(finding.lastSeen)} · ${finding.occurrences}×",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (finding.detail.isNotBlank()) Text(finding.detail, style = MaterialTheme.typography.bodyMedium)
                    finding.remediation?.let {
                        Text("Remediation", style = MaterialTheme.typography.labelLarge)
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Text("Status", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FindingStatus.entries.forEach { st ->
                    FilterChip(selected = finding.status == st, onClick = { onStatus(st) }, label = { Text(st.label) })
                }
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Auditor notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onNotes(notes) }, enabled = notes != finding.notes.orEmpty()) { Text("Save notes") }
                TextButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Delete")
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete finding?") },
            text = { Text("If a tool reports it again it will come back as a new finding. To hide it but keep a record, mark it False positive or Risk accepted instead.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddFindingDialog(onDismiss: () -> Unit, onAdd: (String, String, RiskLevel, String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var severity by remember { mutableStateOf(RiskLevel.MEDIUM) }
    var detail by remember { mutableStateOf("") }
    var fix by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add finding") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(value = target, onValueChange = { target = it }, label = { Text("Target (host, room, asset…)") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RiskLevel.entries.reversed().forEach { sev ->
                        FilterChip(selected = severity == sev, onClick = { severity = sev }, label = { Text(sev.name) })
                    }
                }
                OutlinedTextField(value = detail, onValueChange = { detail = it }, label = { Text("Details / evidence") }, minLines = 2)
                OutlinedTextField(value = fix, onValueChange = { fix = it }, label = { Text("Remediation (optional)") })
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(title, target, severity, detail, fix) }, enabled = title.isNotBlank() && target.isNotBlank()) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun formatTime(ms: Long): String = SimpleDateFormat("MMM d HH:mm", Locale.getDefault()).format(Date(ms))
