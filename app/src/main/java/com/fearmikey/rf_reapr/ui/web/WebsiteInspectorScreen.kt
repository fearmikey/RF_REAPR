package com.fearmikey.rf_reapr.ui.web

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.fearmikey.rf_reapr.domain.model.*
import com.fearmikey.rf_reapr.domain.repository.WebsiteInspectorRepository.InspectorStatus
import com.fearmikey.rf_reapr.ui.scanner.SeverityBadge
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebsiteInspectorScreen(
    viewModel: WebsiteInspectorViewModel,
    onBack: () -> Unit
) {
    var url by remember { mutableStateOf("https://google.com") }
    val uiState by viewModel.uiState.collectAsState()
    val isPassiveMode by viewModel.isPassiveMode.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Website Inspector") },
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
                label = { Text("Target URL or Domain") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isPassiveMode,
                leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Search,
                    autoCorrect = false
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (!isPassiveMode) {
                            viewModel.inspectWebsite(url)
                            keyboardController?.hide()
                        }
                    }
                )
            )

            if (isPassiveMode) {
                Text(
                    "Passive Mode (Stealth). Web auditing inhibited.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    viewModel.inspectWebsite(url)
                    keyboardController?.hide()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isPassiveMode
            ) {
                Text("Run Comprehensive Audit")
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    InspectorSection(
                        title = "HTTP Security Headers",
                        icon = Icons.Default.Http,
                        status = uiState.httpStatus
                    ) { result ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Status Code: ${result.statusCode}",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (result.statusCode < 400) Color.Green else Color.Red
                            )
                            result.misconfigurations.forEach { MisconfigurationCard(it) }
                        }
                    }
                }

                item {
                    InspectorSection(
                        title = "SSL/TLS Configuration",
                        icon = Icons.Default.Lock,
                        status = uiState.tlsStatus
                    ) { result ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    DetailRow("Protocol", result.protocol ?: "Unknown")
                                    DetailRow("Cipher", result.cipherSuite ?: "Unknown")
                                    DetailRow("Expiry", result.expiryDate?.let { 
                                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(it) 
                                    } ?: "Unknown")
                                }
                            }
                            result.vulnerabilities.forEach { TlsVulnerabilityCard(it) }
                        }
                    }
                }

                item {
                    InspectorSection(
                        title = "DNS Infrastructure",
                        icon = Icons.Default.Dns,
                        status = uiState.dnsStatus
                    ) { result ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            result.records.forEach { DnsRecordCard(it) }
                        }
                    }
                }

                item {
                    InspectorSection(
                        title = "Domain Registration (RDAP)",
                        icon = Icons.Default.Public,
                        status = uiState.rdapStatus
                    ) { result ->
                        RdapInfoDisplay(result)
                    }
                }
            }
        }
    }
}

@Composable
fun <T> InspectorSection(
    title: String,
    icon: ImageVector,
    status: InspectorStatus<T>,
    content: @Composable (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (status) {
                        is InspectorStatus.Loading -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        is InspectorStatus.Success -> Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = Color.Green, modifier = Modifier.size(20.dp))
                        is InspectorStatus.Error -> Icon(Icons.Default.Error, contentDescription = "Error", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        InspectorStatus.Idle -> {}
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            }
            
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    when (status) {
                        is InspectorStatus.Success -> content(status.data)
                        is InspectorStatus.Error -> Text(status.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        is InspectorStatus.Loading -> Text("Loading...", style = MaterialTheme.typography.bodySmall)
                        InspectorStatus.Idle -> Text("Start audit to see results", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
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

@Composable
fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(text = value, style = MaterialTheme.typography.bodySmall)
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

@Composable
fun RdapInfoDisplay(info: RdapDomainInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = info.ldhName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Status: ${info.status.joinToString(", ")}",
            style = MaterialTheme.typography.bodySmall
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        Text("Registration Events", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        
        info.events.forEach { event ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = event.eventAction.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(text = event.eventDate.substringBefore("T"), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        
        if (info.nameservers.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Name Servers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
