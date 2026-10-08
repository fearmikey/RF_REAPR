package com.fearmikey.rf_reapr.ui.network

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptivePortalDetectorScreen(onBack: () -> Unit) {
    var resultText by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    var statusState by remember { mutableStateOf(0) } // 0 = normal, 1 = error, 2 = success
    
    val coroutineScope = rememberCoroutineScope()

    suspend fun performTest() {
        isTesting = true
        resultText = "Testing known generate_204 endpoints..."
        statusState = 0
        
        withContext(Dispatchers.IO) {
            val endpoints = listOf(
                "http://connectivitycheck.gstatic.com/generate_204",
                "http://clients3.google.com/generate_204",
                "http://detectportal.firefox.com/success.txt"
            )
            
            val client = OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
                
            val sb = java.lang.StringBuilder()
            var detectedInterception = false
            var allFailed = true
            
            for (url in endpoints) {
                try {
                    val request = Request.Builder().url(url).build()
                    client.newCall(request).execute().use { response ->
                        allFailed = false
                        val code = response.code
                        sb.append("Testing $url\n-> HTTP $code\n")
                        
                        if (code in 300..399) {
                            detectedInterception = true
                            sb.append("-> Redirected to: ${response.header("Location")}\n")
                        } else if (code == 200 && url.contains("generate_204")) {
                            detectedInterception = true
                            sb.append("-> Expected 204, got 200. Portal intercepted.\n")
                        }
                        sb.append("\n")
                    }
                } catch (e: Exception) {
                    sb.append("Testing $url\n-> Error: ${e.message}\n\n")
                }
            }
            
            if (allFailed) {
                resultText = sb.toString() + "All tests failed. You might have no internet connection at all."
                statusState = 1
            } else if (detectedInterception) {
                resultText = sb.toString() + "🚨 CAPTIVE PORTAL / INTERCEPTION DETECTED! 🚨\nYour traffic is being redirected."
                statusState = 1
            } else {
                resultText = sb.toString() + "✅ No captive portal detected. Direct internet access available."
                statusState = 2
            }
            
            isTesting = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Captive Portal Detector") },
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
            Text(
                "This tool checks if the current network is intercepting HTTP traffic (like a hotel or airport WiFi) by attempting to reach known endpoints that should return an empty HTTP 204 No Content response.",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Button(
                onClick = { coroutineScope.launch { performTest() } },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isTesting
            ) {
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.Sensors, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Run Test")
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            if (resultText.isNotEmpty()) {
                Text(
                    text = resultText,
                    color = when (statusState) {
                        1 -> MaterialTheme.colorScheme.error
                        2 -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
