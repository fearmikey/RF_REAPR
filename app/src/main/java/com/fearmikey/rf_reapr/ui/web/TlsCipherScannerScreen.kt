package com.fearmikey.rf_reapr.ui.web

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.domain.repository.TlsCipherScannerRepository.CertificateInfo
import com.fearmikey.rf_reapr.domain.repository.TlsCipherScannerRepository.CipherSuiteInfo
import com.fearmikey.rf_reapr.domain.repository.TlsCipherScannerRepository.ProtocolSupport
import com.fearmikey.rf_reapr.ui.scanner.SeverityBadge
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TlsCipherScannerScreen(
    viewModel: TlsCipherScannerViewModel,
    onBack: () -> Unit
) {
    var url by remember { mutableStateOf("google.com") }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isPassiveMode by viewModel.isPassiveMode.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    val running = uiState.progress > 0f && !uiState.isFinished

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TLS Scanner") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Host or https URL") },
                    placeholder = { Text("example.com or example.com:8443") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isPassiveMode && !running,
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search, autoCorrect = false),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (url.isNotBlank() && !isPassiveMode) { viewModel.startScan(url); keyboardController?.hide() }
                    })
                )
            }
            if (isPassiveMode) {
                item { Text("Passive (stealth) mode is on, so TLS scanning is disabled.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            item {
                Button(
                    onClick = { viewModel.startScan(url); keyboardController?.hide() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !running && !isPassiveMode && url.isNotBlank()
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (running) "Scanning…" else "Scan TLS configuration")
                }
            }

            if (running) {
                item {
                    Column {
                        LinearProgressIndicator(progress = { uiState.progress }, modifier = Modifier.fillMaxWidth())
                        Text(uiState.phase, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            uiState.error?.let { err -> item { Text(err, color = MaterialTheme.colorScheme.error) } }

            if (uiState.protocols.isNotEmpty()) {
                item { GradeCard(uiState.grade, uiState.host, uiState.port, uiState.isFinished) }
                item { ProtocolCard(uiState.protocols) }
            }
            uiState.certificate?.let { cert -> item { CertificateCard(cert) } }

            if (uiState.supportedCiphers.isNotEmpty()) {
                item { Text("Accepted cipher suites (${uiState.supportedCiphers.size})", style = MaterialTheme.typography.titleMedium) }
                items(uiState.supportedCiphers, key = { it.protocol + it.cipherSuite }) { CipherRow(it) }
            }
        }
    }
}

@Composable
private fun GradeCard(grade: String?, host: String, port: Int, finished: Boolean) {
    val color = when (grade) {
        "A+", "A" -> Color(0xFF4CAF50)
        "B" -> Color(0xFF8BC34A)
        "C" -> Color(0xFFFF9800)
        "F" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    Card {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = color, shape = MaterialTheme.shapes.medium) {
                Text(
                    grade ?: "…",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("$host:$port", style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
                Text(if (finished) "Overall TLS grade" else "Scanning…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProtocolCard(protocols: List<ProtocolSupport>) {
    Card {
        Column(Modifier.padding(12.dp)) {
            Text("Protocols", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            protocols.forEach { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.protocol, modifier = Modifier.weight(1f), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                    if (p.enabled) {
                        Text("enabled", color = if (p.riskLevel >= RiskLevel.HIGH) MaterialTheme.colorScheme.error else Color(0xFF4CAF50), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        if (p.riskLevel >= RiskLevel.HIGH) { Spacer(Modifier.width(6.dp)); SeverityBadge(p.riskLevel) }
                    } else {
                        Text("disabled", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    }
                }
                p.note?.takeIf { p.enabled }?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun CertificateCard(cert: CertificateInfo) {
    var expanded by remember { mutableStateOf(false) }
    val df = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val worst = cert.issues.maxByOrNull { it.severity.ordinal }?.severity
    Card(
        colors = if (worst != null && worst >= RiskLevel.HIGH) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer) else CardDefaults.cardColors()
    ) {
        Column(Modifier.clickable { expanded = !expanded }.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Certificate", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(cert.subject, style = MaterialTheme.typography.bodyMedium)
                    Text("Issued by ${cert.issuer}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }
            Text(
                when {
                    cert.expired -> "Expired ${df.format(Date(cert.notAfter))}"
                    cert.daysUntilExpiry < 30 -> "Expires in ${cert.daysUntilExpiry} days (${df.format(Date(cert.notAfter))})"
                    else -> "Valid until ${df.format(Date(cert.notAfter))}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (cert.daysUntilExpiry < 30 || cert.expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            cert.issues.forEach { issue ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    SeverityBadge(issue.severity)
                    Spacer(Modifier.width(6.dp))
                    Text(issue.message, style = MaterialTheme.typography.bodySmall)
                }
            }
            AnimatedVisibility(expanded) {
                SelectionContainer {
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        CertRow("Key", "${cert.keyAlgorithm} ${cert.keyBits}-bit")
                        CertRow("Signature", cert.signatureAlgorithm)
                        CertRow("Serial", cert.serialNumber)
                        CertRow("Chain length", cert.chainLength.toString())
                        CertRow("Valid from", df.format(Date(cert.notBefore)))
                        CertRow("SHA-256", cert.sha256Fingerprint)
                        if (cert.sans.isNotEmpty()) CertRow("SANs", cert.sans.joinToString(", "))
                    }
                }
            }
        }
    }
}

@Composable
private fun CertRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun CipherRow(item: CipherSuiteInfo) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.protocol, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                if (item.forwardSecrecy) Tag("FS")
                if (item.aead) Tag("AEAD")
                Spacer(Modifier.width(6.dp))
                SeverityBadge(item.riskLevel)
            }
            Text(item.cipherSuite, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
            item.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (item.riskLevel >= RiskLevel.HIGH) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun Tag(text: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.extraSmall, modifier = Modifier.padding(end = 4.dp)) {
        Text(text, Modifier.padding(horizontal = 4.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall)
    }
}
