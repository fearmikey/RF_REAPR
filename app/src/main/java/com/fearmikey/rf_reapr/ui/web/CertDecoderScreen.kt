package com.fearmikey.rf_reapr.ui.web

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CertDecoderScreen(onBack: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("") }

    LaunchedEffect(input) {
        if (input.isBlank()) {
            resultText = ""
            return@LaunchedEffect
        }
        
        try {
            val factory = CertificateFactory.getInstance("X.509")
            val ism = ByteArrayInputStream(input.toByteArray())
            val cert = factory.generateCertificate(ism) as X509Certificate
            
            val sb = java.lang.StringBuilder()
            sb.append("Subject:\n${cert.subjectX500Principal.name}\n\n")
            sb.append("Issuer:\n${cert.issuerX500Principal.name}\n\n")
            sb.append("Valid From:\n${cert.notBefore}\n\n")
            sb.append("Valid Until:\n${cert.notAfter}\n\n")
            sb.append("Serial Number:\n${cert.serialNumber.toString(16)}\n\n")
            sb.append("Signature Algorithm:\n${cert.sigAlgName}\n\n")
            
            val sans = cert.subjectAlternativeNames
            if (sans != null) {
                sb.append("Subject Alternative Names:\n")
                sans.forEach { san ->
                    // san is a List where index 1 is the value
                    if (san.size > 1) {
                        sb.append("- ${san[1]}\n")
                    }
                }
            }
            
            resultText = sb.toString()
        } catch (e: Exception) {
            resultText = "Invalid PEM Certificate: ${e.message}"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Certificate Decoder") },
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
                value = input,
                onValueChange = { input = it },
                label = { Text("Paste PEM Certificate") },
                placeholder = { Text("-----BEGIN CERTIFICATE-----\n...") },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            if (resultText.isNotEmpty()) {
                Text("Decoded Information", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = resultText,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().weight(2f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (resultText.startsWith("Invalid")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                )
            } else {
                Row(modifier = Modifier.fillMaxWidth().weight(2f)) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Paste a Base64 PEM encoded X.509 certificate to decode it offline.")
                }
            }
        }
    }
}
