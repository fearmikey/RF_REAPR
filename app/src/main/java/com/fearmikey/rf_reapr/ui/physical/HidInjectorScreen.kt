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
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.fearmikey.rf_reapr.domain.model.HidPayload

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
    val isPassiveMode by viewModel.isPassiveMode.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val isFlashing by viewModel.isFlashing.collectAsState()

    val context = LocalContext.current
    var showScriptList by remember { mutableStateOf(false) }
    var scriptToFlash by remember { mutableStateOf<HidPayload?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let { viewModel.importScript(it, context) }
        }
    )

    val flashLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
        onResult = { uri ->
            uri?.let { viewModel.flashScript(it, context) }
        }
    )

    val flashSavedLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
        onResult = { uri ->
            uri?.let { 
                scriptToFlash?.let { payload ->
                    viewModel.flashSavedScript(payload, it, context)
                }
            }
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
                        onClick = { flashLauncher.launch("payload.txt") },
                        enabled = !isFlashing && currentScript.isNotBlank() && !isPassiveMode
                    ) {
                        Icon(
                            imageVector = Icons.Default.Usb,
                            contentDescription = "Flash to USB",
                            tint = if (isFlashing || isPassiveMode) Color.Gray else MaterialTheme.colorScheme.primary
                        )
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
            if (isPassiveMode) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        "Passive Mode (Stealth). USB HID injection inhibited.",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

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

            Text("Operation Logs", style = MaterialTheme.typography.titleMedium)
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
                                    Row {
                                        IconButton(onClick = { 
                                            scriptToFlash = script
                                            flashSavedLauncher.launch("${script.name}.txt")
                                        }) {
                                            Icon(Icons.Default.Usb, contentDescription = "Flash", tint = MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(onClick = { viewModel.deleteScript(script) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                        }
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
