package com.example.rf_reapr.ui.wireless

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.rf_reapr.domain.model.BleDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothProximityFinderScreen(
    viewModel: BluetoothProximityFinderViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val targetAddress by viewModel.targetAddress.collectAsState()
    val targetName by viewModel.targetName.collectAsState()
    val currentRssi by viewModel.currentRssi.collectAsState()
    val rssiHistory by viewModel.rssiHistory.collectAsState()
    val isTracking by viewModel.isTracking.collectAsState()
    val isDiscoveryActive by viewModel.isDiscoveryActive.collectAsState()
    val isActiveMode by viewModel.isActiveMode.collectAsState()
    val recentlySeen by viewModel.recentlySeen.collectAsState()

    var showInfoDialog by remember { mutableStateOf(false) }

    // Start discovery ONLY when the screen is viewed and permissions are likely granted.
    // In MainActivity, we navigate here only after permissions are granted.
    DisposableEffect(Unit) {
        val hasScanPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Legacy permissions handled at install time or via location
        }

        if (hasScanPermission) {
            viewModel.startDiscovery()
        }
        
        onDispose {
            viewModel.stopDiscovery()
        }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("Scan Modes Explained") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column {
                        Text("Passive Mode (Stealthy)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("The device only listens for broadcasted packets. It does not transmit any radio signals, making it invisible to monitoring tools. However, names for many devices will not be resolved.")
                    }
                    Column {
                        Text("Active Mode (Discovery)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Text("The device actively sends 'Scan Requests' to nearby hardware and uses Classic Bluetooth discovery. This provides maximum name resolution (e.g. 'LG TV') but makes your presence detectable by security systems.")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Got it")
                }
            }
        )
    }

    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    LaunchedEffect(Unit) {
        viewModel.vibrationTrigger.collect {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bluetooth Proximity Finder") },
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (targetAddress == null) {
                TargetSelection(
                    recentlySeen = recentlySeen,
                    isDiscoveryActive = isDiscoveryActive,
                    isActiveMode = isActiveMode,
                    onToggleActiveMode = { viewModel.toggleActiveMode(it) },
                    onShowInfo = { showInfoDialog = true },
                    onSelect = { address, name -> viewModel.setTarget(address, name) }
                )
            } else {
                TrackingView(
                    address = targetAddress!!,
                    name = targetName,
                    rssi = currentRssi,
                    history = rssiHistory,
                    isTracking = isTracking,
                    onToggle = { viewModel.toggleTracking() },
                    onChangeTarget = { viewModel.setTarget("") }
                )
            }
        }
    }
}

@Composable
fun TargetSelection(
    recentlySeen: List<BleDevice>,
    isDiscoveryActive: Boolean,
    isActiveMode: Boolean,
    onToggleActiveMode: (Boolean) -> Unit,
    onShowInfo: () -> Unit,
    onSelect: (String, String?) -> Unit
) {
    var manualAddress by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isActiveMode) "Active Discovery" else "Passive Stealth",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isActiveMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = onShowInfo) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = isActiveMode,
                onCheckedChange = onToggleActiveMode
            )
        }

        Text("Select a device to track", style = MaterialTheme.typography.labelMedium)
        spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = manualAddress,
            onValueChange = { manualAddress = it },
            label = { Text("Manual MAC Address") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = { if (manualAddress.isNotBlank()) onSelect(manualAddress, null) }) {
                    Icon(Icons.Default.Radar, contentDescription = "Track")
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Nearby Devices", style = MaterialTheme.typography.titleSmall)
            if (isDiscoveryActive) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
            }
        }
        
        if (recentlySeen.isEmpty() && isDiscoveryActive) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Searching for nearby devices...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = recentlySeen,
                key = { it.address }
            ) { device ->
                val displayName = device.name ?: "Unnamed Device"
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(device.address, displayName) }
                ) {
                    ListItem(
                        headlineContent = { Text(displayName) },
                        supportingContent = { 
                            Text(
                                text = if (device.manufacturer != null && !displayName.contains(device.manufacturer!!))
                                    "${device.manufacturer} • ${device.address}" 
                                else device.address
                            ) 
                        },
                        trailingContent = { 
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${device.rssi} dBm", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    text = "~${"%.1f".format(device.estimatedDistance)}m",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        leadingContent = { Icon(Icons.Default.Bluetooth, contentDescription = null) }
                    )
                }
            }
        }
    }
}

@Composable
fun spacer(modifier: Modifier) {
    Spacer(modifier = modifier)
}

@Composable
fun TrackingView(
    address: String,
    name: String?,
    rssi: Int?,
    history: List<Int>,
    isTracking: Boolean,
    onToggle: () -> Unit,
    onChangeTarget: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Tracking Target", style = MaterialTheme.typography.labelLarge)
            Text(
                text = name ?: address,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (name != null) {
                Text(address, style = MaterialTheme.typography.bodySmall)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                SignalMeter(rssi = rssi)
            }
            
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                val statusText = when {
                    !isTracking -> "Ready"
                    rssi == null -> "Searching..."
                    rssi > -50 -> "EXTREMELY HOT"
                    rssi > -65 -> "HOT"
                    rssi > -80 -> "WARM"
                    else -> "COLD"
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black
                        ),
                        textAlign = TextAlign.Center,
                        color = when {
                            rssi == null -> MaterialTheme.colorScheme.onSurfaceVariant
                            rssi > -65 -> Color.Red
                            rssi > -80 -> Color(0xFFFFA500) // Orange
                            else -> Color.Cyan
                        }
                    )
                    if (rssi != null && isTracking) {
                        val distance = BleDevice.calculateDistance(rssi)
                        Text(
                            text = "~${"%.1f".format(distance)} meters",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        RssiHistoryGraph(
            history = history,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onToggle,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isTracking) "Stop Tracking" else "Start Tracking")
            }
            OutlinedButton(
                onClick = onChangeTarget,
                modifier = Modifier.weight(1f)
            ) {
                Text("Change Target")
            }
        }
    }
}

@Composable
fun RssiHistoryGraph(history: List<Int>, modifier: Modifier = Modifier) {
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    val axisColor = MaterialTheme.colorScheme.outline

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val labelWidth = 40.dp.toPx()
        val graphPadding = 8.dp.toPx()
        val width = size.width - labelWidth - graphPadding
        val height = size.height - graphPadding * 2
        val maxPoints = 60
        val minRssi = -100f
        val maxRssi = -15f
        val range = maxRssi - minRssi

        fun getRssiColor(rssi: Int): Color {
            return when {
                rssi > -65 -> Color.Red
                rssi > -80 -> Color(0xFFFFA500)
                else -> Color.Cyan
            }
        }

        val gridLevels = listOf(-15, -30, -50, -70, -90, -100)
        gridLevels.forEach { level ->
            val y = height - ((level.toFloat() - minRssi) / range * height) + graphPadding
            drawLine(
                color = gridColor,
                start = androidx.compose.ui.geometry.Offset(labelWidth, y),
                end = androidx.compose.ui.geometry.Offset(size.width, y),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            )
            drawContext.canvas.nativeCanvas.drawText(
                "$level",
                10f,
                y + 10f,
                android.graphics.Paint().apply {
                    this.color = labelColor.toArgb()
                    this.textSize = 10.sp.toPx()
                }
            )
        }

        drawLine(
            color = axisColor,
            start = androidx.compose.ui.geometry.Offset(labelWidth, graphPadding),
            end = androidx.compose.ui.geometry.Offset(labelWidth, height + graphPadding),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = axisColor,
            start = androidx.compose.ui.geometry.Offset(labelWidth, height + graphPadding),
            end = androidx.compose.ui.geometry.Offset(size.width, height + graphPadding),
            strokeWidth = 1.dp.toPx()
        )

        if (history.size < 2) return@Canvas

        for (i in 0 until history.size - 1) {
            val rssi1 = history[i]
            val rssi2 = history[i + 1]
            val x1 = labelWidth + (width / (maxPoints - 1)) * i
            val y1 = height - ((rssi1.toFloat() - minRssi) / range * height) + graphPadding
            val x2 = labelWidth + (width / (maxPoints - 1)) * (i + 1)
            val y2 = height - ((rssi2.toFloat() - minRssi) / range * height) + graphPadding
            val segmentColor = getRssiColor(rssi2)

            drawLine(
                color = segmentColor,
                start = androidx.compose.ui.geometry.Offset(x1, y1),
                end = androidx.compose.ui.geometry.Offset(x2, y2),
                strokeWidth = 2.dp.toPx()
            )

            val fillPath = Path().apply {
                moveTo(x1, height + graphPadding)
                lineTo(x1, y1)
                lineTo(x2, y2)
                lineTo(x2, height + graphPadding)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(segmentColor.copy(alpha = 0.2f), Color.Transparent),
                    startY = minOf(y1, y2),
                    endY = height + graphPadding
                )
            )
        }
    }
}

@Composable
fun SignalMeter(rssi: Int?) {
    Box(
        modifier = Modifier
            .size(150.dp)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = {
                if (rssi == null) 0f
                else ((rssi + 100).toFloat() / 85f).coerceIn(0f, 1f)
            },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 12.dp,
            color = when {
                rssi == null -> MaterialTheme.colorScheme.surfaceVariant
                rssi > -65 -> Color.Red
                rssi > -80 -> Color(0xFFFFA500)
                else -> Color.Cyan
            },
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = rssi?.toString() ?: "--",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold
            )
            Text("dBm", style = MaterialTheme.typography.labelMedium)
        }
    }
}
