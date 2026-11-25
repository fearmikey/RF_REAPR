package com.fearmikey.rf_reapr.ui.web

import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fearmikey.rf_reapr.domain.repository.CloudAssetScannerRepository.CloudAsset
import com.fearmikey.rf_reapr.domain.service.BucketState
import com.fearmikey.rf_reapr.domain.service.CloudProvider
import com.fearmikey.rf_reapr.ui.components.ScopeWarning

private enum class AssetFilter(val label: String) { ALL("All"), PUBLIC("Public"), PRIVATE("Private") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudAssetScannerScreen(
    viewModel: CloudAssetScannerViewModel,
    initialDomain: String? = null,
    onBack: () -> Unit
) {
    // A domain handed over by another tool is only prefilled: the scan makes hundreds of
    // requests to cloud providers, so it starts when the user asks for it.
    var domain by rememberSaveable { mutableStateOf(initialDomain.orEmpty()) }
    var keywords by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(AssetFilter.ALL) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val isPassiveMode by viewModel.isPassiveMode.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val recorded by viewModel.recordedFindings.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    val copy = rememberCopyAction()

    val planned = remember(domain, keywords, providers) { viewModel.plannedRequests(domain, keywords) }
    val canScan = planned > 0 && !isPassiveMode && !isScanning
    val startScan = {
        if (canScan) {
            keyboardController?.hide()
            viewModel.scanAssets(domain, keywords)
        }
    }
    val assets = uiState.discoveredAssets
    val shown = remember(assets, filter) {
        when (filter) {
            AssetFilter.ALL -> assets
            AssetFilter.PUBLIC -> assets.filter { it.isPublic }
            AssetFilter.PRIVATE -> assets.filterNot { it.isPublic }
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
                },
                actions = {
                    if (assets.isNotEmpty()) {
                        IconButton(onClick = {
                            copy("Results", assets.joinToString("\n") { "${it.state.label}\t${it.platform}\t${it.url}" })
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy results")
                        }
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    label = { Text("Target domain") },
                    placeholder = { Text("example.com") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isPassiveMode && !isScanning,
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Cloud, contentDescription = null) },
                    supportingText = { Text("Bucket and database names are guessed from the organisation name.") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search, autoCorrectEnabled = false
                    ),
                    keyboardActions = KeyboardActions(onSearch = { startScan() })
                )
                ScopeWarning(target = domain.trim())
            }
            item {
                OutlinedTextField(
                    value = keywords,
                    onValueChange = { keywords = it },
                    label = { Text("Extra keywords (optional)") },
                    placeholder = { Text("product names, team names, …") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isPassiveMode && !isScanning,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done)
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CloudProvider.entries.forEach { provider ->
                        FilterChip(
                            selected = provider in providers,
                            onClick = { viewModel.toggleProvider(provider) },
                            enabled = !isScanning,
                            label = { Text(provider.label) }
                        )
                    }
                }
            }

            if (isPassiveMode) {
                item { OsintNotice("Passive Mode is on, so cloud discovery is disabled.", kind = NoticeKind.ERROR) }
            }

            item {
                if (isScanning) {
                    OutlinedButton(onClick = { viewModel.stopScan() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Stop (${uiState.checked} / ${uiState.total})")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { uiState.progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    Button(onClick = startScan, modifier = Modifier.fillMaxWidth(), enabled = canScan) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (planned > 0) "Scan ($planned requests)" else "Scan")
                    }
                }
            }

            uiState.error?.let { error -> item { OsintNotice(error, kind = NoticeKind.ERROR) } }

            if (uiState.total > 0) {
                item {
                    Text(
                        text = "${assets.size} found, ${assets.count { it.isPublic }} public" +
                            (if (uiState.isFinished && !isScanning && uiState.checked < uiState.total) " (stopped at ${uiState.checked} / ${uiState.total})" else ""),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (uiState.failedProbes > 0) {
                        Text(
                            "${uiState.failedProbes} requests got no usable answer (timeouts or blocked).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (recorded > 0) item { FindingsRecordedNotice(recorded) }
                if (assets.isNotEmpty()) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssetFilter.entries.forEach { option ->
                                FilterChip(selected = filter == option, onClick = { filter = option }, label = { Text(option.label) })
                            }
                        }
                    }
                } else if (uiState.isFinished) {
                    item { OsintNotice("No storage with a name derived from this domain was found on the selected providers.") }
                }
            } else if (!isScanning && uiState.error == null) {
                item {
                    OsintNotice(
                        "A match only means the name exists, not that it belongs to the target. Confirm ownership " +
                            "before reporting, and don't download bucket contents without authorisation."
                    )
                }
            }

            items(shown, key = { it.url }) { asset -> CloudAssetCard(asset, onCopy = { copy("URL", asset.url) }) }
        }
    }
}

@Composable
private fun CloudAssetCard(asset: CloudAsset, onCopy: () -> Unit) {
    val context = LocalContext.current
    val listable = asset.state == BucketState.PUBLIC_LISTING
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (listable) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
        else CardDefaults.cardColors()
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(asset.platform, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    Surface(
                        color = when (asset.state) {
                            BucketState.PUBLIC_LISTING -> MaterialTheme.colorScheme.error
                            BucketState.PUBLIC_READ -> Color(0xFFFF9800)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            asset.state.label.uppercase(),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (asset.isPublic) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(asset.url, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                Text(
                    asset.status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (listable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (asset.sampleKeys.isNotEmpty()) {
                    Text(
                        "Contains: ${asset.sampleKeys.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy URL")
            }
            IconButton(onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, asset.url.toUri()))
                } catch (_: Exception) {
                    Toast.makeText(context, "No browser available", Toast.LENGTH_SHORT).show()
                }
            }) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = "Open in browser")
            }
        }
    }
}
