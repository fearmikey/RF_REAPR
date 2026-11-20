package com.fearmikey.rf_reapr.ui.wireless

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fearmikey.rf_reapr.domain.model.BleDevice
import com.fearmikey.rf_reapr.domain.service.BleAddressType
import com.fearmikey.rf_reapr.domain.service.BleCategory
import com.fearmikey.rf_reapr.domain.service.MacAddressAnalyzer
import com.fearmikey.rf_reapr.ui.theme.BluetoothBlue
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothProximityFinderScreen(
    viewModel: BluetoothProximityFinderViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val targetAddress by viewModel.targetAddress.collectAsStateWithLifecycle()
    val targetName by viewModel.targetName.collectAsStateWithLifecycle()
    val currentRssi by viewModel.currentRssi.collectAsStateWithLifecycle()
    val rssiHistory by viewModel.rssiHistory.collectAsStateWithLifecycle()
    val isTracking by viewModel.isTracking.collectAsStateWithLifecycle()
    val isDiscoveryActive by viewModel.isDiscoveryActive.collectAsStateWithLifecycle()
    val isActiveMode by viewModel.isActiveMode.collectAsStateWithLifecycle()
    val recentlySeen by viewModel.recentlySeen.collectAsStateWithLifecycle()
    val followers by viewModel.possibleFollowers.collectAsStateWithLifecycle()
    val scanError by viewModel.scanError.collectAsStateWithLifecycle()
    val silenceMs by viewModel.targetSilenceMs.collectAsStateWithLifecycle()
    val trend by viewModel.trend.collectAsStateWithLifecycle()
    val vibrationEnabled by viewModel.vibrationEnabled.collectAsStateWithLifecycle()

    var showInfoDialog by remember { mutableStateOf(false) }

    // Scan while the screen is visible; the ViewModel writes the session log when it's cleared.
    DisposableEffect(Unit) {
        val hasScanPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else true
        if (hasScanPermission && viewModel.targetAddress.value == null) viewModel.startDiscovery()
        onDispose { viewModel.stopDiscovery(log = false) }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("How it works") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column {
                        Text("Passive mode (stealthy)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("Only listens for advertisements. Nothing is transmitted, but many devices won't report a name.", style = MaterialTheme.typography.bodySmall)
                    }
                    Column {
                        Text("Active mode (discovery)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Text("Sends scan requests and runs Classic Bluetooth discovery for better names, but your phone becomes visible to monitoring tools.", style = MaterialTheme.typography.bodySmall)
                    }
                    Column {
                        Text("Tracker detection", fontWeight = FontWeight.Bold)
                        Text("AirTags/Find My, Samsung SmartTag, Tile, Chipolo and Google Find My tags are flagged. A tracker that stays near you for 10+ minutes raises an alert.", style = MaterialTheme.typography.bodySmall)
                    }
                    Column {
                        Text("Distance", fontWeight = FontWeight.Bold)
                        Text("Estimated from signal strength (and advertised TX power when available). Walls and bodies distort it, so watch the trend rather than the number.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showInfoDialog = false }) { Text("Got it") } }
        )
    }

    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
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

    BackHandler(enabled = targetAddress != null) { viewModel.setTarget("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (targetAddress == null) "Bluetooth Proximity Finder" else "Locating device") },
                navigationIcon = {
                    IconButton(onClick = { if (targetAddress != null) viewModel.setTarget("") else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showInfoDialog = true }) { Icon(Icons.Default.Info, contentDescription = "Info") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val target = targetAddress
            if (target == null) {
                TargetSelection(
                    viewModel = viewModel,
                    recentlySeen = recentlySeen,
                    followers = followers,
                    isDiscoveryActive = isDiscoveryActive,
                    isActiveMode = isActiveMode,
                    scanError = scanError,
                    onSelect = { address, name -> viewModel.setTarget(address, name) }
                )
            } else {
                TrackingView(
                    address = target,
                    name = targetName,
                    rssi = currentRssi,
                    history = rssiHistory,
                    isTracking = isTracking,
                    silenceMs = silenceMs,
                    trend = trend,
                    txPower = viewModel.targetTxPower(),
                    vibrationEnabled = vibrationEnabled,
                    onToggleVibration = { viewModel.setVibrationEnabled(!vibrationEnabled) },
                    onToggle = viewModel::toggleTracking,
                    onChangeTarget = { viewModel.setTarget("") }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TargetSelection(
    viewModel: BluetoothProximityFinderViewModel,
    recentlySeen: List<BleDevice>,
    followers: List<BleDevice>,
    isDiscoveryActive: Boolean,
    isActiveMode: Boolean,
    scanError: String?,
    onSelect: (String, String?) -> Unit
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val trackersOnly by viewModel.trackersOnly.collectAsStateWithLifecycle()
    var manualAddress by remember { mutableStateOf("") }
    val manualValid = MacAddressAnalyzer.parse(manualAddress) != null
    val trackerCount = recentlySeen.count { it.isTracker }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isActiveMode) "Active discovery" else "Passive (stealth)",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isActiveMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        if (isDiscoveryActive) "Scanning · ${recentlySeen.size} devices" else "Scan paused",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Switch(checked = isActiveMode, onCheckedChange = viewModel::toggleActiveMode)
                Spacer(Modifier.width(8.dp))
                FilledTonalIconButton(onClick = { if (isDiscoveryActive) viewModel.stopDiscovery() else viewModel.startDiscovery() }) {
                    Icon(if (isDiscoveryActive) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (isDiscoveryActive) "Pause scan" else "Start scan")
                }
            }
            if (isDiscoveryActive) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
        }

        scanError?.let { err ->
            item {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                    Text(err, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        if (followers.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text("Tracker may be following you", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        followers.forEach { f ->
                            Text(
                                "${f.category.label} · ${f.address} · ${(f.lastSeen - f.firstSeen) / 60_000} min",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(onClick = { followers.first().let { onSelect(it.address, it.name) } }) { Text("Locate it") }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("Filter by name, MAC or vendor") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Default.Clear, contentDescription = "Clear filter") }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BleSort.entries.forEach { s ->
                    FilterChip(selected = sort == s, onClick = { viewModel.setSort(s) }, label = { Text(s.label) })
                }
                FilterChip(
                    selected = trackersOnly,
                    onClick = { viewModel.setTrackersOnly(!trackersOnly) },
                    label = { Text(if (trackerCount > 0) "Trackers ($trackerCount)" else "Trackers") }
                )
            }
        }

        if (recentlySeen.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text(
                        when {
                            !isDiscoveryActive -> "Scan is paused."
                            query.isNotBlank() || trackersOnly -> "No devices match."
                            else -> "Searching for nearby devices…"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        items(items = recentlySeen, key = { it.address }, contentType = { "ble_device" }) { device ->
            BleDeviceRow(device) { onSelect(device.address, device.name) }
        }

        item {
            OutlinedTextField(
                value = manualAddress,
                onValueChange = { manualAddress = it },
                label = { Text("Track a MAC address manually") },
                singleLine = true,
                isError = manualAddress.isNotBlank() && !manualValid,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(
                        onClick = { MacAddressAnalyzer.parse(manualAddress)?.let { onSelect(it.colonFormat, null) } },
                        enabled = manualValid
                    ) { Icon(Icons.Default.Radar, contentDescription = "Track") }
                }
            )
        }
    }
}

@Composable
private fun BleDeviceRow(device: BleDevice, onClick: () -> Unit) {
    val now = System.currentTimeMillis()
    val ageSec = ((now - device.lastSeen) / 1000).coerceAtLeast(0)
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (device.isTracker) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (device.isTracker) Icons.Default.LocationSearching else Icons.Default.Bluetooth,
                contentDescription = null,
                tint = if (device.isTracker) MaterialTheme.colorScheme.error else BluetoothBlue,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(device.name ?: "Unnamed device", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = buildList {
                    if (device.category != BleCategory.UNKNOWN && device.name != device.category.label) add(device.category.label)
                    device.categoryDetail?.let { add(it) }
                    device.manufacturer?.takeIf { device.name?.contains(it) != true }?.let { add(it) }
                    if (device.isClassic) add("Classic")
                }
                if (sub.isNotEmpty()) Text(sub.joinToString(" · "), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    device.address + if (device.addressType == BleAddressType.RESOLVABLE_PRIVATE) " · rotating" else "",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${device.rssi} dBm", style = MaterialTheme.typography.labelMedium, color = rssiColor(device.rssi))
                Text(
                    "~${String.format(Locale.US, "%.1f", device.estimatedDistance)} m",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(if (ageSec < 3) "now" else "${ageSec}s ago", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun rssiColor(rssi: Int): Color = when {
    rssi > -65 -> Color(0xFFE53935)
    rssi > -80 -> Color(0xFFFFA000)
    else -> Color(0xFF00ACC1)
}

@Composable
fun TrackingView(
    address: String,
    name: String?,
    rssi: Int?,
    history: List<Int>,
    isTracking: Boolean,
    silenceMs: Long?,
    trend: TrackTrend,
    txPower: Int?,
    vibrationEnabled: Boolean,
    onToggleVibration: () -> Unit,
    onToggle: () -> Unit,
    onChangeTarget: () -> Unit
) {
    val lost = isTracking && (silenceMs ?: 0) > BluetoothProximityFinderViewModel.LOST_AFTER_MS
    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name ?: address, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            if (name != null) Text(address, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }

        if (lost) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                Text(
                    "Signal lost for ${(silenceMs ?: 0) / 1000}s. The device may be out of range, asleep, or has rotated its address.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { SignalMeter(rssi = if (lost) null else rssi) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val statusText = when {
                    !isTracking -> "Paused"
                    lost -> "LOST"
                    rssi == null -> "Searching…"
                    rssi > -50 -> "VERY HOT"
                    rssi > -65 -> "HOT"
                    rssi > -80 -> "WARM"
                    else -> "COLD"
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        statusText,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                        textAlign = TextAlign.Center,
                        color = if (rssi == null || lost) MaterialTheme.colorScheme.onSurfaceVariant else rssiColor(rssi)
                    )
                    if (rssi != null && isTracking && !lost) {
                        Text(
                            "~${String.format(Locale.US, "%.1f", BleDevice.calculateDistance(rssi, txPower))} m",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val (icon, label) = when (trend) {
                                TrackTrend.CLOSER -> Icons.AutoMirrored.Filled.TrendingUp to "Getting closer"
                                TrackTrend.FARTHER -> Icons.AutoMirrored.Filled.TrendingDown to "Moving away"
                                TrackTrend.STEADY -> Icons.AutoMirrored.Filled.TrendingFlat to "Steady"
                            }
                            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        RssiHistoryGraph(history = history, modifier = Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 8.dp))

        Text(
            "Walk slowly and turn around: your body blocks the signal, so the reading is strongest when you face the device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.weight(1f))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onToggle, modifier = Modifier.weight(1f)) { Text(if (isTracking) "Pause" else "Resume") }
            OutlinedButton(onClick = onChangeTarget, modifier = Modifier.weight(1f)) { Text("Change target") }
            IconToggleButton(checked = vibrationEnabled, onCheckedChange = { onToggleVibration() }) {
                Icon(
                    Icons.Default.Vibration,
                    contentDescription = if (vibrationEnabled) "Disable vibration" else "Enable vibration",
                    tint = if (vibrationEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun RssiHistoryGraph(history: List<Int>, modifier: Modifier = Modifier) {
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    val axisColor = MaterialTheme.colorScheme.outline
    val labelTextSize = 10.sp

    androidx.compose.foundation.Canvas(
        modifier = modifier.drawWithCache {
            val labelWidth = 40.dp.toPx()
            val graphPadding = 8.dp.toPx()
            val width = size.width - labelWidth - graphPadding
            val height = size.height - graphPadding * 2
            val maxPoints = 60
            val minRssi = -100f
            val maxRssi = -15f
            val range = maxRssi - minRssi

            val textPaint = android.graphics.Paint().apply {
                this.color = labelColor.toArgb()
                this.textSize = labelTextSize.toPx()
            }

            onDrawBehind {
                listOf(-15, -30, -50, -70, -90, -100).forEach { level ->
                    val y = height - ((level.toFloat() - minRssi) / range * height) + graphPadding
                    drawLine(
                        color = gridColor,
                        start = androidx.compose.ui.geometry.Offset(labelWidth, y),
                        end = androidx.compose.ui.geometry.Offset(size.width, y),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                    drawContext.canvas.nativeCanvas.drawText("$level", 10f, y + 10f, textPaint)
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
                if (history.size < 2) return@onDrawBehind

                for (i in 0 until history.size - 1) {
                    val x1 = labelWidth + (width / (maxPoints - 1)) * i
                    val y1 = height - ((history[i].toFloat() - minRssi) / range * height) + graphPadding
                    val x2 = labelWidth + (width / (maxPoints - 1)) * (i + 1)
                    val y2 = height - ((history[i + 1].toFloat() - minRssi) / range * height) + graphPadding
                    val segmentColor = rssiColor(history[i + 1])
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
    ) {}
}

@Composable
fun SignalMeter(rssi: Int?) {
    Box(modifier = Modifier.size(150.dp).padding(8.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { if (rssi == null) 0f else ((rssi + 100).toFloat() / 85f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 12.dp,
            color = if (rssi == null) MaterialTheme.colorScheme.surfaceVariant else rssiColor(rssi),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(rssi?.toString() ?: "--", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
            Text("dBm", style = MaterialTheme.typography.labelMedium)
        }
    }
}
