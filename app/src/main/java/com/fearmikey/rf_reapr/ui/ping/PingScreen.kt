package com.fearmikey.rf_reapr.ui.ping

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fearmikey.rf_reapr.domain.repository.PingRepository.PingStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PingScreen(
    viewModel: PingViewModel,
    onBack: () -> Unit
) {
    var host by remember { mutableStateOf("google.com") }
    var continuous by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsState()
    val pingLines by viewModel.pingLines.collectAsState()
    val listState = rememberLazyListState()

    val isRunning = uiState is PingStatus.Loading || uiState is PingStatus.Progress

    // Auto-scroll to bottom when new lines arrive
    LaunchedEffect(pingLines.size) {
        if (pingLines.isNotEmpty()) {
            listState.animateScrollToItem(pingLines.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ping Tool") },
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
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Target Host or IP") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isRunning,
                trailingIcon = {
                    if (isRunning) {
                        IconButton(onClick = { viewModel.cancelPing() }) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop Ping", tint = Color.Red)
                        }
                    } else {
                        IconButton(
                            onClick = { viewModel.runPing(host, continuous) },
                            enabled = host.isNotBlank()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Run Ping")
                        }
                    }
                }
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Checkbox(
                    checked = continuous,
                    onCheckedChange = { continuous = it },
                    enabled = !isRunning
                )
                Text(
                    text = "Continuous Ping",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black, shape = MaterialTheme.shapes.medium)
                    .padding(8.dp)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(pingLines) { line ->
                        Text(
                            text = line,
                            color = Color.Green,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                    
                    if (uiState is PingStatus.Loading) {
                        item {
                            Text(
                                text = "Initializing ping...",
                                color = Color.Gray,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            if (uiState is PingStatus.Error) {
                Text(
                    text = (uiState as PingStatus.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.reset() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                enabled = !isRunning
            ) {
                Text("Clear Console")
            }
        }
    }
}
