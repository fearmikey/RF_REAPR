package com.fearmikey.rf_reapr.ui.findings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.fearmikey.rf_reapr.domain.service.ComplianceMapper
import com.fearmikey.rf_reapr.ui.compliance.EvidenceIntegrityRow
import java.io.File
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
    var showOverflow by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

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
                                    exportLauncher.launch(exportFileName(projectName ?: "rf-reapr", format.fileSuffix, format.extension))
                                })
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { showOverflow = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More options") }
                        DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                            DropdownMenuItem(
                                text = { Text("Clear all findings") },
                                leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                                enabled = total > 0,
                                onClick = { showOverflow = false; confirmClear = true }
                            )
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
      BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
        // Tablets, unfolded foldables and desktop windows get list + detail side by side.
        val twoPane = maxWidth >= 840.dp
        val selected = allFindings.firstOrNull { it.id == selectedId }
        Row(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = if (twoPane) Modifier.weight(0.42f).fillMaxHeight() else Modifier.fillMaxSize(),
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
            items(findings, key = { it.id }) { f -> FindingRow(f, selected = twoPane && f.id == selectedId) { selectedId = f.id } }
        }
        if (twoPane) {
            VerticalDivider()
            Box(Modifier.weight(0.58f).fillMaxHeight()) {
                if (selected == null) {
                    Text(
                        "Select a finding to see its details",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FindingDetail(
                        finding = selected,
                        viewModel = viewModel,
                        onDeleted = { selectedId = null },
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
                    )
                }
            }
        }
        }
        if (!twoPane && selected != null) {
            ModalBottomSheet(onDismissRequest = { selectedId = null }) {
                FindingDetail(
                    finding = selected,
                    viewModel = viewModel,
                    onDeleted = { selectedId = null },
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)
                )
            }
        }
      }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
            title = { Text("Clear all findings?") },
            text = {
                Text(
                    "This permanently deletes all $total findings in " +
                        (projectName?.let { "\"$it\"" } ?: "the no-project list") +
                        ", including triage status and notes. Export them first if you need a record."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        selectedId = null
                        viewModel.clearAll()
                        scope.launch { snackbar.showSnackbar("Findings cleared") }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete all") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
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
private fun FindingRow(f: Finding, selected: Boolean = false, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = if (selected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else CardDefaults.cardColors()
    ) {
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

/** Detail of one finding: shown in a bottom sheet on phones and in the side pane on wide windows. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FindingDetail(
    finding: Finding,
    viewModel: FindingsViewModel,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val onStatus: (FindingStatus) -> Unit = { viewModel.setStatus(finding, it) }
    val onNotes: (String) -> Unit = { viewModel.setNotes(finding, it) }
    val onDelete: () -> Unit = { viewModel.delete(finding); onDeleted() }
    val evidence by remember(finding.id) { viewModel.evidenceFor(finding.id) }.collectAsStateWithLifecycle(emptyList())
    val candidates by viewModel.candidateEvidence.collectAsStateWithLifecycle()
    val controls = remember(finding.tool, finding.title) { ComplianceMapper.controlIds(finding) }
    var showEvidencePicker by remember { mutableStateOf(false) }

    var notes by remember(finding.id) { mutableStateOf(finding.notes.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Unsaved notes are kept when the sheet is dismissed or another finding is selected.
    val latestNotes by rememberUpdatedState(notes)
    val savedNotes by rememberUpdatedState(finding.notes.orEmpty())
    DisposableEffect(finding.id) {
        onDispose { if (latestNotes != savedNotes) viewModel.setNotes(finding, latestNotes) }
    }
    run {
        Column(
            modifier,
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
            if (controls.isNotEmpty()) {
                Text("Related compliance controls", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    controls.forEach { id -> SuggestionChip(onClick = {}, label = { Text(id) }) }
                }
            }
            Text("Evidence (${evidence.size})", style = MaterialTheme.typography.labelLarge)
            if (evidence.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(evidence, key = { it.id }) { item ->
                        Column(Modifier.width(132.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box {
                                AsyncImage(
                                    model = File(item.filePath),
                                    contentDescription = item.notes.ifBlank { "Evidence photo" },
                                    modifier = Modifier.size(132.dp).clip(MaterialTheme.shapes.small),
                                    contentScale = ContentScale.Crop
                                )
                                FilledTonalIconButton(
                                    onClick = { viewModel.detachEvidence(item) },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(28.dp)
                                ) { Icon(Icons.Default.LinkOff, contentDescription = "Detach from this finding", modifier = Modifier.size(16.dp)) }
                            }
                            EvidenceIntegrityRow(evidence = item, verify = viewModel::verifyIntegrity)
                        }
                    }
                }
            }
            OutlinedButton(onClick = { showEvidencePicker = true }) {
                Icon(Icons.Default.AttachFile, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Attach evidence")
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
    if (showEvidencePicker) {
        val attachedIds = evidence.map { it.id }.toSet()
        val available = candidates.filter { it.id !in attachedIds }
        AlertDialog(
            onDismissRequest = { showEvidencePicker = false },
            title = { Text("Attach evidence") },
            text = {
                if (available.isEmpty()) {
                    Text("There is no other evidence in this project. Capture or import photos from Evidence Capture first.")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(96.dp),
                        modifier = Modifier.heightIn(max = 420.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(available, key = { it.id }) { item ->
                            Box(Modifier.clickable { viewModel.attachEvidence(item, finding); showEvidencePicker = false }) {
                                AsyncImage(
                                    model = File(item.filePath),
                                    contentDescription = item.notes.ifBlank { "Evidence photo" },
                                    modifier = Modifier.aspectRatio(1f).clip(MaterialTheme.shapes.small),
                                    contentScale = ContentScale.Crop
                                )
                                if (item.findingId != null) {
                                    // Attaching moves the photo: an item documents one finding at a time.
                                    Icon(
                                        Icons.Default.Link,
                                        contentDescription = "Already linked to another finding",
                                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp),
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showEvidencePicker = false }) { Text("Close") } }
        )
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
