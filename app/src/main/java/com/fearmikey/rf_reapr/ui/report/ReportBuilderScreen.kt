package com.fearmikey.rf_reapr.ui.report

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportBuilderScreen(
    viewModel: ReportBuilderViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.generatedUri) {
        state.generatedUri?.let { uri ->
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, if (uri.toString().endsWith(".pdf")) "application/pdf" else "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Open Report"))
            viewModel.clearResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report Builder") },
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
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "Report Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = { viewModel.updateTitle(it) },
                    label = { Text("Report Title") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = state.auditorName,
                    onValueChange = { viewModel.updateAuditorName(it) },
                    label = { Text("Auditor Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = state.executiveSummary,
                    onValueChange = { viewModel.updateSummary(it) },
                    label = { Text("Executive Summary / Comments") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4
                )
            }

            // SECTIONS
            item { SectionHeader("Network Scans", Icons.Default.Router) }
            items(state.availableScans) { scan ->
                SelectionItem(
                    title = scan.networkName ?: "Unknown Network",
                    subtitle = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(scan.timestamp)),
                    isSelected = scan.id in state.selectedScanIds,
                    onToggle = { viewModel.toggleScan(scan.id) }
                )
            }

            item { SectionHeader("WiFi Spectrum Scans", Icons.Default.Wifi) }
            items(state.availableWifiScans) { scan ->
                SelectionItem(
                    title = scan.summary,
                    subtitle = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(scan.timestamp)),
                    isSelected = scan.id in state.selectedWifiScanIds,
                    onToggle = { viewModel.toggleWifiScan(scan.id) }
                )
            }

            item { SectionHeader("iPerf Throughput Tests", Icons.Default.NetworkPing) }
            items(state.availableIperfTests) { test ->
                SelectionItem(
                    title = test.summary,
                    subtitle = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(test.timestamp)),
                    isSelected = test.id in state.selectedIperfTestIds,
                    onToggle = { viewModel.toggleIperfTest(test.id) }
                )
            }

            item { SectionHeader("SNMP Browser Results", Icons.Default.Router) }
            items(state.availableSnmpResults) { result ->
                SelectionItem(
                    title = result.summary,
                    subtitle = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(result.timestamp)),
                    isSelected = result.id in state.selectedSnmpLogIds,
                    onToggle = { viewModel.toggleSnmpResult(result.id) }
                )
            }

            item { SectionHeader("Evidence Projects", Icons.Default.PhotoLibrary) }
            items(state.availableProjects) { project ->
                SelectionItem(
                    title = project.project.name,
                    subtitle = project.project.description,
                    isSelected = project.project.id in state.selectedProjectIds,
                    onToggle = { viewModel.toggleProject(project.project.id) }
                )
            }

            item { SectionHeader("Compliance Frameworks", Icons.AutoMirrored.Filled.Assignment) }
            items(state.availableCompliance) { framework ->
                SelectionItem(
                    title = framework.framework.title,
                    subtitle = "Audit status for ${framework.framework.name}",
                    isSelected = framework.framework.name in state.selectedComplianceIds,
                    onToggle = { viewModel.toggleCompliance(framework.framework.name) }
                )
            }

            item { SectionHeader("Event Logs", Icons.AutoMirrored.Filled.List) }
            items(state.availableLogs) { log ->
                SelectionItem(
                    title = "${log.type}: ${log.summary}",
                    subtitle = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp)),
                    isSelected = log.id in state.selectedLogIds,
                    onToggle = { viewModel.toggleLog(log.id) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = { viewModel.generateReport(ReportFormat.PDF) },
                        modifier = Modifier.weight(1f),
                        enabled = !state.isGenerating
                    ) {
                        if (state.isGenerating) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        else Text("Export PDF")
                    }
                    Button(
                        onClick = { viewModel.generateReport(ReportFormat.DOCX) },
                        modifier = Modifier.weight(1f),
                        enabled = !state.isGenerating,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        if (state.isGenerating) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        else Text("Export Word")
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SelectionItem(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        onClick = onToggle,
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
        }
    }
}
