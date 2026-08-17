package com.fearmikey.rf_reapr.ui.iperf

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IperfScreen(
    viewModel: IperfViewModel = viewModel(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val interfaces by viewModel.interfaces.collectAsState()
    val selectedInterface by viewModel.selectedInterface.collectAsState()
    val serverIp by viewModel.serverIp.collectAsState()
    val isServerMode by viewModel.isServerMode.collectAsState()
    val testOutput by viewModel.testOutput.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    // Dynamically adjust font size: smaller for narrow screens to avoid wrapping
    val consoleFontSize = when {
        screenWidth < 360 -> 9.sp
        screenWidth < 480 -> 11.sp
        else -> 12.sp
    }

    LaunchedEffect(isRunning) {
        if (!isRunning && (testOutput.contains("iperf Done.") || testOutput.contains("iperf Done"))) {
            Toast.makeText(
                context,
                "Test complete! You can export this result in Logs or add it to a Report.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("iPerf Tester") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mode Selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !isServerMode,
                    onClick = { viewModel.setServerMode(false) },
                    label = { Text("Client Mode") },
                    enabled = !isRunning,
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = isServerMode,
                    onClick = { viewModel.setServerMode(true) },
                    label = { Text("Server Mode") },
                    enabled = !isRunning,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!isServerMode) {
                OutlinedTextField(
                    value = serverIp,
                    onValueChange = { viewModel.setServerIp(it) },
                    label = { Text("Server IP (e.g. 192.168.1.10)") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isRunning
                )
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Text(
                    text = "Running in Server Mode will open port 5201 and wait for an incoming connection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Interface Dropdown
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
            ) {
                OutlinedTextField(
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    readOnly = true,
                    value = selectedInterface?.displayLabel ?: "Select Interface",
                    onValueChange = {},
                    label = { Text("Bind to Interface") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    interfaces.forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption.displayLabel) },
                            onClick = {
                                viewModel.setSelectedInterface(selectionOption)
                                expanded = false
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { viewModel.loadInterfaces() },
                modifier = Modifier.align(Alignment.End),
                enabled = !isRunning
            ) {
                Text("Refresh Interfaces")
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.startTest() },
                    modifier = Modifier.weight(1f),
                    enabled = !isRunning && (isServerMode || serverIp.isNotBlank())
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Running...")
                    } else {
                        Text("Start iPerf Test")
                    }
                }

                if (isRunning) {
                    Button(
                        onClick = { viewModel.stopTest() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(0.4f)
                    ) {
                        Text("Stop")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Test Output:",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Text(
                    text = testOutput,
                    fontFamily = FontFamily.Monospace,
                    fontSize = consoleFontSize,
                    lineHeight = (consoleFontSize.value * 1.2).sp,
                    modifier = Modifier
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }
}
