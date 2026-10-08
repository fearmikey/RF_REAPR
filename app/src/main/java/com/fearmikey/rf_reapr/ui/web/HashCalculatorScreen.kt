package com.fearmikey.rf_reapr.ui.web

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import java.security.MessageDigest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HashCalculatorScreen(onBack: () -> Unit) {
    var input by remember { mutableStateOf("") }
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    
    var md5 by remember { mutableStateOf("") }
    var sha1 by remember { mutableStateOf("") }
    var sha256 by remember { mutableStateOf("") }

    LaunchedEffect(input) {
        if (input.isEmpty()) {
            md5 = ""
            sha1 = ""
            sha256 = ""
            return@LaunchedEffect
        }
        val bytes = input.toByteArray()
        
        md5 = MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
        sha1 = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
        sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hash Calculator") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Input String") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            HashResultRow("MD5", md5, clipboardManager)
            HashResultRow("SHA-1", sha1, clipboardManager)
            HashResultRow("SHA-256", sha256, clipboardManager)
        }
    }
}

@Suppress("DEPRECATION")
@Composable
fun HashResultRow(algo: String, hash: String, clipboardManager: androidx.compose.ui.platform.ClipboardManager) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(algo, style = MaterialTheme.typography.labelLarge)
            if (hash.isNotEmpty()) {
                IconButton(onClick = { clipboardManager.setText(AnnotatedString(hash)) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                }
            }
        }
        OutlinedTextField(
            value = hash,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
