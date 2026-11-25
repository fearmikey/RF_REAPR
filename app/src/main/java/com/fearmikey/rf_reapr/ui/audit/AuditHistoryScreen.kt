package com.fearmikey.rf_reapr.ui.audit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fearmikey.rf_reapr.domain.model.AuditRunFinding
import com.fearmikey.rf_reapr.domain.service.AuditRunDiff
import com.fearmikey.rf_reapr.ui.components.exportFileName
import com.fearmikey.rf_reapr.ui.components.writeTextToUri
import com.fearmikey.rf_reapr.ui.scanner.SeverityBadge
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Past Audit Mode runs of the active project, each compared with the one before it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditHistoryScreen(viewModel: AuditHistoryViewModel, onOpenLogs: () -> Unit, onBack: () -> Unit) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val projectName by viewModel.projectName.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var expandedId by remember { mutableStateOf<Long?>(null) }
    var pendingExport by remember { mutableStateOf<AuditHistoryEntry?>(null) }
    var confirmDelete by remember { mutableStateOf<AuditHistoryEntry?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val entry = pendingExport
        pendingExport = null
        if (uri != null && entry != null) {
            scope.launch { snackbar.showSnackbar(writeTextToUri(context, uri, viewModel.reportFor(entry)) ?: "Audit report exported") }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Audit history")
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
                    IconButton(onClick = onOpenLogs) { Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = "Raw audit logs") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (entries.isEmpty()) {
                item(key = "empty") {
                    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("No audits stored for this project yet", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Each finished Audit Mode run is kept here so the next one can show what was fixed, what is new and how the grade moved.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            items(entries, key = { it.run.id }) { entry ->
                val run = entry.run
                val expanded = expandedId == run.id
                Card(Modifier.fillMaxWidth().animateContentSize().clickable { expandedId = if (expanded) null else run.id }) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SmallGrade(run.grade)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(run.finishedAt)),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    listOfNotNull(
                                        "score ${run.score}/100",
                                        "${run.findings.size} finding${if (run.findings.size == 1) "" else "s"}",
                                        "${run.checks.size} check${if (run.checks.size == 1) "" else "s"}",
                                        "${run.hostCount} host${if (run.hostCount == 1) "" else "s"}".takeIf { run.hostCount > 0 },
                                        "incomplete".takeIf { !run.completed }
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                run.networkLabel?.let {
                                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Text(
                            entry.diff?.let { "Since the previous audit: ${it.summary()}" } ?: "First stored audit for this project",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (expanded) {
                            entry.diff?.let { AuditDiffDetails(it) }
                                ?: run.findings.takeIf { it.isNotEmpty() }?.let { DiffSection("Findings", it, MaterialTheme.colorScheme.onSurface) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    pendingExport = entry
                                    exportLauncher.launch(exportFileName(projectName ?: "rf-reapr", "audit", "md"))
                                }) {
                                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Export report")
                                }
                                TextButton(onClick = { confirmDelete = entry }) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this audit run?") },
            text = { Text("Its stored report and comparison data are removed. Findings it recorded stay in the Findings list.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(entry.run); confirmDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }
        )
    }
}

/** "Since the previous audit" card shown on the Audit Mode result screen. */
@Composable
fun AuditComparisonCard(diff: AuditRunDiff.Result, modifier: Modifier = Modifier) {
    var expanded by remember(diff.latest.id) { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth().animateContentSize().clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Since the previous audit", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Compared with ${SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(diff.previous.finishedAt))}: ${diff.summary()}",
                style = MaterialTheme.typography.bodySmall
            )
            if (expanded) AuditDiffDetails(diff)
            else if (diff.newFindings.isNotEmpty() || diff.resolved.isNotEmpty() || diff.notRetested.isNotEmpty()) {
                Text("Tap for details", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

@Composable
private fun AuditDiffDetails(diff: AuditRunDiff.Result) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DiffSection("Resolved", diff.resolved, Color(0xFF2E7D32))
        DiffSection("New", diff.newFindings, MaterialTheme.colorScheme.error)
        DiffSection("Still present", diff.persisting, MaterialTheme.colorScheme.onSurface)
        DiffSection("Not re-tested (check didn't run this time)", diff.notRetested, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DiffSection(title: String, items: List<AuditRunFinding>, color: Color) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$title (${items.size})", style = MaterialTheme.typography.labelLarge, color = color)
        items.take(MAX_DIFF_ROWS).forEach { f ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SeverityBadge(f.severity)
                Column {
                    Text(f.title, style = MaterialTheme.typography.bodySmall)
                    Text(
                        f.target, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (items.size > MAX_DIFF_ROWS) {
            Text(
                "…and ${items.size - MAX_DIFF_ROWS} more (see the exported report)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SmallGrade(grade: String) {
    val color = when (grade) {
        "A" -> Color(0xFF4CAF50)
        "B" -> Color(0xFF8BC34A)
        "C" -> Color(0xFFFFC107)
        "D" -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }
    Surface(color = color.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small, modifier = Modifier.size(44.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(grade, color = color, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        }
    }
}

private const val MAX_DIFF_ROWS = 15
