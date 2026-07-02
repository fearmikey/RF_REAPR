package com.example.rf_reapr.ui.dns

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rf_reapr.domain.model.DnsEnumerationResult
import com.example.rf_reapr.domain.model.DnsRecord
import com.example.rf_reapr.domain.repository.DnsEnumeratorRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsEnumeratorScreen(
    viewModel: DnsEnumeratorViewModel,
    onBack: () -> Unit
) {
    var domain by remember { mutableStateOf("google.com") }
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DNS Enumerator") },
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
                value = domain,
                onValueChange = { domain = it },
                label = { Text("Domain Name") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { viewModel.enumerateDomain(domain) },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState !is DnsEnumeratorRepository.EnumerationStatus.Loading
            ) {
                Text("Enumerate Records")
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            when (val state = uiState) {
                is DnsEnumeratorRepository.EnumerationStatus.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                is DnsEnumeratorRepository.EnumerationStatus.Success -> {
                    DnsResultsList(state.result)
                }
                is DnsEnumeratorRepository.EnumerationStatus.Error -> {
                    Text(
                        text = "Error: ${state.message}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                null -> {
                    Text(
                        "Enter a domain to map its DNS infrastructure.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}

@Composable
fun DnsResultsList(result: DnsEnumerationResult) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(
                "Results for ${result.domain}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        items(result.records) { record ->
            DnsRecordCard(record)
        }
    }
}

@Composable
fun DnsRecordCard(record: DnsRecord) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = record.type.name,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = record.value,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (record.priority != null) {
                    Text(
                        text = "Priority: ${record.priority}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
