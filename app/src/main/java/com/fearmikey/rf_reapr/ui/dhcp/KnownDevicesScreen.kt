package com.fearmikey.rf_reapr.ui.dhcp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.repository.KnownDeviceRepository
import com.fearmikey.rf_reapr.domain.service.ActiveEngagement
import com.fearmikey.rf_reapr.domain.service.DeviceIdentificationService
import com.fearmikey.rf_reapr.domain.service.KnownDevice
import com.fearmikey.rf_reapr.domain.service.KnownDeviceMatcher
import com.fearmikey.rf_reapr.domain.service.MacAddressAnalyzer
import com.fearmikey.rf_reapr.domain.service.ObservedDevice
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KnownDevicesViewModel(private val repository: KnownDeviceRepository) : ViewModel() {

    val devices: StateFlow<List<KnownDevice>> = repository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projectName: StateFlow<String?> = ActiveEngagement.current.map { it?.name }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Returns an error message, or null when the device was added. */
    suspend fun addManual(mac: String, label: String): String? {
        val normalized = KnownDeviceMatcher.normalize(mac) ?: return "Enter a full MAC address, e.g. AA:BB:CC:DD:EE:FF"
        val added = repository.add(listOf(ObservedDevice(ip = "", mac = normalized, hostname = label.trim().ifBlank { "Device $normalized" })))
        return if (added == 0) "That MAC address is already on the list" else null
    }

    fun update(device: KnownDevice, label: String, notes: String) = viewModelScope.launch { repository.update(device.id, label, notes) }

    fun remove(device: KnownDevice) = viewModelScope.launch { repository.remove(device.id) }
}

/** The active project's allowlist of expected devices; anything else gets flagged by the Network Monitor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnownDevicesScreen(viewModel: KnownDevicesViewModel, onBack: () -> Unit) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val projectName by viewModel.projectName.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<KnownDevice?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Known devices")
                        Text(
                            projectName ?: "No active project",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add by MAC") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "intro") {
                Text(
                    "Devices listed here are expected on this project's network. Once the list has entries, the Network Monitor tags " +
                        "every other device as not on the list and records it under Findings. Matching is by MAC address, so phones " +
                        "using a private (randomized) address will show up as unknown again when it changes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (devices.isEmpty()) {
                item(key = "empty") {
                    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("No known devices yet", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "In the Network Monitor, use \"Mark all listed devices as known\" after a sweep of a network you trust, or add devices one at a time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            items(devices, key = { it.id }) { device ->
                Card(Modifier.fillMaxWidth().clickable { editing = device }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(device.label, fontWeight = FontWeight.SemiBold)
                            Text(
                                device.mac + (DeviceIdentificationService.getManufacturer(device.mac)?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                listOfNotNull(
                                    device.lastIp?.takeIf { it.isNotBlank() }?.let { "IP when added: $it" },
                                    "added ${SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(device.addedAt))}",
                                    "randomized MAC".takeIf { MacAddressAnalyzer.parse(device.mac)?.isRandomized == true }
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            device.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                        IconButton(onClick = { viewModel.remove(device) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove ${device.label} from known devices")
                        }
                    }
                }
            }
        }
    }

    editing?.let { device ->
        var label by remember(device.id) { mutableStateOf(device.label) }
        var notes by remember(device.id) { mutableStateOf(device.notes.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit known device") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(device.mac, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes (owner, location…)") })
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.update(device, label, notes); editing = null }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }

    if (showAdd) {
        var mac by remember { mutableStateOf("") }
        var label by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add known device") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = mac,
                        onValueChange = { mac = it; error = null },
                        label = { Text("MAC address") },
                        singleLine = true,
                        isError = error != null,
                        supportingText = error?.let { message -> { Text(message) } }
                    )
                    OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Name (optional)") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        error = viewModel.addManual(mac, label)
                        if (error == null) showAdd = false
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }
}
