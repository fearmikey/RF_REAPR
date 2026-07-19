package com.fearmikey.rf_reapr.ui.physical

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HidInjectorScreen(
    viewModel: HidInjectorViewModel,
    onBack: () -> Unit,
    onNavigateToAssets: () -> Unit
) {
    val scripts by viewModel.scripts.collectAsState()
    val currentScript by viewModel.currentScript.collectAsState()
    val scriptName by viewModel.scriptName.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val isExecuting by viewModel.isExecuting.collectAsState()

    val context = LocalContext.current
    var showScriptList by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let { viewModel.importScript(it, context) }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HID Injector") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        importLauncher.launch(arrayOf("text/plain", "application/octet-stream"))
                    }) {
                        Icon(Icons.Default.FileUpload, contentDescription = "Import")
                    }
                    TextButton(onClick = { showScriptList = true }) {
                        Text("Scripts", color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick = onNavigateToAssets) {
                        Text("Assets", color = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(
                        onClick = { viewModel.runScript() },
                        enabled = !isExecuting && currentScript.isNotBlank()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Run", tint = if (isExecuting) Color.Gray else MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = scriptName,
                onValueChange = { viewModel.onNameChange(it) },
                label = { Text("Script Name") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { viewModel.saveScript() }) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = currentScript,
                onValueChange = { viewModel.onScriptChange(it) },
                label = { Text("DuckyScript") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                placeholder = { Text("STRING Hello World\nENTER") },
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("Execution Logs", style = MaterialTheme.typography.titleMedium)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp)
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(logs) { log ->
                        Text(
                            log,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                if (logs.isNotEmpty()) {
                    TextButton(
                        onClick = { viewModel.clearLogs() },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Text("Clear")
                    }
                }
            }
        }

        if (showScriptList) {
            AlertDialog(
                onDismissRequest = { showScriptList = false },
                title = { 
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Saved Scripts")
                        IconButton(onClick = { 
                            importLauncher.launch(arrayOf("text/plain", "application/octet-stream"))
                            showScriptList = false
                        }) {
                            Icon(Icons.Default.FileUpload, contentDescription = "Import")
                        }
                    }
                },
                text = {
                    if (scripts.isEmpty()) {
                        Text("No scripts saved yet.")
                    } else {
                        LazyColumn {
                            items(scripts) { script ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.loadScript(script)
                                            showScriptList = false
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(script.name, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { viewModel.deleteScript(script) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showScriptList = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}
