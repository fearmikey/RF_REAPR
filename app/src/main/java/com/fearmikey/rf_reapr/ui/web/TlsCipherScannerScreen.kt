package com.fearmikey.rf_reapr.ui.web

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fearmikey.rf_reapr.ui.scanner.SeverityBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TlsCipherScannerScreen(
    viewModel: TlsCipherScannerViewModel,
    onBack: () -> Unit
) {
    var url by remember { mutableStateOf("https://google.com") }
    val uiState by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TLS Cipher Scanner") },
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
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Search,
                    autoCorrect = false
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (url.isNotBlank()) {
                            viewModel.startScan(url)
                            keyboardController?.hide()
                        }
                    }
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    viewModel.startScan(url)
                    keyboardController?.hide()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.progress == 0f || uiState.isFinished
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan Cipher Suites")
            }
            
            if (uiState.progress > 0f && !uiState.isFinished) {
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { uiState.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Supported Cipher Suites (${uiState.supportedCiphers.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.supportedCiphers) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(item.protocol, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    SeverityBadge(item.riskLevel)
                                }
                                Text(item.cipherSuite, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                item.reason?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                            
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(item.cipherSuite))
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Cipher Name")
                            }
                        }
                    }
                }
            }
        }
    }
}
