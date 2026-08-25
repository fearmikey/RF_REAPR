package com.fearmikey.rf_reapr.ui.dns

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LockPerson
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsAuditorScreen(
    viewModel: DnsAuditorViewModel,
    onBackClick: () -> Unit
) {
    val result by viewModel.auditResult.collectAsStateWithLifecycle()
    val isPassiveMode by viewModel.isPassiveMode.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DNS Security Auditor") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
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
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            if (isLoading) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Analyzing DNS resolution...")
            } else if (result != null) {
                AuditResultCard(result!!)
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = { viewModel.runAudit() },
                    enabled = !isPassiveMode
                ) {
                    Text("Re-Run Audit")
                }
            } else {
                Text(
                    "Compare your system DNS results against trusted providers to detect hijacking or leaks.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                
                if (isPassiveMode) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            "Passive Mode (Stealth). DNS auditing inhibited.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = { viewModel.runAudit() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isPassiveMode
                ) {
                    Icon(Icons.Default.LockPerson, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start DNS Audit")
                }
            }
        }
    }
}

@Composable
fun AuditResultCard(result: com.fearmikey.rf_reapr.domain.repository.DnsAuditResult) {
    val isHijacked = result.isHijacked
    val isLocalRedir = result.isLocalRedirection
    val isInconclusive = result.isInconclusive
    var showDiagnostics by remember { mutableStateOf(false) }
    
    val cardColor = when {
        isInconclusive -> MaterialTheme.colorScheme.surfaceVariant
        isHijacked -> MaterialTheme.colorScheme.errorContainer
        isLocalRedir -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val statusText = when {
        isInconclusive -> "AUDIT INCONCLUSIVE"
        isHijacked -> "DNS HIJACKING SUSPECTED"
        isLocalRedir -> "LOCAL DNS REDIRECTION"
        else -> "DNS Resolution Secure"
    }
    
    val statusIcon = when {
        isInconclusive -> Icons.Default.Error
        isHijacked -> Icons.Default.Error
        isLocalRedir -> Icons.Default.Security
        else -> Icons.Default.CheckCircle
    }
    
    val statusColor = when {
        isInconclusive -> MaterialTheme.colorScheme.outline
        isHijacked -> Color.Red
        isLocalRedir -> MaterialTheme.colorScheme.tertiary
        else -> Color.Green
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = statusColor
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isHijacked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }
            
            if (isInconclusive) {
                Spacer(modifier = Modifier.height(8.dp))
                val errorMessage = if (result.trustedError?.contains("dns.google") == true || result.trustedError?.contains("cloudflare") == true) {
                    "Your network is blocking DoH provider hostnames. The app is attempting to use bootstrap IPs (1.1.1.1/8.8.8.8) to verify your resolution."
                } else {
                    "The app was unable to complete the audit. This can happen if your network or Private DNS settings are blocking resolution tests."
                }
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (isLocalRedir) {
                Spacer(modifier = Modifier.height(8.dp))
                val reason = if (result.isPrivateIp) {
                    "Resolved to a private IP address (${result.systemIp}). This is a strong indicator of local network management like pfSense, pfBlockerNG, or an ad-blocker."
                } else {
                    "Resolution is pointing to your own DNS server. This is common with firewalls (like pfSense), ad-blockers, or captive portals."
                }
                
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                
                if (result.privateDnsMode != null && result.privateDnsMode != "off") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Private DNS is active (${result.privateDnsMode}). This can sometimes mask local DNS server IPs from the system's standard view.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            Text("System DNS Servers:", style = MaterialTheme.typography.labelSmall)
            result.systemDnsServers.forEach { server ->
                Text(text = "• $server", style = MaterialTheme.typography.bodySmall)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Local Result", style = MaterialTheme.typography.labelSmall)
                    Text(result.systemIp.ifEmpty { "Failed" }, style = MaterialTheme.typography.bodyMedium)
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("Trusted Results", style = MaterialTheme.typography.labelSmall)
                    if (result.trustedIps.isEmpty()) {
                        Text("Failed", style = MaterialTheme.typography.bodySmall)
                    } else {
                        result.trustedIps.take(3).forEach { ip ->
                            Text(ip, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            TextButton(onClick = { showDiagnostics = !showDiagnostics }) {
                Text(if (showDiagnostics) "Hide Diagnostic Info" else "Show Diagnostic Info")
            }

            if (showDiagnostics) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    DiagnosticRow("Test Domain", result.testDomain)
                    DiagnosticRow("Private DNS", result.privateDnsMode ?: "unknown")
                    DiagnosticRow("Is Private IP", result.isPrivateIp.toString())
                    DiagnosticRow("In Server List", result.isInDnsServerList.toString())
                    DiagnosticRow("Resolution Match", result.resolutionMatch.toString())
                    DiagnosticRow("Local Redir", result.isLocalRedirection.toString())
                    DiagnosticRow("System Error", result.systemError ?: "none")
                    DiagnosticRow("Trusted Error", result.trustedError ?: "none")
                    DiagnosticRow("Resolved Host", result.resolvedHostname ?: "none")
                    DiagnosticRow("Can Resolve", result.canResolveKnownDomain.toString())
                    DiagnosticRow("Inconclusive", result.isInconclusive.toString())
                }
            }
        }
    }
}

@Composable
fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
