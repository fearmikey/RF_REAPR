package com.fearmikey.rf_reapr.ui.engagement

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fearmikey.rf_reapr.domain.model.ReportScanSession
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.ui.components.ExportFormat
import com.fearmikey.rf_reapr.ui.components.exportFileName
import com.fearmikey.rf_reapr.ui.components.writeTextToUri
import com.fearmikey.rf_reapr.ui.scanner.SeverityBadge
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SCOPE_HELP = """
    One entry per line. Examples:
    10.0.0.0/24 · 192.168.1.20-40 · 172.16.5.9
    example.com (incl. subdomains) · *.corp.example.com
    !10.0.0.1 excludes a host · # starts a comment
""".trimIndent()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngagementScreen(
    viewModel: EngagementViewModel,
    onOpenFindings: () -> Unit,
    onOpenBackup: () -> Unit,
    onBack: () -> Unit
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val active by viewModel.activeProject.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val comparison by viewModel.comparison.collectAsStateWithLifecycle()
    val scopeDraft by viewModel.scopeDraft.collectAsStateWithLifecycle()
    val scopeInfo by viewModel.scopeInfo.collectAsStateWithLifecycle()
    val message by viewModel.messages.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showNewProject by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<ExportFormat?>(null) }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val format = pendingExport
        pendingExport = null
        if (uri != null && format != null) {
            scope.launch {
                val error = writeTextToUri(context, uri, viewModel.buildExport(format))
                viewModel.showMessage(error ?: "Exported ${format.label}")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Engagement") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "project") {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Active project", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        ProjectPicker(
                            selectedName = active?.name ?: "None (unassigned)",
                            options = listOf<Pair<String?, String>>(null to "None (unassigned)") + projects.map { it.id to it.name },
                            onSelect = viewModel::setActive
                        )
                        Text(
                            "While a project is active, network scans, tool logs and findings are tagged with it, " +
                                "and tools warn when a target is outside its scope.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = { showNewProject = true }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("New project")
                        }
                    }
                }
            }

            active?.let { project ->
                item(key = "scope") {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Authorized scope", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = scopeDraft ?: project.scope,
                                onValueChange = viewModel::editScope,
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                maxLines = 10,
                                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                                placeholder = { Text("10.0.0.0/24\nexample.com") },
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                                supportingText = {
                                    Text(
                                        when {
                                            scopeInfo.invalidLines.isNotEmpty() -> "Can't parse: ${scopeInfo.invalidLines.joinToString(" · ")}"
                                            scopeInfo.ruleCount == 0 -> "No scope defined, so targets aren't checked."
                                            else -> "${scopeInfo.ruleCount} entries"
                                        }
                                    )
                                },
                                isError = scopeInfo.invalidLines.isNotEmpty()
                            )
                            Text(SCOPE_HELP, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (scopeDraft != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = viewModel::saveScope) { Text("Save scope") }
                                    TextButton(onClick = viewModel::discardScopeEdit) { Text("Discard") }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "summary") {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (active == null) "Unassigned work" else "Summary",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RiskLevel.entries.reversed().forEach { sev ->
                                val n = stats.openBySeverity[sev] ?: 0
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SeverityBadge(sev)
                                    Text(" $n", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Text(
                            "Open findings by severity · ${stats.totalFindings} total · ${stats.scans} network scans · " +
                                "${stats.logs} tool logs" + if (active != null) " · ${stats.evidence} evidence items" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (active == null) {
                            Text(
                                "No project is active: showing findings recorded without a project and all scans on this device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(onClick = onOpenFindings, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.BugReport, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Review findings")
                        }
                    }
                }
            }

            item(key = "export") {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Export", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Saved to a location you choose. Markdown is a ready-to-share report; JSON/CSV import into other tools.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ExportFormat.entries.forEach { format ->
                            TextButton(onClick = {
                                pendingExport = format
                                val suffix = when (format) {
                                    ExportFormat.HOSTS_CSV -> "hosts"
                                    ExportFormat.CSV -> "findings"
                                    else -> "report"
                                }
                                exportLauncher.launch(exportFileName(active?.name ?: "rf-reapr", suffix, format.extension))
                            }) {
                                Icon(Icons.Default.FileDownload, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(format.label)
                            }
                        }
                    }
                }
            }

            item(key = "diff") {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Compare network scans", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        val diff = comparison
                        if (diff == null) {
                            Text(
                                "Run Network Discovery at least twice" + (if (active != null) " in this project" else "") +
                                    " to see which hosts appeared or disappeared.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            SessionPicker("Baseline", diff.baseline, sessions) { viewModel.selectComparison(it, diff.latest.id) }
                            SessionPicker("Compare with", diff.latest, sessions) { viewModel.selectComparison(diff.baseline.id, it) }
                            Text(
                                "${diff.added.size} new · ${diff.removed.size} gone · ${diff.unchangedCount} unchanged",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            diff.added.forEach { HostLine("+", it, MaterialTheme.colorScheme.error) }
                            diff.removed.forEach { HostLine("−", it, MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }

            item(key = "backup") {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Protect your data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Everything in RF-REAPR lives only on this phone. Make a password-encrypted backup so losing the device doesn't lose the engagement.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = onOpenBackup) {
                            Icon(Icons.Default.Backup, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Encrypted backup & restore")
                        }
                    }
                }
            }
        }
    }

    if (showNewProject) {
        NewProjectDialog(
            onDismiss = { showNewProject = false },
            onCreate = { name, description, scopeText ->
                viewModel.createProject(name, description, scopeText)
                showNewProject = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectPicker(selectedName: String, options: List<Pair<String?, String>>, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Project") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionPicker(label: String, selected: ReportScanSession, sessions: List<ReportScanSession>, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = sessionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            sessions.forEach { s ->
                DropdownMenuItem(text = { Text(sessionLabel(s)) }, onClick = { onSelect(s.id); expanded = false })
            }
        }
    }
}

private fun sessionLabel(s: ReportScanSession): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(s.timestamp)) +
        " · ${s.networkName ?: "network"} · ${s.nodes.size} hosts"

@Composable
private fun HostLine(prefix: String, host: DiffHost, color: androidx.compose.ui.graphics.Color) {
    Text(
        "$prefix ${host.ip}" + listOfNotNull(host.hostname, host.mac).joinToString(" · ", prefix = "  ").takeIf { it.isNotBlank() }.orEmpty(),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = color
    )
}

@Composable
private fun NewProjectDialog(onDismiss: () -> Unit, onCreate: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var scopeText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Client / description") })
                OutlinedTextField(
                    value = scopeText,
                    onValueChange = { scopeText = it },
                    label = { Text("Authorized scope (optional)") },
                    minLines = 2,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                )
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, description, scopeText) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
