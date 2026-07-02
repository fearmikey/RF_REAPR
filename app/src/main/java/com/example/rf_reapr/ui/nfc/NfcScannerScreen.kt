package com.example.rf_reapr.ui.nfc

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rf_reapr.domain.model.NfcTagData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcScannerScreen(
    viewModel: NfcScannerViewModel,
    onBack: () -> Unit
) {
    val tagData by viewModel.tagData.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NFC Vulnerability Scanner") },
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (tagData == null) {
                Spacer(modifier = Modifier.height(64.dp))
                Icon(
                    imageVector = Icons.Default.Nfc,
                    contentDescription = null,
                    modifier = Modifier.size(100.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
                Text(
                    "Ready to scan",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    "Hold an NFC tag near the back of your device",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                TagInfoCard(tagData!!)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = { viewModel.clearData() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear and Scan Again")
                }
            }
        }
    }
}

@Composable
fun TagInfoCard(tag: NfcTagData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (tag.isVulnerable) {
            CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Tag Detected",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "ID: ${tag.id}", style = MaterialTheme.typography.bodyMedium)
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Technologies:", style = MaterialTheme.typography.labelLarge)
            tag.techList.forEach { tech ->
                Text(text = "• ${tech.substringAfterLast('.')}", style = MaterialTheme.typography.bodySmall)
            }
            
            if (tag.ndefMessages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "NDEF Payloads:", style = MaterialTheme.typography.labelLarge)
                tag.ndefMessages.forEach { msg ->
                    Text(text = "• $msg", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (tag.isVulnerable) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tag.vulnerabilityDescription ?: "Vulnerability detected",
                        color = Color.Red,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
