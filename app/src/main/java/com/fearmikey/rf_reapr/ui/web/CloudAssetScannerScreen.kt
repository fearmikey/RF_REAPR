package com.fearmikey.rf_reapr.ui.web

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudAssetScannerScreen(
    viewModel: CloudAssetScannerViewModel,
    initialDomain: String? = null,
    onBack: () -> Unit
) {
    var domain by remember { mutableStateOf(initialDomain ?: "google.com") }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isPassiveMode by viewModel.isPassiveMode.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(initialDomain) {
        if (!initialDomain.isNullOrBlank()) {
            viewModel.scanAssets(initialDomain)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloud Asset Discovery") },
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
                label = { Text("Target Domain (for bucket patterns)") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isPassiveMode,
                leadingIcon = { Icon(Icons.Default.Cloud, contentDescription = null) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Search,
                    autoCorrect = false
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (domain.isNotBlank() && !isPassiveMode) {
                            viewModel.scanAssets(domain)
                            keyboardController?.hide()
                        }
                    }
                )
            )

            if (isPassiveMode) {
                Text(
                    "Passive Mode (Stealth). Cloud discovery inhibited.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    viewModel.scanAssets(domain)
                    keyboardController?.hide()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = (uiState.progress == 0f || uiState.isFinished) && !isPassiveMode
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Search Cloud Buckets")
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
                text = "Potential Cloud Assets (${uiState.discoveredAssets.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.discoveredAssets) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (item.isPublic) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)) else CardDefaults.cardColors()
                    ) {
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
                                    Text(item.platform, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    if (item.isPublic) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.error,
                                            shape = MaterialTheme.shapes.small
                                        ) {
                                            Text(
                                                "PUBLIC",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                                Text(item.url, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text(item.status, style = MaterialTheme.typography.bodySmall, color = if (item.isPublic) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            
                            Row {
                                IconButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(item.url))
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy URL")
                                }
                                IconButton(onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
                                    context.startActivity(intent)
                                }) {
                                    Icon(Icons.Default.OpenInBrowser, contentDescription = "Open in Browser")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
