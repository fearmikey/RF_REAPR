package com.fearmikey.rf_reapr.ui.web

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fearmikey.rf_reapr.domain.model.ExifMetadata
import com.fearmikey.rf_reapr.domain.repository.SanitizedImage
import com.fearmikey.rf_reapr.domain.service.ExifPrivacyAnalyzer
import com.fearmikey.rf_reapr.domain.service.ExifPrivacyFinding
import com.fearmikey.rf_reapr.ui.scanner.SeverityBadge
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExifInspectorScreen(
    viewModel: ExifInspectorViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val copy = rememberCopyAction()
    var showRawTags by rememberSaveable { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectImage(it) }
    }
    val sanitizedMime = uiState.sanitized?.mimeType ?: "image/jpeg"
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(sanitizedMime)
    ) { uri: Uri? ->
        uri?.let { viewModel.saveSanitizedTo(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("EXIF Metadata Inspector") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    viewModel.report()?.let { report ->
                        IconButton(onClick = { copy("Report", report) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy report")
                        }
                    }
                    if (uiState.selectedUri != null) {
                        IconButton(onClick = { viewModel.clear() }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Clear")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            uiState.errorMessage?.let { error ->
                MessageBanner(error, isError = true, onDismiss = viewModel::dismissMessage)
            }
            uiState.successMessage?.let { success ->
                MessageBanner(success, isError = false, onDismiss = viewModel::dismissMessage)
            }

            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (uiState.selectedUri == null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Inspect Image Metadata",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "See what a photo reveals: where and when it was taken, on which device, by whom, and whether " +
                                "it was edited. Everything runs on the device, and a verified metadata-free copy can be made before sharing.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = { imagePickerLauncher.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select Image")
                        }
                    }
                }
                return@Column
            }

            val meta = uiState.metadata
            FileCard(meta, onChange = { imagePickerLauncher.launch("image/*") }, onCopyHash = { copy("SHA-256", it) })
            if (meta == null) return@Column

            PrivacyCard(uiState.findings, uiState.editingIndicators)

            if (meta.hasGps) {
                GpsCard(meta, onCopy = { copy("Coordinates", it) }, onOpenMap = { openMapLocation(context, meta.latitude!!, meta.longitude!!) })
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Camera & Capture", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    HorizontalDivider()
                    val rows = listOf(
                        "Camera make" to meta.make, "Camera model" to meta.model, "Lens" to meta.lensModel,
                        "Body serial" to meta.rawTags["BodySerialNumber"], "Lens serial" to meta.rawTags["LensSerialNumber"],
                        "Owner" to meta.rawTags["CameraOwnerName"], "Artist" to meta.rawTags["Artist"],
                        "Copyright" to meta.rawTags["Copyright"], "Software" to meta.software,
                        "Taken" to meta.dateTimeOriginal, "UTC offset" to (meta.rawTags["OffsetTimeOriginal"] ?: meta.rawTags["OffsetTime"]),
                        "Focal length" to meta.focalLength, "Aperture" to meta.fNumber, "Shutter speed" to meta.exposureTime,
                        "ISO" to meta.iso, "Flash" to meta.flash, "White balance" to meta.whiteBalance, "Orientation" to meta.orientation
                    ).filter { !it.second.isNullOrBlank() }
                    if (rows.isEmpty()) {
                        Text("No camera information in this file.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        rows.forEach { (label, value) -> ExifDetailRow(label, value) }
                    }
                }
            }

            if (meta.rawTags.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "All Tags (${meta.rawTags.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { showRawTags = !showRawTags }) {
                                Text(if (showRawTags) "Hide" else "Show")
                            }
                        }
                        AnimatedVisibility(visible = showRawTags) {
                            SelectionContainer {
                                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    HorizontalDivider()
                                    meta.rawTags.toSortedMap().forEach { (tag, value) ->
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                            Text(text = tag, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                            Text(text = value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1.5f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SanitizerCard(
                isStripping = uiState.isStripping,
                sanitized = uiState.sanitized,
                onStrip = viewModel::stripExif,
                onShare = { shareSanitizedImage(context, it) },
                onSave = { image ->
                    val base = (meta.fileName ?: "image").substringBeforeLast('.')
                    saveLauncher.launch("${base}_clean.${image.file.extension}")
                }
            )
        }
    }
}

@Composable
private fun MessageBanner(text: String, isError: Boolean, onDismiss: () -> Unit) {
    val (container, content, tint) = if (isError) {
        Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, MaterialTheme.colorScheme.error)
    } else {
        Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.primary)
    }
    Card(colors = CardDefaults.cardColors(containerColor = container), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(if (isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle, contentDescription = null, tint = tint)
            Text(text, color = content, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = tint)
            }
        }
    }
}

@Composable
private fun FileCard(meta: ExifMetadata?, onChange: () -> Unit, onCopyHash: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = meta?.fileName ?: "Selected image",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = onChange, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                    Text("Change")
                }
            }
            meta?.let {
                Text(
                    listOfNotNull(it.mimeType, it.fileSizeFormatted, it.dimensionsFormatted).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall
                )
                it.sha256?.let { hash ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "SHA-256 $hash",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onCopyHash(hash) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy hash", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyCard(findings: List<ExifPrivacyFinding>, editingIndicators: List<String>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("What This Image Reveals", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                ExifPrivacyAnalyzer.overallRisk(findings)?.let { SeverityBadge(it) }
            }
            if (findings.isEmpty()) {
                Text(
                    "No location, device, owner or timestamp metadata was found. The picture itself can still identify a place or person.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            findings.forEach { finding ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SeverityBadge(finding.severity)
                        Text(finding.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Text(finding.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (editingIndicators.isNotEmpty()) {
                HorizontalDivider()
                Text("Signs of Editing", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                editingIndicators.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun GpsCard(meta: ExifMetadata, onCopy: (String) -> Unit, onOpenMap: () -> Unit) {
    val latitude = meta.latitude ?: return
    val longitude = meta.longitude ?: return
    val decimal = ExifPrivacyAnalyzer.formatDecimal(latitude, longitude)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(
                    text = "Embedded GPS Location",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(text = decimal, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
            Text(text = ExifPrivacyAnalyzer.formatDms(latitude, longitude), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            listOfNotNull(
                meta.altitude?.let { "Altitude ${String.format(Locale.US, "%.1f", it)} m" },
                ExifPrivacyAnalyzer.gpsTimestampUtc(meta.rawTags)?.let { "Fix time $it" },
                meta.rawTags["GPSImgDirection"]?.let(ExifPrivacyAnalyzer::parseRational)?.let { "Facing ${String.format(Locale.US, "%.0f", it)}°" },
                meta.rawTags["GPSProcessingMethod"]?.let { "Source: $it" }
            ).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenMap, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Map, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Map")
                }
                OutlinedButton(onClick = { onCopy(decimal) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy")
                }
            }
        }
    }
}

@Composable
private fun SanitizerCard(
    isStripping: Boolean,
    sanitized: SanitizedImage?,
    onStrip: () -> Unit,
    onShare: (SanitizedImage) -> Unit,
    onSave: (SanitizedImage) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CleaningServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(text = "Metadata Sanitizer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Text(
                text = "Re-encodes the pixels into a new file, which drops EXIF, GPS, XMP, the embedded thumbnail and anything " +
                    "appended to the file. The copy is then read back to confirm it is clean. The original is not modified.",
                style = MaterialTheme.typography.bodySmall
            )
            Button(onClick = onStrip, enabled = !isStripping, modifier = Modifier.fillMaxWidth()) {
                if (isStripping) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Creating clean copy…")
                } else {
                    Icon(Icons.Default.Shield, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (sanitized == null) "Create Clean Copy" else "Create Again")
                }
            }

            sanitized?.let { image ->
                Text(
                    "${image.width} × ${image.height} px · ${image.sizeFormatted} · ${if (image.mimeType == "image/png") "PNG" else "JPEG"}" +
                        if (image.downscaled) " · scaled down to fit in memory" else "",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onShare(image) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share")
                    }
                    OutlinedButton(onClick = { onSave(image) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save As")
                    }
                }
            }
        }
    }
}

@Composable
private fun ExifDetailRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

private fun openMapLocation(context: Context, lat: Double, lon: Double) {
    // Locale.US keeps the decimal point a dot regardless of the device language.
    val coords = String.format(Locale.US, "%.6f,%.6f", lat, lon)
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, "geo:$coords?q=$coords(Photo%20location)".toUri()))
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, ExifPrivacyAnalyzer.mapUrl(lat, lon).toUri()))
        } catch (_: Exception) {
            Toast.makeText(context, "No map application or browser available", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun shareSanitizedImage(context: Context, image: SanitizedImage) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", image.file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = image.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Sanitized Image"))
    } catch (e: Exception) {
        Toast.makeText(context, "Could not share the image: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}
