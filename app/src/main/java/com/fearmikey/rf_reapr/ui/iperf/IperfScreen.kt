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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IperfScreen(
    viewModel: IperfViewModel = viewModel(),
    onBack: () -> Unit
) {
    val interfaces by viewModel.interfaces.collectAsState()
    val selectedInterface by viewModel.selectedInterface.collectAsState()
    val serverIp by viewModel.serverIp.collectAsState()
    val testOutput by viewModel.testOutput.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()

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
            OutlinedTextField(
                value = serverIp,
                onValueChange = { viewModel.setServerIp(it) },
                label = { Text("Server IP (e.g. 192.168.1.10)") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isRunning
            )

        Spacer(modifier = Modifier.height(16.dp))

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

        Button(
            onClick = { viewModel.startTest() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRunning && serverIp.isNotBlank()
        ) {
            if (isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Running Test...")
            } else {
                Text("Start iPerf Test")
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
                modifier = Modifier
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}
}