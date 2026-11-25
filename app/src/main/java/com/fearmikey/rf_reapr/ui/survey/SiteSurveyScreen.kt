package com.fearmikey.rf_reapr.ui.survey

import android.graphics.BitmapFactory
import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fearmikey.rf_reapr.domain.repository.SiteSurveyInfo
import com.fearmikey.rf_reapr.domain.service.HeatPoint
import com.fearmikey.rf_reapr.domain.service.SignalHeatmap
import com.fearmikey.rf_reapr.domain.service.SignalQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Walk-around Wi-Fi site survey: tap where you are standing on the floor plan to measure. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteSurveyScreen(viewModel: SiteSurveyViewModel, onBack: () -> Unit) {
    val current by viewModel.current.collectAsStateWithLifecycle()
    val projectName by viewModel.projectName.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }
    // Back from an open survey returns to the list rather than leaving the tool.
    BackHandler(enabled = current != null) { viewModel.close() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(current?.name ?: "Wi-Fi Site Survey")
                        Text(
                            projectName ?: "No active project",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (current != null) viewModel.close() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val survey = current
        if (survey == null) SurveyList(viewModel, Modifier.padding(padding))
        else SurveyCanvas(viewModel, survey, Modifier.padding(padding))
    }
}

@Composable
private fun SurveyList(viewModel: SiteSurveyViewModel, modifier: Modifier) {
    val surveys by viewModel.surveys.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<SiteSurveyInfo?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "intro") {
            Text(
                "Load a floor plan (a photo of a fire-escape map works), then walk the site and tap where you are standing. " +
                    "Each tap records every visible access point's signal there, building a coverage map with dead zones.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item(key = "new") {
            Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("New site survey")
            }
        }
        if (surveys.isEmpty()) {
            item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("No site surveys for this project yet", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        items(surveys, key = { it.id }) { survey ->
            Card(Modifier.fillMaxWidth().clickable { viewModel.open(survey) }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(survey.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${survey.pointCount} point${if (survey.pointCount == 1) "" else "s"} · " +
                                SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(survey.createdAt)) +
                                if (survey.floorPlanPath == null) " · blank grid" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { confirmDelete = survey }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete ${survey.name}")
                    }
                }
            }
        }
    }

    if (showCreate) CreateSurveyDialog(onDismiss = { showCreate = false }) { name, uri ->
        showCreate = false
        viewModel.create(name, uri)
    }
    confirmDelete?.let { survey ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete \"${survey.name}\"?") },
            text = { Text("The floor plan copy and all ${survey.pointCount} measured points are removed.") },
            confirmButton = { TextButton(onClick = { viewModel.delete(survey); confirmDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun CreateSurveyDialog(onDismiss: () -> Unit, onCreate: (String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var planUri by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { planUri = it.toString() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New site survey") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name (e.g. 2nd floor)") }, singleLine = true)
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Text(if (planUri == null) "Choose floor plan image" else "Floor plan selected (change)")
                }
                Text(
                    "The image is copied into the app's private storage. Without one, a blank grid is used.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, planUri) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SurveyCanvas(viewModel: SiteSurveyViewModel, survey: SiteSurveyInfo, modifier: Modifier) {
    val samples by viewModel.samples.collectAsStateWithLifecycle()
    val networks by viewModel.networks.collectAsStateWithLifecycle()
    val heatmap by viewModel.heatmap.collectAsStateWithLifecycle()
    val measuring by viewModel.measuring.collectAsStateWithLifecycle()
    var showNetworks by remember { mutableStateOf(false) }
    var pointToDelete by remember { mutableStateOf<Long?>(null) }

    val plan by produceState<ImageBitmap?>(null, survey.floorPlanPath) {
        value = survey.floorPlanPath?.let { path -> withContext(Dispatchers.IO) { decodePlan(path) } }
    }
    val aspect = plan?.let { it.width.toFloat() / it.height } ?: (4f / 3f)

    // One position per measured point (a point has one row per access point heard there).
    val positions = remember(samples) { samples.groupBy { it.pointId }.map { (id, atPoint) -> Triple(id, atPoint.first().x, atPoint.first().y) } }
    val cols = 36
    val rows = (cols / aspect).roundToInt().coerceIn(12, 72)
    val grid = remember(heatmap.points, cols, rows) { SignalHeatmap.grid(heatmap.points, cols, rows) }
    val coverage = remember(grid) { SignalHeatmap.coverage(grid) }
    val tapRadiusPx = with(LocalDensity.current) { 22.dp.toPx() }
    val labelPaint = remember { Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER; color = android.graphics.Color.WHITE } }
    val labelSizePx = with(LocalDensity.current) { 10.dp.toPx() }
    val description = "Floor plan with ${positions.size} measured points" +
        (heatmap.selection?.let { " showing ${it.ssid.ifBlank { "hidden network" }}" } ?: "")

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Network selector
        Box {
            OutlinedButton(onClick = { showNetworks = true }, enabled = networks.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text(
                    heatmap.selection?.let { it.ssid.ifBlank { "(hidden network)" } + (it.bssid?.let { b -> " · $b" } ?: " · all access points") }
                        ?: "No measurements yet",
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = showNetworks, onDismissRequest = { showNetworks = false }) {
                networks.take(40).forEach { network ->
                    DropdownMenuItem(
                        text = {
                            Text("${network.ssid.ifBlank { "(hidden network)" }}  ${network.bestRssi} dBm · ${network.bands.sorted().joinToString("/")}")
                        },
                        onClick = { showNetworks = false; viewModel.select(NetworkSelection(network.ssid)) }
                    )
                }
            }
        }
        if (heatmap.accessPoints.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val selection = heatmap.selection
                FilterChip(
                    selected = selection?.bssid == null,
                    onClick = { selection?.let { viewModel.select(NetworkSelection(it.ssid)) } },
                    label = { Text("All APs") }
                )
                heatmap.accessPoints.forEach { ap ->
                    FilterChip(
                        selected = selection?.bssid == ap.bssid,
                        onClick = { viewModel.select(NetworkSelection(ap.ssid, ap.bssid)) },
                        label = { Text("${ap.bssid?.takeLast(8)} · ${ap.bands.sorted().joinToString("/")}") }
                    )
                }
            }
        }

        // Plan + heatmap + points
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .semantics { contentDescription = description }
                .pointerInput(positions) {
                    detectTapGestures { tap ->
                        val hit = positions.minByOrNull { (_, x, y) -> hypot(x * size.width - tap.x, y * size.height - tap.y) }
                            ?.takeIf { (_, x, y) -> hypot(x * size.width - tap.x, y * size.height - tap.y) <= tapRadiusPx }
                        if (hit != null) pointToDelete = hit.first
                        else viewModel.measure(tap.x / size.width, tap.y / size.height)
                    }
                }
        ) {
            plan?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
            Canvas(Modifier.fillMaxSize()) {
                val cellW = size.width / cols
                val cellH = size.height / rows
                if (plan == null) {
                    // Blank grid backdrop so positions can be judged without a plan.
                    val line = Color.Gray.copy(alpha = 0.25f)
                    for (i in 1 until 8) {
                        drawLine(line, Offset(size.width * i / 8, 0f), Offset(size.width * i / 8, size.height))
                        drawLine(line, Offset(0f, size.height * i / 8), Offset(size.width, size.height * i / 8))
                    }
                }
                for (row in 0 until rows) {
                    for (col in 0 until cols) {
                        val value = grid[row * cols + col]
                        if (!value.isNaN()) {
                            // Slight overlap hides hairline seams between cells.
                            drawRect(Color(SignalHeatmap.colorArgb(value)), Offset(col * cellW, row * cellH), Size(cellW + 0.5f, cellH + 0.5f))
                        }
                    }
                }
                val byPosition = heatmap.points.associateBy { it.x to it.y }
                labelPaint.textSize = labelSizePx
                positions.forEach { (_, x, y) ->
                    val center = Offset(x * size.width, y * size.height)
                    val reading: HeatPoint? = byPosition[x to y]
                    drawCircle(Color.Black.copy(alpha = 0.75f), radius = labelSizePx * 1.5f, center = center)
                    drawCircle(Color.White, radius = labelSizePx * 1.5f, center = center, style = Stroke(width = 2f))
                    val label = reading?.rssi?.takeIf { it > SignalHeatmap.NOT_HEARD_RSSI }?.toString() ?: "–"
                    drawContext.canvas.nativeCanvas.drawText(label, center.x, center.y + labelSizePx * 0.35f, labelPaint)
                }
            }
            if (measuring) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }

        Text(
            if (positions.isEmpty()) "Tap the plan where you are standing to take the first measurement."
            else "Tap to measure at a new spot. Tap an existing point to remove it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Legend
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(-50, -60, -67, -75, -85).forEach { dbm ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(Color(SignalHeatmap.colorArgb(dbm.toFloat(), alpha = 255))))
                    Text(" $dbm", style = MaterialTheme.typography.labelSmall)
                }
            }
            Text("dBm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Summary
        if (heatmap.points.isNotEmpty()) {
            val heard = heatmap.points.filter { it.rssi > SignalHeatmap.NOT_HEARD_RSSI }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Coverage summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    coverage?.let {
                        Text("${(it * 100).roundToInt()}% of the measured area is at ${SignalQuality.GOOD.minRssi} dBm or better (good for voice/video).")
                    }
                    if (heard.isNotEmpty()) {
                        Text(
                            "Strongest ${heard.maxOf { it.rssi }} dBm · weakest ${heard.minOf { it.rssi }} dBm · " +
                                "${heatmap.points.size - heard.size} of ${heatmap.points.size} points with no signal",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    val dead = heatmap.points.count { SignalQuality.of(it.rssi.toFloat()) >= SignalQuality.WEAK }
                    if (dead > 0) {
                        Text(
                            "$dead point${if (dead == 1) "" else "s"} weaker than ${SignalQuality.FAIR.minRssi} dBm: likely dead zones.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        "Areas further than about a third of the plan from any measurement are left uncoloured rather than guessed.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        OutlinedButton(onClick = viewModel::undoLastPoint, enabled = positions.isNotEmpty() && !measuring) {
            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Undo last point")
        }
        Text(
            "Android limits apps to 4 Wi-Fi scans every 2 minutes. For a faster survey, turn off \"Wi-Fi scan throttling\" in Developer options.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    pointToDelete?.let { pointId ->
        AlertDialog(
            onDismissRequest = { pointToDelete = null },
            title = { Text("Remove this measurement?") },
            confirmButton = { TextButton(onClick = { viewModel.deletePoint(pointId); pointToDelete = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { pointToDelete = null }) { Text("Cancel") } }
        )
    }
}

/** Decodes the floor plan downsampled to at most ~2000 px on its long edge. */
private fun decodePlan(path: String): ImageBitmap? {
    if (!File(path).isFile) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2000) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}
