package com.fearmikey.rf_reapr.ui.survey

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.repository.SurveyExportFormat
import com.fearmikey.rf_reapr.domain.repository.SurveyOptions
import com.fearmikey.rf_reapr.domain.repository.SurveyRepository
import com.fearmikey.rf_reapr.domain.repository.SurveySessionInfo
import com.fearmikey.rf_reapr.domain.repository.SurveyState
import com.fearmikey.rf_reapr.domain.service.ActiveEngagement
import com.fearmikey.rf_reapr.domain.service.SurveyKind
import com.fearmikey.rf_reapr.ui.components.exportFileName
import com.fearmikey.rf_reapr.ui.components.writeTextToUri
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GeoSurveyViewModel(private val repository: SurveyRepository) : ViewModel() {

    val state: StateFlow<SurveyState> = repository.state

    val sessions: StateFlow<List<SurveySessionInfo>> = repository.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projectName: StateFlow<String?> = ActiveEngagement.current.map { it?.name }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _options = MutableStateFlow(SurveyOptions(name = ""))
    val options: StateFlow<SurveyOptions> = _options

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun setOptions(transform: (SurveyOptions) -> SurveyOptions) = _options.update(transform)

    fun start() = viewModelScope.launch {
        val options = _options.value
        val name = options.name.ifBlank { "Survey " + SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date()) }
        repository.start(options.copy(name = name))?.let { _message.value = it }
    }

    fun stop() = repository.stop()

    fun delete(session: SurveySessionInfo) = viewModelScope.launch { repository.deleteSession(session.id) }

    suspend fun export(session: SurveySessionInfo, format: SurveyExportFormat): String? = repository.export(session.id, format)

    fun showMessage(text: String) {
        _message.value = text
    }

    fun consumeMessage() {
        _message.value = null
    }
}

/** Geotagged survey: log the Wi-Fi, Bluetooth and cellular emitters heard along a route. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GeoSurveyScreen(viewModel: GeoSurveyViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val options by viewModel.options.collectAsStateWithLifecycle()
    val projectName by viewModel.projectName.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var pendingExport by remember { mutableStateOf<Pair<SurveySessionInfo, SurveyExportFormat>?>(null) }
    var exportMenuFor by remember { mutableStateOf<Long?>(null) }
    var confirmDelete by remember { mutableStateOf<SurveySessionInfo?>(null) }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val pending = pendingExport
        pendingExport = null
        if (uri != null && pending != null) {
            scope.launch {
                val text = viewModel.export(pending.first, pending.second)
                viewModel.showMessage(
                    if (text == null) "That session no longer exists"
                    else writeTextToUri(context, uri, text) ?: "Exported ${pending.second.label}"
                )
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true) viewModel.start()
        else viewModel.showMessage("Precise location permission is required to geotag observations")
    }
    val startSurvey: () -> Unit = {
        val needed = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && options.ble) add(Manifest.permission.BLUETOOTH_SCAN)
        }.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) {
            viewModel.start()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Geotagged Survey")
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
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "control") {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (state.isRunning) {
                            LiveSurvey(state)
                            OutlinedButton(onClick = viewModel::stop, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Stop survey")
                            }
                        } else {
                            Text(
                                "Walk or drive a route while RF-REAPR passively logs what it hears, each tagged with the GPS position " +
                                    "where it was strongest. Nothing is transmitted to the devices and nothing is uploaded: sessions " +
                                    "stay on this device until you export a file.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = options.name,
                                onValueChange = { name -> viewModel.setOptions { it.copy(name = name) } },
                                label = { Text("Session name (optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = options.wifi, onClick = { viewModel.setOptions { it.copy(wifi = !it.wifi) } }, label = { Text("Wi-Fi") })
                                FilterChip(selected = options.ble, onClick = { viewModel.setOptions { it.copy(ble = !it.ble) } }, label = { Text("Bluetooth LE") })
                                FilterChip(selected = options.cell, onClick = { viewModel.setOptions { it.copy(cell = !it.cell) } }, label = { Text("Cell towers") })
                            }
                            Button(onClick = startSurvey, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Start survey")
                            }
                        }
                    }
                }
            }

            item(key = "sessions_header") { Text("Sessions", style = MaterialTheme.typography.titleMedium) }
            if (sessions.isEmpty()) {
                item(key = "sessions_empty") {
                    Text(
                        "No survey sessions for this project yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(sessions, key = { it.id }) { session ->
                val live = state.isRunning && state.sessionId == session.id
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(session.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(session.startedAt)) +
                                    when {
                                        live -> " · recording"
                                        session.endedAt != null -> " · ${formatDuration(session.endedAt - session.startedAt)}"
                                        else -> ""
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                SurveyKind.entries.joinToString(" · ") { "${session.counts[it] ?: 0} ${it.label}" },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Box {
                            IconButton(onClick = { exportMenuFor = session.id }, enabled = session.total > 0) {
                                Icon(Icons.Default.FileDownload, contentDescription = "Export ${session.name}")
                            }
                            DropdownMenu(expanded = exportMenuFor == session.id, onDismissRequest = { exportMenuFor = null }) {
                                SurveyExportFormat.entries.forEach { format ->
                                    DropdownMenuItem(text = { Text(format.label) }, onClick = {
                                        exportMenuFor = null
                                        pendingExport = session to format
                                        exportLauncher.launch(exportFileName(session.name, "survey", format.extension))
                                    })
                                }
                            }
                        }
                        IconButton(onClick = { confirmDelete = session }, enabled = !live) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete ${session.name}")
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete \"${session.name}\"?") },
            text = { Text("All ${session.total} logged observations of this session are removed. Export it first if you need a copy.") },
            confirmButton = { TextButton(onClick = { viewModel.delete(session); confirmDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun LiveSurvey(state: SurveyState) {
    // Ticks once a second so the elapsed time and fix age stay current.
    val now by produceState(System.currentTimeMillis(), state.isRunning) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val fix = state.fix
    val fixAgeMs = fix?.let { now - it.timestamp }
    val hasFix = fixAgeMs != null && fixAgeMs < 20_000

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            if (hasFix) Icons.Default.GpsFixed else Icons.Default.GpsNotFixed,
            contentDescription = null,
            tint = if (hasFix) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        Column {
            Text(
                "Recording · ${formatDuration(now - (state.startedAt ?: now))}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (hasFix) {
                    "%.5f, %.5f".format(Locale.US, fix.latitude, fix.longitude) +
                        (fix.accuracyM?.let { " · ±${it.toInt()} m" } ?: "")
                } else {
                    "Waiting for a GPS fix. Observations are only logged once there is one; move somewhere with a view of the sky."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        SurveyKind.entries.forEach { kind ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${state.counts[kind] ?: 0}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(kind.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (state.skippedNoFix > 0) {
        Text(
            "${state.skippedNoFix} scan batch${if (state.skippedNoFix == 1) "" else "es"} skipped for lack of a GPS fix",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    Text(
        "Keeps running with the screen off. Android limits Wi-Fi scans to about one every 30 seconds.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return when {
        s >= 3600 -> "${s / 3600}h ${(s % 3600) / 60}m"
        s >= 60 -> "${s / 60}m ${s % 60}s"
        else -> "${s}s"
    }
}
