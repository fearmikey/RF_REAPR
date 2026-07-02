package com.example.rf_reapr.ui.rdap

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rf_reapr.domain.model.RdapDomainInfo
import com.example.rf_reapr.domain.repository.RdapRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RdapScreen(
    viewModel: RdapViewModel,
    onBack: () -> Unit
) {
    var domain by remember { mutableStateOf("google.com") }
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Domain Auditor (RDAP)") },
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
                leadingIcon = { Icon(Icons.Default.Public, contentDescription = null) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { viewModel.queryDomain(domain) },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState !is RdapRepository.RdapStatus.Loading
            ) {
                Text("Query Registration Data")
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            when (val state = uiState) {
                is RdapRepository.RdapStatus.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                is RdapRepository.RdapStatus.Success -> {
                    RdapInfoDisplay(state.info)
                }
                is RdapRepository.RdapStatus.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                RdapRepository.RdapStatus.Idle -> {
                    Text(
                        "Enter a domain to audit registration ownership and dates.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}

@Composable
fun RdapInfoDisplay(info: RdapDomainInfo) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                text = info.ldhName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Status: ${info.status.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        
        item {
            Text("Registration Events", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        
        items(info.events) { event ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = event.eventAction.capitalize(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(text = event.eventDate.substringBefore("T"), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        
        if (info.nameservers.isNotEmpty()) {
            item {
                Text("Name Servers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        info.nameservers.forEach { ns ->
                            Text(text = "• $ns", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
