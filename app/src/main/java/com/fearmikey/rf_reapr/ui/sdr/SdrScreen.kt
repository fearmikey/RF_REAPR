package com.fearmikey.rf_reapr.ui.sdr

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fearmikey.rf_reapr.ui.sdr.components.WaterfallView
import java.util.Locale

private val SAMPLE_RATES = listOf(1_024_000 to "1.024", 1_400_000 to "1.4", 2_048_000 to "2.048", 2_400_000 to "2.4")
private val STEPS = listOf(-1_000_000L to "−1M", -100_000L to "−100k", -12_500L to "−12.5k", 12_500L to "+12.5k", 100_000L to "+100k", 1_000_000L to "+1M")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SdrScreen(
    viewModel: SdrViewModel,
    onNavigateBack: () -> Unit,
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val fftData by viewModel.fftData.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var host by remember { mutableStateOf("127.0.0.1") }
    var port by remember { mutableStateOf("1234") }
    var freqInput by remember { mutableStateOf(String.format(Locale.US, "%.3f", config.frequency / 1_000_000.0)) }
    var gainSlider by remember(config.gain) { mutableFloatStateOf(config.gain.toFloat()) }
    var showAdvanced by remember { mutableStateOf(false) }

    LaunchedEffect(config.frequency) {
        freqInput = String.format(Locale.US, "%.3f", config.frequency / 1_000_000.0)
    }
    LaunchedEffect(error) { error?.let { snackbarHostState.showSnackbar(it) } }

    val presets = listOf(
        SdrPreset("FM radio", 101.1),
        SdrPreset("Airband", 118.1),
        SdrPreset("NOAA APT", 137.1),
        SdrPreset("2 m ham", 145.5),
        SdrPreset("Marine 16", 156.8),
        SdrPreset("AIS", 161.975),
        SdrPreset("Weather", 162.475),
        SdrPreset("ISM 433", 433.92),
        SdrPreset("PMR446", 446.1),
        SdrPreset("ISM 868", 868.3),
        SdrPreset("ISM 915", 915.0),
        SdrPreset("ADS-B", 1090.0)
    )

    fun tune() {
        val mhz = freqInput.replace(',', '.').toDoubleOrNull() ?: return
        viewModel.setFrequency((mhz * 1_000_000).toLong())
    }

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
                actions = {
                    if (config.isConnected) {
                        IconButton(onClick = viewModel::disconnect) {
                            Icon(Icons.Default.PowerOff, contentDescription = "Disconnect", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (!config.isConnected) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.SettingsInputAntenna, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    "Connect to an rtl_tcp server: the RTL-SDR driver app on this phone (USB OTG dongle), or a computer on your network running rtl_tcp -a 0.0.0.0.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = host, onValueChange = { host = it.trim() },
                    label = { Text("Host") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Dns, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                OutlinedTextField(
                    value = port, onValueChange = { v -> port = v.filter { it.isDigit() }.take(5) },
                    label = { Text("Port") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Router, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Button(
                    onClick = { viewModel.connect(host, port.toIntOrNull()?.takeIf { it in 1..65535 } ?: 1234) },
                    enabled = !config.isConnecting && host.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (config.isConnecting) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Default.Power, null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (config.isConnecting) "Connecting…" else "Connect to rtl_tcp")
                }
                TextButton(onClick = { viewModel.launchDriver(context) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Launch RTL-SDR driver app")
                }
                Text(
                    "Start the driver first and leave it listening on the host/port above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                String.format(Locale.US, "%.6f MHz", config.frequency / 1_000_000.0),
                                style = MaterialTheme.typography.headlineSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                listOfNotNull(config.tunerName, "${config.sampleRate / 1e6} MS/s", if (config.ppm != 0) "${config.ppm} ppm" else null).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = freqInput,
                            onValueChange = { freqInput = it },
                            label = { Text("Frequency") },
                            modifier = Modifier.weight(1f),
                            suffix = { Text("MHz") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { tune() })
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { tune() }) { Text("Tune") }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        STEPS.forEach { (delta, label) ->
                            OutlinedButton(onClick = { viewModel.step(delta) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(label) }
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(presets) { preset ->
                            SuggestionChip(onClick = { viewModel.setFrequency((preset.mhz * 1_000_000).toLong()) }, label = { Text(preset.name) })
                        }
                    }
                }
            }

            WaterfallView(
                fftData = fftData,
                modifier = Modifier.fillMaxWidth(),
                onTapFrequency = { hz -> viewModel.setFrequency(hz) }
            )
            Text(
                "Tap the spectrum to tune there.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            fftData.peak?.let { peak ->
                Card {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Strongest signal", style = MaterialTheme.typography.labelMedium)
                            Text(
                                String.format(Locale.US, "%.4f MHz · %.1f dBFS · %.0f dB above noise", peak.frequencyHz / 1e6, peak.powerDb, peak.snrDb),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        TextButton(onClick = viewModel::tuneToPeak) { Text("Tune") }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GraphicEq, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Gain: ${if (gainSlider.toInt() == 0) "Auto" else "${gainSlider.toInt()} dB"}", style = MaterialTheme.typography.bodyMedium)
            }
            // Only send the command when the user lets go (dragging used to flood the dongle).
            Slider(
                value = gainSlider,
                onValueChange = { gainSlider = it },
                onValueChangeFinished = { viewModel.setGain(gainSlider.toInt()) },
                valueRange = 0f..50f,
                steps = 49
            )

            Text("Sample rate (MS/s)", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SAMPLE_RATES.forEachIndexed { i, (rate, label) ->
                    SegmentedButton(
                        selected = config.sampleRate == rate,
                        onClick = { viewModel.setSampleRate(rate) },
                        shape = SegmentedButtonDefaults.itemShape(i, SAMPLE_RATES.size)
                    ) { Text(label) }
                }
            }

            TextButton(onClick = { showAdvanced = !showAdvanced }) { Text(if (showAdvanced) "Hide advanced" else "Advanced") }
            if (showAdvanced) {
                var ppmInput by remember { mutableStateOf(config.ppm.toString()) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = ppmInput,
                        onValueChange = { v -> ppmInput = v.filter { it.isDigit() || it == '-' }.take(4) },
                        label = { Text("Frequency correction") },
                        suffix = { Text("ppm") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { ppmInput.toIntOrNull()?.let(viewModel::setPpm) }) { Text("Set") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Bias-T power", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Sends 4.5 V up the antenna cable for LNAs. Only enable with a dongle and antenna that support it.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = config.biasTee, onCheckedChange = viewModel::setBiasTee)
                }
            }
        }
    }
}

data class SdrPreset(val name: String, val mhz: Double)
