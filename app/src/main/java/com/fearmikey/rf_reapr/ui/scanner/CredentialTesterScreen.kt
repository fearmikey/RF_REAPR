package com.fearmikey.rf_reapr.ui.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fearmikey.rf_reapr.domain.repository.AuthProtocol
import com.fearmikey.rf_reapr.domain.repository.TestResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialTesterScreen(
    viewModel: CredentialTesterViewModel,
    initialIp: String? = null,
    onBack: () -> Unit
) {
    var target by remember { mutableStateOf(initialIp ?: "") }
    var selectedProtocol by remember { mutableStateOf(AuthProtocol.WEB_AUTO) }
    val testResult by viewModel.testResult.collectAsState()
    val currentProgress by viewModel.progress.collectAsState()
    val isTesting by viewModel.isTesting.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Credential Tester") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = target,
                onValueChange = { target = it },
                label = { Text("Target IP or Hostname") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isTesting
            )

            Text("Select Protocol", style = MaterialTheme.typography.labelLarge)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AuthProtocol.entries.forEach { protocol ->
                    FilterChip(
                        selected = selectedProtocol == protocol,
                        onClick = { if (!isTesting) selectedProtocol = protocol },
                        label = { Text(protocol.name) }
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.runTest(target, selectedProtocol) },
                    modifier = Modifier.weight(1f),
                    enabled = target.isNotBlank() && !isTesting
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Test")
                }

                if (isTesting) {
                    Button(
                        onClick = { viewModel.stopTest() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Stop Test")
                    }
                } else if (logs.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { viewModel.reset() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear Logs")
                    }
                }
            }

            // Terminal Log View
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
                    items(logs) { log ->
                        Text(
                            text = log,
                            color = when {
                                log.contains("SUCCESS") -> Color.Green
                                log.contains("ERROR") -> Color.Red
                                log.contains("FAILED") -> Color(0xFFFFA500) // Orange for failed attempts
                                else -> Color.LightGray
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }

            HorizontalDivider()

            // Progress/Result Summary
            if (isTesting && currentProgress != null) {
                val progress = currentProgress!!
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = { progress.index.toFloat() / progress.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Testing: ${progress.current.username}:${progress.current.password}", style = MaterialTheme.typography.labelSmall)
                    Text("${progress.index + 1} / ${progress.total}", style = MaterialTheme.typography.labelSmall)
                }
            } else {
                when (val result = testResult) {
                    is TestResult.Success -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            ListItem(
                                headlineContent = { Text("Success!") },
                                supportingContent = { Text("Credentials found: ${result.credential.username} / ${result.credential.password}") },
                                leadingContent = { Icon(Icons.Default.LockOpen, contentDescription = null) }
                            )
                        }
                    }
                    is TestResult.Finished -> {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("Test finished. No credentials found.")
                        }
                    }
                    is TestResult.Failure -> {
                        Text("Error: ${result.message}", color = MaterialTheme.colorScheme.error)
                    }
                    else -> {}
                }
            }
        }
    }
}
