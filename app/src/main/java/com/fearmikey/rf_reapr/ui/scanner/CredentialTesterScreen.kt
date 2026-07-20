package com.fearmikey.rf_reapr.ui.scanner

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fearmikey.rf_reapr.domain.repository.AuthProtocol
import com.fearmikey.rf_reapr.domain.repository.TestResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialTesterScreen(
    viewModel: CredentialTesterViewModel,
    onBack: () -> Unit
) {
    var target by remember { mutableStateOf("") }
    var selectedProtocol by remember { mutableStateOf(AuthProtocol.HTTP_BASIC) }
    val testResult by viewModel.testResult.collectAsState()
    val isTesting by viewModel.isTesting.collectAsState()

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

            Button(
                onClick = { if (isTesting) viewModel.reset() else viewModel.runTest(target, selectedProtocol) },
                modifier = Modifier.fillMaxWidth(),
                enabled = target.isNotBlank()
            ) {
                Icon(if (isTesting) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isTesting) "Stop Test" else "Start Test")
            }

            HorizontalDivider()

            when (val result = testResult) {
                is TestResult.Progress -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                            progress = { result.index.toFloat() / result.total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Testing: ${result.current.username}:${result.current.password}")
                        Text("${result.index + 1} / ${result.total}", style = MaterialTheme.typography.labelSmall)
                    }
                }
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
                null -> {}
            }
        }
    }
}
