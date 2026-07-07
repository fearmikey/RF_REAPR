package com.example.rf_reapr.ui.compliance

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.example.rf_reapr.domain.model.ComplianceControl
import com.example.rf_reapr.domain.model.ComplianceStatus
import com.example.rf_reapr.ui.theme.RF_REAPRTheme
import com.example.rf_reapr.util.AuditExporter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditChecklistScreen(
    frameworkId: String,
    viewModel: ComplianceViewModel,
    onBack: () -> Unit
) {
    val controls by viewModel.controls.collectAsState()
    val context = LocalContext.current
    var showExportDialog by remember { mutableStateOf(false) }

    val textExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { stream ->
                val report = AuditExporter.generateTextReport(frameworkId, controls)
                stream.write(report.toByteArray())
            }
        }
    }

    val pdfExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { stream ->
                AuditExporter.generatePdfReport(stream, frameworkId, controls)
            }
        }
    }

    LaunchedEffect(frameworkId) {
        viewModel.loadControls(frameworkId)
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Audit Report") },
            text = { Text("Choose a format for the $frameworkId audit report.") },
            confirmButton = {
                TextButton(onClick = {
                    showExportDialog = false
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                    pdfExportLauncher.launch("${frameworkId}_Audit_Report_$timestamp.pdf")
                }) {
                    Text("PDF Report")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExportDialog = false
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                    textExportLauncher.launch("${frameworkId}_Audit_Report_$timestamp.txt")
                }) {
                    Text("Text File")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$frameworkId Audit Checklist") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = controls,
                key = { it.id }
            ) { control ->
                ControlItem(
                    control = control,
                    onStatusChange = { newStatus ->
                        viewModel.updateControlStatus(control.id, frameworkId, newStatus, control.notes)
                    },
                    onNotesChange = { newNotes ->
                        viewModel.updateControlStatus(control.id, frameworkId, control.status, newNotes)
                    }
                )
            }

            item {
                Button(
                    onClick = { showExportDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Audit Report")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlItem(
    control: ComplianceControl,
    onStatusChange: (ComplianceStatus) -> Unit,
    onNotesChange: (String) -> Unit
) {
    var notes by remember(control.id) { mutableStateOf(control.notes) }
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 1. Control Header (ID, Name, Description)
            Text(
                text = control.id,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = control.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = control.description,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Status Dropdown (Below description, aligned to the end)
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.width(180.dp)
                ) {
                    OutlinedTextField(
                        value = when(control.status) {
                            ComplianceStatus.COMPLIANT -> "Compliant"
                            ComplianceStatus.NON_COMPLIANT -> "Non-Compliant"
                            ComplianceStatus.NONE -> "Not Audited"
                        },
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text("Status", style = MaterialTheme.typography.labelSmall) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = when(control.status) {
                                ComplianceStatus.COMPLIANT -> Color(0xFF00E676)
                                ComplianceStatus.NON_COMPLIANT -> Color(0xFFEF5350)
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            unfocusedTextColor = when(control.status) {
                                ComplianceStatus.COMPLIANT -> Color(0xFF00E676)
                                ComplianceStatus.NON_COMPLIANT -> Color(0xFFEF5350)
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        ),
                        modifier = Modifier.menuAnchor(),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        ComplianceStatus.entries.forEach { status ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = when(status) {
                                            ComplianceStatus.COMPLIANT -> "Compliant"
                                            ComplianceStatus.NON_COMPLIANT -> "Non-Compliant"
                                            ComplianceStatus.NONE -> "Not Audited"
                                        },
                                        color = when(status) {
                                            ComplianceStatus.COMPLIANT -> Color(0xFF00E676)
                                            ComplianceStatus.NON_COMPLIANT -> Color(0xFFEF5350)
                                            else -> MaterialTheme.colorScheme.onSurface
                                        },
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                onClick = {
                                    onStatusChange(status)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            // 3. Auditor Notes
            if (control.status != ComplianceStatus.NONE) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = {
                        notes = it
                        onNotesChange(it)
                    },
                    label = { Text("Auditor Notes / Evidence") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        autoCorrect = true
                    )
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ControlItemPreview() {
    RF_REAPRTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            ControlItem(
                control = ComplianceControl("NIST-3.1.1", "NIST", "Access Control Policy", "Establish and maintain an access control policy.", status = ComplianceStatus.COMPLIANT, notes = "Policy is documented in Sharepoint."),
                onStatusChange = {},
                onNotesChange = {}
            )
            Spacer(modifier = Modifier.height(16.dp))
            ControlItem(
                control = ComplianceControl("NIST-3.1.2", "NIST", "Account Management", "Manage information system accounts.", status = ComplianceStatus.NONE),
                onStatusChange = {},
                onNotesChange = {}
            )
        }
    }
}
