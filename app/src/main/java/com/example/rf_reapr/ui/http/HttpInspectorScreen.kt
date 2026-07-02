package com.example.rf_reapr.ui.http

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rf_reapr.domain.model.HttpSecurityResult
import com.example.rf_reapr.domain.model.SecurityMisconfiguration
import com.example.rf_reapr.domain.repository.HttpInspectorRepository
import com.example.rf_reapr.ui.scanner.SeverityBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HttpInspectorScreen(
    viewModel: HttpInspectorViewModel,
    onBack: () -> Unit
) {
    var url by remember { mutableStateOf("https://google.com") }
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HTTP/HTTPS Inspector") },
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
                value = url,
                onValueChange = { url = it },
                label = { Text("Target URL") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { viewModel.inspectUrl(url) },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState !is HttpInspectorRepository.InspectorResult.Loading
            ) {
                Text("Analyze Headers")
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            when (val state = uiState) {
                is HttpInspectorRepository.InspectorResult.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                is HttpInspectorRepository.InspectorResult.Success -> {
                    HeaderAnalysisResults(state.result)
                }
                is HttpInspectorRepository.InspectorResult.Error -> {
                    Text(
                        text = "Error: ${state.message}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                null -> {
                    Text(
                        "Enter a URL to analyze its security headers.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}

@Composable
fun HeaderAnalysisResults(result: HttpSecurityResult) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                "Status: ${result.statusCode}",
                style = MaterialTheme.typography.titleMedium,
                color = if (result.statusCode < 400) Color.Green else Color.Red
            )
            Text(
                "Found ${result.misconfigurations.size} potential issues",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
        }
        
        items(result.misconfigurations) { misconfig ->
            MisconfigurationCard(misconfig)
        }
    }
}

@Composable
fun MisconfigurationCard(misconfig: SecurityMisconfiguration) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = misconfig.header,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                SeverityBadge(misconfig.severity)
            }
            Text(
                text = misconfig.issue,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Recommendation:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = misconfig.recommendation,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
