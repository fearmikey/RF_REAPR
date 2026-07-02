package com.example.rf_reapr.ui.tls

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rf_reapr.domain.model.TlsAuditResult
import com.example.rf_reapr.domain.model.TlsVulnerability
import com.example.rf_reapr.domain.repository.TlsAuditorRepository
import com.example.rf_reapr.ui.scanner.SeverityBadge
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TlsAuditorScreen(
    viewModel: TlsAuditorViewModel,
    onBack: () -> Unit
) {
    var url by remember { mutableStateOf("https://google.com") }
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SSL/TLS Auditor") },
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
                label = { Text("HTTPS URL") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { viewModel.auditUrl(url) },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState !is TlsAuditorRepository.AuditStatus.Loading
            ) {
                Text("Perform TLS Audit")
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            when (val state = uiState) {
                is TlsAuditorRepository.AuditStatus.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                is TlsAuditorRepository.AuditStatus.Success -> {
                    TlsAuditDetails(state.result)
                }
                is TlsAuditorRepository.AuditStatus.Error -> {
                    Text(
                        text = "Handshake Error: ${state.message}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                null -> {
                    Text(
                        "Analyze a server's TLS configuration for weaknesses.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}

@Composable
fun TlsAuditDetails(result: TlsAuditResult) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Connection Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    DetailRow("Protocol", result.protocol ?: "Unknown")
                    DetailRow("Cipher", result.cipherSuite ?: "Unknown")
                    DetailRow("Expiry", result.expiryDate?.let { 
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(it) 
                    } ?: "Unknown")
                    DetailRow("Issuer", result.certificateIssuer?.substringBefore(",") ?: "Unknown")
                }
            }
        }
        
        item {
            Text(
                text = if (result.vulnerabilities.isEmpty()) "No TLS vulnerabilities found" else "Security Findings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (result.vulnerabilities.isEmpty()) Color.Green else MaterialTheme.colorScheme.error
            )
        }
        
        items(result.vulnerabilities) { vuln ->
            TlsVulnerabilityCard(vuln)
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun TlsVulnerabilityCard(vuln: TlsVulnerability) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = vuln.type, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                SeverityBadge(vuln.severity)
            }
            Text(text = vuln.description, style = MaterialTheme.typography.bodySmall)
        }
    }
}
