package com.fearmikey.rf_reapr.ui.traceroute

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import android.widget.Toast
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.fearmikey.rf_reapr.domain.repository.TracerouteHop
import com.fearmikey.rf_reapr.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracerouteScreen(
    viewModel: TracerouteViewModel,
    onBackClick: () -> Unit
) {
    var hostInput by remember { mutableStateOf("8.8.8.8") }
    val hops by viewModel.hops.collectAsState()
    val isPassiveMode by viewModel.isPassiveMode.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    // Auto-scroll to bottom when new hops arrive
    LaunchedEffect(hops.size) {
        if (hops.isNotEmpty()) {
            listState.animateScrollToItem(hops.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Visual Traceroute") },
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
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = hostInput,
                onValueChange = { hostInput = it },
                label = { Text("Target Host / IP") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isPassiveMode,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Search,
                    autoCorrect = false
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (hostInput.isNotBlank() && !isPassiveMode) {
                            viewModel.startTraceroute(hostInput)
                            keyboardController?.hide()
                        }
                    }
                ),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            viewModel.startTraceroute(hostInput)
                            keyboardController?.hide()
                        },
                        enabled = !isScanning && hostInput.isNotBlank() && !isPassiveMode
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Scan")
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            if (isPassiveMode) {
                Text(
                    "Passive Mode (Stealth). Traceroute inhibited.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (isScanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Tracing path to $hostInput...", style = MaterialTheme.typography.bodySmall)
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .scrollbar(listState),
                state = listState,
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(hops) { hop ->
                    HopItem(
                        hop = hop,
                        onCopyClick = { ip ->
                            clipboardManager.setText(AnnotatedString(ip))
                            Toast.makeText(
                                context,
                                context.getString(R.string.copy_to_clipboard_success, ip),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            }
        }
    }
}

fun Modifier.scrollbar(
    state: LazyListState,
    width: Dp = 4.dp
): Modifier = drawWithContent {
    drawContent()
    val layoutInfo = state.layoutInfo
    val visibleItemsInfo = layoutInfo.visibleItemsInfo
    val totalItemsCount = layoutInfo.totalItemsCount

    if (visibleItemsInfo.isEmpty() || totalItemsCount <= visibleItemsInfo.size) return@drawWithContent

    val viewportHeight = size.height

    val totalHeight = (totalItemsCount.toFloat() / visibleItemsInfo.size) * viewportHeight
    val scrollbarHeight = (viewportHeight / totalHeight) * viewportHeight
    val scrollbarOffset = (state.firstVisibleItemIndex.toFloat() / totalItemsCount) * viewportHeight

    drawRoundRect(
        color = Color.Gray.copy(alpha = 0.5f),
        topLeft = Offset(size.width - width.toPx(), scrollbarOffset),
        size = Size(width.toPx(), scrollbarHeight),
        cornerRadius = CornerRadius(width.toPx() / 2, width.toPx() / 2)
    )
}

@Composable
fun HopItem(
    hop: TracerouteHop,
    onCopyClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = hop.hopNumber.toString(), style = MaterialTheme.typography.labelSmall)
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Card(
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = hop.ip != null) {
                    hop.ip?.let { onCopyClick(it) }
                },
            colors = CardDefaults.cardColors(
                containerColor = if (hop.isFinal) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = hop.ip ?: "* * *",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (hop.ip == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                    )
                    if (hop.hostname != null) {
                        Text(text = hop.hostname, style = MaterialTheme.typography.bodySmall)
                    }
                }
                
                if (hop.latencyMs != null) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "${hop.latencyMs} ms",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}
