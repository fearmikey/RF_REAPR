package com.fearmikey.rf_reapr.ui.wireless

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.repository.FindingRepository
import com.fearmikey.rf_reapr.domain.repository.GattInspection
import com.fearmikey.rf_reapr.domain.repository.GattInspectorRepository
import com.fearmikey.rf_reapr.domain.repository.GattPhase
import com.fearmikey.rf_reapr.domain.repository.LogRepository
import com.fearmikey.rf_reapr.domain.service.ActiveEngagement
import com.fearmikey.rf_reapr.domain.service.GattAnalyzer
import com.fearmikey.rf_reapr.domain.service.GattCatalog
import com.fearmikey.rf_reapr.domain.service.GattServiceInfo
import com.fearmikey.rf_reapr.domain.service.ScopeMatcher
import com.fearmikey.rf_reapr.ui.scanner.SeverityBadge
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GattInspectorViewModel(
    private val repository: GattInspectorRepository,
    private val findingRepository: FindingRepository? = null,
    private val logRepository: LogRepository? = null
) : ViewModel() {

    private val _state = MutableStateFlow<GattInspection?>(null)
    val state: StateFlow<GattInspection?> = _state.asStateFlow()

    private var job: Job? = null

    val isRunning: Boolean get() = job?.isActive == true

    /** Connects to [address] and enumerates it. Starting again cancels (and disconnects) a run in progress. */
    fun inspect(address: String) {
        job?.cancel()
        job = viewModelScope.launch {
            repository.inspect(address).collect { inspection ->
                _state.value = inspection
                if (inspection.phase == GattPhase.DONE) {
                    findingRepository?.record(GattAnalyzer.toDrafts(inspection.name.orEmpty(), inspection.address, inspection.findings))
                    logRepository?.saveLog(
                        type = "BLE",
                        summary = "GATT inspection of ${inspection.name ?: inspection.address}: " +
                            "${inspection.services.size} services, ${inspection.findings.size} findings",
                        // Same shape as BLE scan logs (a JSON array) so report readers don't choke on it.
                        detailJson = "[]"
                    )
                }
            }
        }
    }

    override fun onCleared() {
        // Cancelling the collection disconnects from the device.
        job?.cancel()
        super.onCleared()
    }
}

/** Read-only GATT enumeration of one BLE device. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GattInspectorScreen(viewModel: GattInspectorViewModel, address: String, deviceName: String?, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var consented by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.inspect(address) else permissionDenied = true
    }
    val start: () -> Unit = {
        consented = true
        permissionDenied = false
        val needsRuntimePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        if (needsRuntimePermission) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            viewModel.inspect(address)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("GATT Inspector")
                        Text(
                            listOfNotNull(state?.name ?: deviceName, address).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    val phase = state?.phase
                    if (phase == GattPhase.DONE || phase == GattPhase.FAILED) {
                        IconButton(onClick = start) { Icon(Icons.Default.Refresh, contentDescription = "Inspect again") }
                    }
                }
            )
        }
    ) { padding ->
        val inspection = state
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!consented) {
                item(key = "consent") {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.BluetoothSearching, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Connect to this device?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "RF-REAPR will open a Bluetooth LE connection, list the device's services and characteristics, read its " +
                                    "standard information values (name, model, firmware, battery) and disconnect. It never writes to the " +
                                    "device, never subscribes to notifications and never starts pairing.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "Connecting is an active step the device's owner can notice. Only inspect devices you own or are authorized to test.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            ActiveEngagement.current.collectAsStateWithLifecycle().value?.let { engagement ->
                                if (engagement.scopeText.isNotBlank()) {
                                    Text(
                                        "Active project: ${engagement.name}. Bluetooth devices can't be matched against its network scope, so confirm this one is covered.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Button(onClick = start, modifier = Modifier.fillMaxWidth()) { Text("Connect and inspect") }
                        }
                    }
                }
            }
            if (permissionDenied) {
                item(key = "permission") {
                    Text(
                        "The Nearby devices permission was denied, so RF-REAPR can't connect. Grant it in the app's system settings and try again.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            if (inspection != null) {
                item(key = "status") { StatusCard(inspection) }
                if (inspection.findings.isNotEmpty()) {
                    item(key = "findings_header") { Text("Findings", style = MaterialTheme.typography.titleMedium) }
                    items(inspection.findings, key = { "finding_${it.title}" }) { finding ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SeverityBadge(finding.severity)
                                    Text(finding.title, fontWeight = FontWeight.SemiBold)
                                }
                                SelectionContainer { Text(finding.detail, style = MaterialTheme.typography.bodySmall) }
                                Text(finding.remediation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else if (inspection.phase == GattPhase.DONE) {
                    item(key = "no_findings") {
                        Text(
                            if (inspection.bonded) "This device is already paired with the phone, so what it exposes to strangers couldn't be assessed."
                            else "Nothing notable is exposed to an unpaired connection. This only covers what the service list reveals.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (inspection.services.isNotEmpty()) {
                    item(key = "services_header") {
                        Text("Services (${inspection.services.size})", style = MaterialTheme.typography.titleMedium)
                    }
                    items(inspection.services, key = { "service_${it.uuid}" }) { service -> ServiceCard(service) }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(inspection: GattInspection) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val busy = inspection.phase in setOf(GattPhase.CONNECTING, GattPhase.DISCOVERING, GattPhase.READING)
            if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
            Column {
                Text(
                    when (inspection.phase) {
                        GattPhase.CONNECTING -> "Connecting…"
                        GattPhase.DISCOVERING -> "Discovering services…"
                        GattPhase.READING -> "Reading device information…"
                        GattPhase.DONE -> "Inspection complete, disconnected"
                        GattPhase.FAILED -> "Inspection failed"
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = if (inspection.phase == GattPhase.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                inspection.error?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (inspection.phase == GattPhase.DONE) {
                    Text(
                        "${inspection.services.size} services · ${inspection.services.sumOf { it.characteristics.size }} characteristics · " +
                            if (inspection.bonded) "paired with this phone" else "not paired",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ServiceCard(service: GattServiceInfo) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(service.name, fontWeight = FontWeight.SemiBold)
            Text(
                service.uuid, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            service.characteristics.forEach { characteristic ->
                Column(Modifier.padding(start = 8.dp)) {
                    Text(
                        "${characteristic.name} · ${GattCatalog.describeProperties(characteristic.properties).joinToString().ifBlank { "no properties" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (characteristic.isWritable && !service.isStandard) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                    if (GattCatalog.shortUuid(characteristic.uuid) == null) {
                        Text(
                            characteristic.uuid, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    GattAnalyzer.decodeValue(characteristic.uuid, characteristic.value)?.let { value ->
                        SelectionContainer {
                            Text("= $value", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}
