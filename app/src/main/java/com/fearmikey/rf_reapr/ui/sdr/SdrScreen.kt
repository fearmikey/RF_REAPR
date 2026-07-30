package com.fearmikey.rf_reapr.ui.sdr

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fearmikey.rf_reapr.ui.sdr.components.WaterfallView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.fearmikey.rf_reapr.data.repository.RtlTcpRepositoryImpl
import com.fearmikey.rf_reapr.domain.repository.SdrRepository
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SdrScreen(
    viewModel: SdrViewModel,
    onNavigateBack: () -> Unit,
) {
    val config by viewModel.config.collectAsState()
    val fftData by viewModel.fftData.collectAsState()
    val error by viewModel.error.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var host by remember { mutableStateOf("127.0.0.1") }
    var port by remember { mutableStateOf("1234") }
    
    // Frequency state in MHz for the text field
    var freqInput by remember { mutableStateOf((config.frequency / 1_000_000.0).toString()) }

    LaunchedEffect(config.frequency) {
        freqInput = String.format(Locale.US, "%.3f", config.frequency / 1_000_000.0)
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    val presets = listOf(
        SdrPreset("FM Radio", 101.1),
        SdrPreset("Airband", 118.1),
        SdrPreset("Weather", 162.475),
        SdrPreset("AIS", 161.975),
        SdrPreset("Pagers", 931.0)
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("SDR Controller", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!config.isConnected) {
                Icon(
                    Icons.Default.SettingsInputAntenna,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Host Address") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Dns, null) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Router, null) }
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = { viewModel.connect(host, port.toIntOrNull() ?: 1234) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Power, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Connect to rtl_tcp")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                TextButton(
                    onClick = { viewModel.launchDriver(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Launch RTL-SDR Driver App")
                }
                
                Text(
                    "Note: Ensure the driver is in 'Standby' mode and listening on the host/port specified above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                // TUNE SECTION
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = String.format(Locale.US, "%.6f MHz", config.frequency / 1_000_000.0),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text("Center Frequency", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { viewModel.disconnect() }) {
                                Icon(Icons.Default.PowerOff, contentDescription = "Disconnect", tint = Color.Red)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = freqInput,
                                onValueChange = { freqInput = it },
                                label = { Text("Frequency (MHz)") },
                                modifier = Modifier.weight(1f),
                                suffix = { Text("MHz") },
                                singleLine = true
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { 
                                    val mhz = freqInput.toDoubleOrNull() ?: (config.frequency / 1_000_000.0)
                                    viewModel.setFrequency((mhz * 1_000_000).toLong())
                                },
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                Text("Tune")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(presets) { preset ->
                                SuggestionChip(
                                    onClick = { viewModel.setFrequency((preset.mhz * 1_000_000).toLong()) },
                                    label = { Text(preset.name) }
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // CONTROLS SECTION
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Gain: ${if (config.gain == 0) "Auto" else "${config.gain} dB"}", style = MaterialTheme.typography.bodyMedium)
                }
                Slider(
                    value = config.gain.toFloat(),
                    onValueChange = { viewModel.setGain(it.toInt()) },
                    valueRange = 0f..50f,
                    steps = 50
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // WATERFALL SECTION
                WaterfallView(
                    fftData = fftData,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

data class SdrPreset(val name: String, val mhz: Double)
