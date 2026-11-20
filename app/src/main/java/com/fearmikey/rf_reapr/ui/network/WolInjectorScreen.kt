package com.fearmikey.rf_reapr.ui.network

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fearmikey.rf_reapr.domain.model.WolTarget
import com.fearmikey.rf_reapr.domain.service.MacAddressAnalyzer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WolInjectorScreen(
    viewModel: WolViewModel,
    onBack: () -> Unit
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val subnetBroadcast by viewModel.subnetBroadcast.collectAsStateWithLifecycle()
    val saved by viewModel.savedTargets.collectAsStateWithLifecycle()
    val discovered by viewModel.discoveredDevices.collectAsStateWithLifecycle()
    var showAdvanced by rememberSaveable { mutableStateOf(false) }

    val macInfo = MacAddressAnalyzer.parse(form.mac)
    val vendor = remember(form.mac) { viewModel.vendorFor(form.mac) }
    val isVerifying = status is WolStatus.Verifying

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wake-on-LAN Injector") },
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
            if (discovered.isNotEmpty()) {
                item(key = "discovered") {
                    Column {
                        Text("From Network Discovery", style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            discovered.forEach { node ->
                                SuggestionChip(
                                    onClick = { viewModel.loadDiscovered(node) },
                                    label = { Text(node.hostname ?: node.ipAddress, maxLines = 1) }
                                )
                            }
                        }
                    }
                }
            }

            item(key = "form") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = form.mac,
                        onValueChange = { v -> viewModel.updateForm { it.copy(mac = v) } },
                        label = { Text("Target MAC Address") },
                        placeholder = { Text("00:1A:2B:3C:4D:5E") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = form.mac.isNotBlank() && macInfo == null,
                        supportingText = {
                            when {
                                form.mac.isBlank() -> Text("Colon, dash, Cisco (aabb.ccdd.eeff) or bare formats")
                                macInfo == null -> Text("Needs 12 hex digits")
                                macInfo.isRandomized -> Text("Randomized MAC: WoL will only work if the OS keeps it stable", color = MaterialTheme.colorScheme.tertiary)
                                vendor != null -> Text(vendor)
                                else -> Text(macInfo.colonFormat)
                            }
                        },
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = form.broadcast,
                            onValueChange = { v -> viewModel.updateForm { it.copy(broadcast = v.trim()) } },
                            label = { Text("Broadcast IP") },
                            modifier = Modifier.weight(2f),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                            // Decimal keyboard exposes the '.' separator needed for dotted IPs.
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        var portText by remember(form.port) { mutableStateOf(form.port.toString()) }
                        OutlinedTextField(
                            value = portText,
                            onValueChange = { v ->
                                portText = v.filter { it.isDigit() }.take(5)
                                portText.toIntOrNull()?.takeIf { it in 1..65535 }?.let { p -> viewModel.updateForm { it.copy(port = p) } }
                            },
                            label = { Text("Port") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        subnetBroadcast?.let { b ->
                            FilterChip(
                                selected = form.broadcast == b,
                                onClick = { viewModel.updateForm { it.copy(broadcast = b) } },
                                label = { Text("Subnet $b") }
                            )
                        }
                        FilterChip(
                            selected = form.broadcast == WolForm.LIMITED_BROADCAST,
                            onClick = { viewModel.updateForm { it.copy(broadcast = WolForm.LIMITED_BROADCAST) } },
                            label = { Text(WolForm.LIMITED_BROADCAST) }
                        )
                    }

                    OutlinedTextField(
                        value = form.verifyIp,
                        onValueChange = { v -> viewModel.updateForm { it.copy(verifyIp = v.trim()) } },
                        label = { Text("Device IP (optional, to confirm wake-up)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )

                    TextButton(onClick = { showAdvanced = !showAdvanced }, contentPadding = PaddingValues(0.dp)) {
                        Text(if (showAdvanced) "Hide advanced options" else "Advanced options")
                    }
                    AnimatedVisibility(visible = showAdvanced) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Also send to ports 7 and 9", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = form.alsoPort7, onCheckedChange = { c -> viewModel.updateForm { it.copy(alsoPort7 = c) } })
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Repeat", modifier = Modifier.width(64.dp), style = MaterialTheme.typography.bodyMedium)
                                listOf(1, 3, 5).forEach { n ->
                                    FilterChip(
                                        selected = form.repeat == n,
                                        onClick = { viewModel.updateForm { it.copy(repeat = n) } },
                                        label = { Text("${n}×") },
                                        modifier = Modifier.padding(end = 6.dp)
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = form.secureOn,
                                onValueChange = { v -> viewModel.updateForm { it.copy(secureOn = v) } },
                                label = { Text("SecureOn password (optional)") },
                                placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = form.name,
                        onValueChange = { v -> viewModel.updateForm { it.copy(name = v) } },
                        label = { Text("Device name (for saving)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = viewModel::send,
                            modifier = Modifier.weight(1f),
                            enabled = macInfo != null && form.broadcast.isNotBlank() && !isVerifying
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Send Magic Packet")
                        }
                        OutlinedButton(onClick = viewModel::saveCurrent, enabled = macInfo != null) {
                            Icon(Icons.Default.BookmarkAdd, contentDescription = "Save device")
                        }
                    }
                }
            }

            item(key = "status") { WolStatusView(status, onCancel = viewModel::cancelVerification) }

            if (saved.isNotEmpty()) {
                item(key = "saved_header") {
                    Text("Saved devices", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                }
                items(saved, key = { it.id }) { target ->
                    SavedTargetCard(
                        target = target,
                        onLoad = { viewModel.loadTarget(target) },
                        onWake = { viewModel.wake(target) },
                        onDelete = { viewModel.deleteTarget(target) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WolStatusView(status: WolStatus, onCancel: () -> Unit) {
    when (status) {
        WolStatus.Idle -> Unit
        is WolStatus.Sent -> Text(
            "Sent ${status.packets} magic packet${if (status.packets == 1) "" else "s"} to ${status.destination}",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium
        )
        is WolStatus.Verifying -> Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Waiting for ${status.ip} to come online… ${status.secondsElapsed}s", modifier = Modifier.weight(1f))
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
                LinearProgressIndicator(
                    progress = { status.secondsElapsed.toFloat() / status.timeoutSeconds },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        is WolStatus.Awake -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("${status.ip} is awake (responded after ${status.afterSeconds}s)", fontWeight = FontWeight.Bold)
        }
        is WolStatus.NotConfirmed -> Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.width(8.dp))
            Text(
                "${status.ip} didn't respond within ${WolViewModel.VERIFY_TIMEOUT_SECONDS}s. Check that WoL is enabled in the BIOS/NIC " +
                    "settings, the device is on wired Ethernet, and the broadcast address matches its subnet.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        is WolStatus.Error -> Text(status.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SavedTargetCard(target: WolTarget, onLoad: () -> Unit, onWake: () -> Unit, onDelete: () -> Unit) {
    OutlinedCard(onClick = onLoad, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(target.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(target.mac, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                Text(
                    "${target.broadcast}:${target.port}" + (target.ip?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalIconButton(onClick = onWake) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = "Wake ${target.name}")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete ${target.name}")
            }
        }
    }
}
