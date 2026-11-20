package com.fearmikey.rf_reapr.ui.web

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.zip.CRC32

private data class HashSet(val crc32: String, val md5: String, val sha1: String, val sha256: String, val sha512: String) {
    fun all() = listOf("CRC32" to crc32, "MD5" to md5, "SHA-1" to sha1, "SHA-256" to sha256, "SHA-512" to sha512)
    fun contains(needle: String) = all().any { it.second.equals(needle, ignoreCase = true) }
}

private fun hashOf(bytes: ByteArray): HashSet {
    val crc = CRC32().apply { update(bytes) }.value
    fun d(alg: String) = MessageDigest.getInstance(alg).digest(bytes).joinToString("") { "%02x".format(it) }
    return HashSet("%08x".format(crc), d("MD5"), d("SHA-1"), d("SHA-256"), d("SHA-512"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HashCalculatorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var input by remember { mutableStateOf("") }
    var expected by remember { mutableStateOf("") }
    var hashes by remember { mutableStateOf<HashSet?>(null) }
    var sourceLabel by remember { mutableStateOf<String?>(null) }
    var hashingFile by remember { mutableStateOf(false) }

    LaunchedEffect(input) {
        // Typing text supersedes a previously hashed file.
        if (input.isNotEmpty()) {
            sourceLabel = null
            hashes = hashOf(input.toByteArray())
        } else if (sourceLabel == null) {
            hashes = null
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            hashingFile = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)!!.use { stream ->
                        val md = mapOf(
                            "MD5" to MessageDigest.getInstance("MD5"),
                            "SHA-1" to MessageDigest.getInstance("SHA-1"),
                            "SHA-256" to MessageDigest.getInstance("SHA-256"),
                            "SHA-512" to MessageDigest.getInstance("SHA-512")
                        )
                        val crc = CRC32()
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = stream.read(buffer)
                            if (n < 0) break
                            crc.update(buffer, 0, n)
                            md.values.forEach { it.update(buffer, 0, n) }
                        }
                        HashSet(
                            "%08x".format(crc.value),
                            md.getValue("MD5").digest().joinToString("") { "%02x".format(it) },
                            md.getValue("SHA-1").digest().joinToString("") { "%02x".format(it) },
                            md.getValue("SHA-256").digest().joinToString("") { "%02x".format(it) },
                            md.getValue("SHA-512").digest().joinToString("") { "%02x".format(it) }
                        )
                    }
                }.getOrNull()
            }
            if (result != null) {
                input = ""
                sourceLabel = uri.lastPathSegment?.substringAfterLast('/') ?: "selected file"
                hashes = result
            }
            hashingFile = false
        }
    }

    val match = expected.isNotBlank() && hashes?.contains(expected.trim()) == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hash Calculator") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Input text") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            OutlinedButton(onClick = { filePicker.launch("*/*") }, modifier = Modifier.fillMaxWidth(), enabled = !hashingFile) {
                if (hashingFile) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.FileOpen, contentDescription = null)
                }
                Spacer(Modifier.width(8.dp))
                Text(sourceLabel?.let { "Hashed: $it (pick another)" } ?: "Hash a file instead")
            }

            OutlinedTextField(
                value = expected,
                onValueChange = { expected = it },
                label = { Text("Compare against a known hash (optional)") },
                placeholder = { Text("Paste an expected checksum to verify") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = expected.isNotBlank() && hashes != null && !match,
                supportingText = {
                    if (expected.isNotBlank() && hashes != null) {
                        if (match) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Match: ${hashes!!.all().first { it.second.equals(expected.trim(), true) }.first}", color = Color(0xFF4CAF50))
                            }
                        } else {
                            Text("No match with any computed hash", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )

            HorizontalDivider()

            hashes?.let { h ->
                h.all().forEach { (algo, value) ->
                    val weak = algo == "MD5" || algo == "SHA-1" || algo == "CRC32"
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(algo, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                if (weak) Text("  (not for security)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { clipboard.setText(AnnotatedString(value)) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy $algo", modifier = Modifier.size(18.dp))
                            }
                        }
                        SelectionContainer {
                            Text(
                                value,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = if (match && value.equals(expected.trim(), true)) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            } ?: Text(
                "Type text or pick a file to compute CRC32, MD5, SHA-1, SHA-256 and SHA-512. Use the compare box to verify a download against its published checksum.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
