package com.fearmikey.rf_reapr.ui.web

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.domain.service.TlsAnalyzer
import com.fearmikey.rf_reapr.ui.scanner.SeverityBadge
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.text.DateFormat
import java.util.Date

private data class DecodedCert(
    val fields: List<Pair<String, String>>,
    val sans: List<String>,
    val issues: List<Pair<RiskLevel, String>>,
    val error: String? = null
)

private fun decode(pem: String): DecodedCert? {
    if (pem.isBlank()) return null
    return try {
        val normalized = if (pem.contains("BEGIN CERTIFICATE")) pem else "-----BEGIN CERTIFICATE-----\n${pem.trim()}\n-----END CERTIFICATE-----"
        val factory = CertificateFactory.getInstance("X.509")
        val cert = factory.generateCertificate(ByteArrayInputStream(normalized.toByteArray())) as X509Certificate
        val now = System.currentTimeMillis()
        val days = (cert.notAfter.time - now) / 86_400_000L
        val df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        val bits = when (val pk = cert.publicKey) {
            is RSAPublicKey -> pk.modulus.bitLength()
            is ECPublicKey -> pk.params.curve.field.fieldSize
            else -> 0
        }
        val sha256 = MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString(":") { "%02X".format(it) }
        val sha1 = MessageDigest.getInstance("SHA-1").digest(cert.encoded).joinToString(":") { "%02X".format(it) }
        val selfSigned = cert.subjectX500Principal == cert.issuerX500Principal

        val issues = buildList {
            when {
                now > cert.notAfter.time -> add(RiskLevel.CRITICAL to "Expired ${-days} days ago.")
                days < 30 -> add(RiskLevel.MEDIUM to "Expires in $days days.")
            }
            if (now < cert.notBefore.time) add(RiskLevel.HIGH to "Not valid yet.")
            if (selfSigned) add(RiskLevel.MEDIUM to "Self-signed (not browser-trusted).")
            TlsAnalyzer.keyStrengthRisk(cert.publicKey.algorithm, bits).second?.let { add(TlsAnalyzer.keyStrengthRisk(cert.publicKey.algorithm, bits).first to it) }
            TlsAnalyzer.signatureRisk(cert.sigAlgName).second?.let { add(TlsAnalyzer.signatureRisk(cert.sigAlgName).first to it) }
        }

        DecodedCert(
            fields = listOf(
                "Subject" to cert.subjectX500Principal.name,
                "Issuer" to cert.issuerX500Principal.name,
                "Valid from" to df.format(cert.notBefore),
                "Valid until" to df.format(Date(cert.notAfter.time)) + "  ($days days left)",
                "Public key" to "${cert.publicKey.algorithm} $bits-bit",
                "Signature" to cert.sigAlgName,
                "Serial" to cert.serialNumber.toString(16),
                "Version" to "v${cert.version}",
                "SHA-256" to sha256,
                "SHA-1" to sha1
            ),
            sans = runCatching { cert.subjectAlternativeNames?.mapNotNull { it.getOrNull(1)?.toString() } }.getOrNull().orEmpty(),
            issues = issues
        )
    } catch (e: Exception) {
        DecodedCert(emptyList(), emptyList(), emptyList(), error = "Couldn't parse certificate: ${e.message ?: "invalid PEM/DER"}")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CertDecoderScreen(onBack: () -> Unit) {
    var input by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val decoded = remember(input) { decode(input) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Certificate Decoder") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { clipboard.getText()?.text?.let { input = it } }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Paste PEM certificate") },
                    placeholder = { Text("-----BEGIN CERTIFICATE-----\n…") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
            }

            val d = decoded
            when {
                d == null -> item {
                    Row {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Paste a Base64 PEM X.509 certificate to decode and audit it, fully offline. The body alone works too; the BEGIN/END lines are optional.")
                    }
                }
                d.error != null -> item { Text(d.error, color = MaterialTheme.colorScheme.error) }
                else -> {
                    if (d.issues.isNotEmpty()) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("Findings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    d.issues.sortedByDescending { it.first.ordinal }.forEach { (sev, msg) ->
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                                            SeverityBadge(sev)
                                            Spacer(Modifier.width(6.dp))
                                            Text(msg, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        SelectionContainer {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                d.fields.forEach { (label, value) ->
                                    Column {
                                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                    }
                                }
                                if (d.sans.isNotEmpty()) {
                                    Column {
                                        Text("Subject alternative names (${d.sans.size})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        d.sans.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
