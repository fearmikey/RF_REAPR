package com.fearmikey.rf_reapr.ui.network

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacLookupScreen(
    viewModel: MacLookupViewModel,
    onBack: () -> Unit
) {
    val databaseSize by viewModel.databaseSize.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val single by viewModel.single.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val bulkInput by viewModel.bulkInput.collectAsStateWithLifecycle()
    val bulkResult by viewModel.bulkResult.collectAsStateWithLifecycle()

    var bulkMode by rememberSaveable { mutableStateOf(false) }

    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val copy: (String, String) -> Unit = { label, value ->
        clipboard.setText(AnnotatedString(value))
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("MAC OUI Lookup")
                        Text(
                            text = databaseSize?.let { "$it vendor prefixes · offline" } ?: "Loading database…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            item(key = "mode") {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !bulkMode,
                        onClick = { bulkMode = false },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text("Single") }
                    SegmentedButton(
                        selected = bulkMode,
                        onClick = { bulkMode = true },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text("Bulk") }
                }
            }

            if (!bulkMode) {
                item(key = "single_input") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::onQueryChange,
                        label = { Text("MAC address or OUI prefix") },
                        placeholder = { Text("B8:27:EB:12:34:56 or b827.eb12.3456") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                        isError = single is SingleLookupState.Invalid,
                        supportingText = (single as? SingleLookupState.Invalid)?.let { { Text(it.message) } },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onQueryChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(onSearch = {
                            viewModel.commitQuery()
                            keyboard?.hide()
                        })
                    )
                }

                (single as? SingleLookupState.Result)?.let { result ->
                    item(key = "single_result") {
                        MacResultCard(entry = result.entry, onCopy = copy)
                    }
                }

                if (history.isNotEmpty()) {
                    item(key = "history_header") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Recent lookups", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = viewModel::clearHistory) { Text("Clear") }
                        }
                    }
                    items(history, key = { "h_${it.mac?.normalized ?: it.oui}" }) { entry ->
                        MacEntryRow(entry = entry, onClick = { viewModel.onQueryChange(entry.input) })
                    }
                }
            } else {
                item(key = "bulk_input") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = bulkInput,
                            onValueChange = viewModel::onBulkInputChange,
                            label = { Text("Paste text containing MAC addresses") },
                            placeholder = { Text("ARP tables, DHCP leases, router logs…") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 5,
                            maxLines = 10,
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                clipboard.getText()?.text?.let(viewModel::onBulkInputChange)
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Paste")
                            }
                            Button(
                                onClick = {
                                    viewModel.runBulkLookup()
                                    keyboard?.hide()
                                },
                                enabled = bulkInput.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) { Text("Look up all") }
                            if (bulkInput.isNotEmpty() || bulkResult != null) {
                                TextButton(onClick = viewModel::clearBulk) { Text("Clear") }
                            }
                        }
                    }
                }

                bulkResult?.let { result ->
                    item(key = "bulk_summary") {
                        if (result.entries.isEmpty()) {
                            Text(
                                "No MAC addresses found in the text.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${result.entries.size} addresses · ${result.vendorCount} vendors · " +
                                        "${result.randomizedCount} randomized · ${result.unknownCount} unknown",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { copy("Results (CSV)", MacLookupViewModel.toCsv(result.entries)) }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy results as CSV")
                                }
                            }
                        }
                    }
                    items(result.entries, key = { "b_${it.mac?.normalized ?: it.input}" }) { entry ->
                        MacEntryRow(entry = entry, onClick = { copy("MAC", entry.mac?.colonFormat ?: entry.input) })
                    }
                }
            }

            item(key = "footer") {
                Text(
                    text = "Lookups run fully offline against the built-in vendor table plus assets/oui.json " +
                        "(drop in a full IEEE OUI export to extend it). Randomized (locally administered) " +
                        "addresses are generated by the device's OS, not assigned by a vendor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MacResultCard(entry: MacLookupEntry, onCopy: (String, String) -> Unit) {
    val mac = entry.mac
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            mac?.isBroadcast == true -> "Broadcast address"
                            entry.vendor != null -> entry.vendor
                            entry.isRandomized -> "Randomized address"
                            mac?.isMulticast == true -> "Multicast group"
                            else -> "Unknown vendor"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "OUI ${entry.ouiColon}" + if (entry.vendor == null) " · not in offline database" else "",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                entry.vendor?.let {
                    IconButton(onClick = { onCopy("Vendor", it) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy vendor")
                    }
                }
            }

            if (mac != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MacFlag(if (mac.isMulticast) "Multicast" else "Unicast")
                    MacFlag(
                        text = if (mac.isLocallyAdministered) "Locally administered" else "Vendor-assigned",
                        highlight = mac.isRandomized
                    )
                }
                if (mac.isRandomized) {
                    Text(
                        text = "This looks like a private/randomized MAC (Android, iOS, Windows and macOS " +
                            "randomize per network). It can't identify the hardware vendor and may change over time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                HorizontalDivider()
                FormatRow("Colon", mac.colonFormat, onCopy)
                FormatRow("Hyphen", mac.dashFormat, onCopy)
                FormatRow("Cisco", mac.ciscoFormat, onCopy)
                FormatRow("Bare", mac.normalized, onCopy)
            } else {
                Text(
                    text = "Prefix lookup. Enter all 12 digits to see address type and formats.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FormatRow(label: String, value: String, onCopy: (String, String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Copy $label format") { onCopy("$label format", value) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Default.ContentCopy,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun MacFlag(text: String, highlight: Boolean = false) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (highlight) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun MacEntryRow(entry: MacLookupEntry, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.mac?.colonFormat ?: entry.ouiColon,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = entry.vendor ?: if (entry.isRandomized) "Randomized (private)" else "Unknown vendor",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (entry.vendor != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (entry.isRandomized) {
                MacFlag("Random", highlight = true)
            } else if (entry.mac?.isMulticast == true) {
                MacFlag("Multicast")
            }
        }
    }
}
