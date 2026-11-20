package com.fearmikey.rf_reapr.ui.snmp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.domain.model.SnmpInterface
import com.fearmikey.rf_reapr.domain.model.SnmpResult
import com.fearmikey.rf_reapr.domain.service.SnmpTools
import com.fearmikey.rf_reapr.ui.scanner.SeverityBadge
import kotlinx.coroutines.launch

private enum class SnmpTab(val label: String) { OVERVIEW("Overview"), INTERFACES("Interfaces"), NETWORK("ARP / IPs"), WALK("OID walk") }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SnmpBrowserScreen(
    viewModel: SnmpViewModel,
    initialTarget: String? = null,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(initialTarget) {
        if (!initialTarget.isNullOrBlank() && viewModel.uiState.value.target.isBlank()) viewModel.updateTarget(initialTarget)
    }
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(SnmpTab.OVERVIEW) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("SNMP Browser") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.result != null) {
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(viewModel.reportText()))
                            scope.launch { snackbar.showSnackbar("Report copied") }
                        }) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy report") }
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
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = uiState.target,
                            onValueChange = viewModel::updateTarget,
                            label = { Text("Target IP or hostname") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !uiState.isBusy,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                        )
                        com.fearmikey.rf_reapr.ui.components.ScopeWarning(uiState.target)
                        OutlinedTextField(
                            value = uiState.community,
                            onValueChange = viewModel::updateCommunity,
                            label = { Text("Community string") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !uiState.isBusy
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Version", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.width(12.dp))
                            SingleChoiceSegmentedButtonRow {
                                listOf(0 to "v1", 1 to "v2c").forEachIndexed { i, (v, label) ->
                                    SegmentedButton(
                                        selected = uiState.version == v,
                                        onClick = { viewModel.updateVersion(v) },
                                        enabled = !uiState.isBusy,
                                        shape = SegmentedButtonDefaults.itemShape(i, 2)
                                    ) { Text(label) }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = viewModel::queryDevice,
                                modifier = Modifier.weight(1f),
                                enabled = !uiState.isBusy && uiState.target.isNotBlank()
                            ) {
                                if (uiState.isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Query")
                                }
                            }
                            OutlinedButton(
                                onClick = { if (uiState.isTestingCommunities) viewModel.cancel() else viewModel.testDefaultCommunities() },
                                modifier = Modifier.weight(1f),
                                enabled = uiState.isTestingCommunities || (!uiState.isBusy && uiState.target.isNotBlank())
                            ) {
                                Icon(if (uiState.isTestingCommunities) Icons.Default.Stop else Icons.Default.Key, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (uiState.isTestingCommunities) "${uiState.communityProgress}/${SnmpTools.DEFAULT_COMMUNITIES.size}" else "Test defaults")
                            }
                        }
                        if (uiState.isTestingCommunities) {
                            LinearProgressIndicator(
                                progress = { uiState.communityProgress / SnmpTools.DEFAULT_COMMUNITIES.size.toFloat() },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (uiState.communitiesTested || uiState.workingCommunities.isNotEmpty()) {
                            Text(
                                if (uiState.workingCommunities.isEmpty()) "None of the ${SnmpTools.DEFAULT_COMMUNITIES.size} default communities were accepted."
                                else "Accepted: ${uiState.workingCommunities.joinToString()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (uiState.workingCommunities.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                                fontWeight = if (uiState.workingCommunities.isEmpty()) FontWeight.Normal else FontWeight.Bold
                            )
                        }
                        uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            val result = uiState.result
            if (result != null || uiState.walkResults.isNotEmpty() || tab == SnmpTab.WALK) {
                item {
                    PrimaryScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 0.dp) {
                        SnmpTab.entries.forEach { t ->
                            Tab(selected = tab == t, onClick = { tab = t }, text = { Text(t.label) })
                        }
                    }
                }
            } else {
                item {
                    TextButton(onClick = { tab = SnmpTab.WALK }) { Text("Walk a custom OID…") }
                }
            }

            when (tab) {
                SnmpTab.OVERVIEW -> if (result != null) {
                    if (uiState.findings.isNotEmpty()) {
                        items(uiState.findings) { f ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (f.severity >= RiskLevel.MEDIUM) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(f.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                        SeverityBadge(f.severity)
                                    }
                                    Text(f.detail, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    item { SystemCard(result) }
                }
                SnmpTab.INTERFACES -> if (result != null) {
                    val up = result.interfaces.count { it.operStatus == "up" }
                    item { Text("${result.interfaces.size} interfaces · $up up", style = MaterialTheme.typography.titleMedium) }
                    items(result.interfaces.sortedWith(compareBy({ it.operStatus != "up" }, { it.index })), key = { it.index }) { InterfaceCard(it) }
                }
                SnmpTab.NETWORK -> if (result != null) {
                    item { Text("IP addresses (${result.ipAddresses.size})", style = MaterialTheme.typography.titleMedium) }
                    items(result.ipAddresses) { ip ->
                        Text("${ip.address} / ${ip.netmask ?: "?"}" + (ip.ifIndex?.let { "  (${ifName(result, it)})" } ?: ""), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                    item { Text("ARP neighbours (${result.arpTable.size})", style = MaterialTheme.typography.titleMedium) }
                    if (result.arpTable.isEmpty()) {
                        item { Text("The device didn't return an ARP table.", style = MaterialTheme.typography.bodySmall) }
                    }
                    items(result.arpTable) { e ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(e.ipAddress, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text(e.macAddress, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                SnmpTab.WALK -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SnmpTools.WALK_PRESETS.forEach { (label, oid) ->
                                    AssistChip(onClick = { viewModel.walk(oid) }, enabled = !uiState.isBusy && uiState.target.isNotBlank(), label = { Text(label) })
                                }
                            }
                            OutlinedTextField(
                                value = uiState.walkOid,
                                onValueChange = viewModel::updateWalkOid,
                                label = { Text("Root OID") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                trailingIcon = {
                                    IconButton(onClick = { viewModel.walk() }, enabled = !uiState.isBusy && uiState.target.isNotBlank()) {
                                        Icon(Icons.Default.Search, contentDescription = "Walk")
                                    }
                                }
                            )
                            if (uiState.isWalking) LinearProgressIndicator(Modifier.fillMaxWidth())
                            uiState.walkError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                            if (uiState.walkResults.isNotEmpty()) {
                                Text("${uiState.walkResults.size} values" + if (uiState.walkResults.size >= 500) " (limit reached)" else "", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    items(uiState.walkResults) { vb ->
                        SelectionContainer {
                            Column(Modifier.padding(vertical = 2.dp)) {
                                Text(vb.oid, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text("${vb.type}: ${vb.value}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ifName(result: SnmpResult, index: Int) = result.interfaces.firstOrNull { it.index == index }?.name ?: "if $index"

@Composable
private fun SystemCard(result: SnmpResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        SelectionContainer {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("System", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                InfoRow("Name", result.sysName ?: "N/A")
                InfoRow("Description", result.sysDescr ?: "N/A")
                SnmpTools.vendorFromObjectId(result.sysObjectId)?.let { InfoRow("Vendor", it) }
                InfoRow("Object ID", result.sysObjectId ?: "N/A")
                InfoRow("Uptime", result.sysUptime ?: "N/A")
                InfoRow("Contact", result.sysContact ?: "N/A")
                InfoRow("Location", result.sysLocation ?: "N/A")
                InfoRow("Query time", "${result.durationMs} ms")
            }
        }
    }
}

@Composable
fun InterfaceCard(iface: SnmpInterface) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Router, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(iface.name, style = MaterialTheme.typography.titleSmall)
                    iface.alias?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Surface(
                    color = if (iface.operStatus == "up") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        iface.operStatus.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            InfoRow("Type", "${iface.type} · MTU ${iface.mtu}")
            InfoRow("Speed", SnmpTools.formatSpeed(iface.speed))
            if (iface.physAddress.isNotBlank()) InfoRow("MAC", iface.physAddress)
            InfoRow("Traffic", "↓ ${SnmpTools.formatOctets(iface.inOctets)}  ↑ ${SnmpTools.formatOctets(iface.outOctets)}")
            if (iface.adminStatus != "up") InfoRow("Admin", iface.adminStatus)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            "$label:",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(100.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
