package com.fearmikey.rf_reapr.ui.web

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fearmikey.rf_reapr.domain.repository.SubdomainFinderRepository
import com.fearmikey.rf_reapr.domain.repository.SubdomainFinderRepository.DiscoverySource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubdomainFinderScreen(
    viewModel: SubdomainFinderViewModel,
    onBack: () -> Unit,
    onNavigateToCloudScanner: (String) -> Unit
) {
    var domain by remember { mutableStateOf("google.com") }
    val subdomains by viewModel.subdomains.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val isPassiveMode by viewModel.isPassiveMode.collectAsStateWithLifecycle()
    val isFinished by viewModel.isFinished.collectAsStateWithLifecycle()
    
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val onCopy = remember { { hostname: String -> clipboardManager.setText(AnnotatedString(hostname)) } }
    val onOpen = remember {
        { hostname: String ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://$hostname"))
            context.startActivity(intent)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Subdomain Enumerator") },
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
                label = { Text("Target Domain") },
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
                        if (domain.isNotBlank() && !isPassiveMode) {
                            viewModel.startSearch(domain)
                            keyboardController?.hide()
                        }
                    }
                )
            )

            if (isPassiveMode) {
                Text(
                    "Passive Mode (Stealth). Enumeration inhibited.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    viewModel.startSearch(domain)
                    keyboardController?.hide()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = (progress == 0f || isFinished) && !isPassiveMode
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Enumerate Subdomains")
            }
            
            if (progress > 0f && !isFinished) {
                Spacer(modifier = Modifier.height(16.dp))
                SubdomainProgress(progress = progress)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            SubdomainResultsList(
                subdomains = subdomains,
                isFinished = isFinished,
                onCopy = onCopy,
                onOpen = onOpen,
                onCloudScan = onNavigateToCloudScanner
            )
        }
    }
}

@Composable
fun SubdomainResultsList(
    subdomains: SubdomainFinderRepository.SubdomainListWrapper,
    isFinished: Boolean,
    onCopy: (String) -> Unit,
    onOpen: (String) -> Unit,
    onCloudScan: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Discovered Subdomains (${subdomains.items.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        val scrollState = androidx.compose.foundation.lazy.rememberLazyListState()

        LazyColumn(
            state = scrollState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollbar(scrollState)
        ) {
            items(
                items = subdomains.items,
                key = { it.hostname },
                contentType = { "subdomain" }
            ) { item ->
                SubdomainItemRow(
                    item = item,
                    onCopy = onCopy,
                    onOpen = onOpen,
                    onCloudScan = onCloudScan
                )
            }
            
            if (isFinished && subdomains.items.isEmpty()) {
                item {
                    Text(
                        "No subdomains found with current wordlist.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun SubdomainItemRow(
    item: SubdomainFinderRepository.SubdomainItem,
    onCopy: (String) -> Unit,
    onOpen: (String) -> Unit,
    onCloudScan: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.hostname,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = if (item.source == DiscoverySource.PASSIVE)
                            MaterialTheme.colorScheme.secondaryContainer
                        else
                            MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = if (item.source == DiscoverySource.PASSIVE) "CT" else "BRUTE",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (item.source == DiscoverySource.PASSIVE)
                                MaterialTheme.colorScheme.onSecondaryContainer
                            else
                                MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                Text(
                    text = item.ipAddress ?: "No IP",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onCopy(item.hostname) },
                    tint = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Default.OpenInBrowser,
                    contentDescription = "Open",
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onOpen(item.hostname) },
                    tint = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = "Cloud Discovery",
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onCloudScan(item.hostname) },
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

fun Modifier.verticalScrollbar(
    state: androidx.compose.foundation.lazy.LazyListState,
    width: androidx.compose.ui.unit.Dp = 4.dp
): Modifier = this.then(Modifier.drawWithContent {
    drawContent()

    val layoutInfo = state.layoutInfo
    val visibleItemsInfo = layoutInfo.visibleItemsInfo
    if (visibleItemsInfo.isEmpty()) return@drawWithContent

    val totalItemsCount = layoutInfo.totalItemsCount
    val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
    if (viewportHeight <= 0) return@drawWithContent

    val firstVisibleItem = visibleItemsInfo.first()

    val totalHeight = (viewportHeight.toFloat() / visibleItemsInfo.size) * totalItemsCount
    val scrollbarHeight = (viewportHeight.toFloat() / totalHeight) * viewportHeight
    val scrollOffset = (firstVisibleItem.index.toFloat() / totalItemsCount) * viewportHeight

    drawRect(
        color = Color.Gray.copy(alpha = 0.5f),
        topLeft = Offset(size.width - width.toPx(), scrollOffset),
        size = Size(width.toPx(), scrollbarHeight),
    )
})


@Composable
fun SubdomainProgress(progress: Float) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth(),
    )
}

