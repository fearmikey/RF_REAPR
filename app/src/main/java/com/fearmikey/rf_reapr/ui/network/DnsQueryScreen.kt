package com.fearmikey.rf_reapr.ui.network

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsQueryScreen(onBack: () -> Unit) {
    var domainInput by remember { mutableStateOf("google.com") }
    var selectedType by remember { mutableStateOf("A (1)") }
    var selectedServer by remember { mutableStateOf("Cloudflare") }
    var resultText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    val recordTypes = listOf("A (1)", "AAAA (28)", "MX (15)", "TXT (16)", "NS (2)", "CNAME (5)")
    val servers = listOf("Cloudflare", "Google")
    
    var typeExpanded by remember { mutableStateOf(false) }
    var serverExpanded by remember { mutableStateOf(false) }

    suspend fun performQuery() {
        isLoading = true
        resultText = "Querying..."
        
        withContext(Dispatchers.IO) {
            try {
                val typeId = selectedType.substringAfter("(").substringBefore(")")
                val baseUrl = if (selectedServer == "Cloudflare") {
                    "https://cloudflare-dns.com/dns-query"
                } else {
                    "https://dns.google/resolve"
                }
                
                val url = "$baseUrl?name=$domainInput&type=$typeId"
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/dns-json")
                    .build()
                
                val client = OkHttpClient()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        resultText = "Error: HTTP ${response.code}"
                        return@use
                    }
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    
                    val sb = java.lang.StringBuilder()
                    sb.append("Status: ${json.optInt("Status")}\n\n")
                    
                    if (json.has("Answer")) {
                        val answers = json.getJSONArray("Answer")
                        sb.append("Answers (${answers.length()}):\n")
                        for (i in 0 until answers.length()) {
                            val answer = answers.getJSONObject(i)
                            sb.append("- ${answer.optString("name")} -> ${answer.optString("data")} (TTL: ${answer.optInt("TTL")})\n")
                        }
                    } else {
                        sb.append("No Answer records found.\n")
                    }
                    
                    if (json.has("Authority")) {
                        sb.append("\nAuthority:\n")
                        val auths = json.getJSONArray("Authority")
                        for (i in 0 until auths.length()) {
                            val auth = auths.getJSONObject(i)
                            sb.append("- ${auth.optString("name")} -> ${auth.optString("data")}\n")
                        }
                    }
                    
                    resultText = sb.toString()
                }
            } catch (e: Exception) {
                resultText = "Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DNS Query Tool") },
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
                value = domainInput,
                onValueChange = { domainInput = it },
                label = { Text("Target Domain") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = !typeExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = selectedType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Record Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        recordTypes.forEach { selectionOption ->
                            DropdownMenuItem(
                                text = { Text(selectionOption) },
                                onClick = {
                                    selectedType = selectionOption
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = serverExpanded,
                    onExpandedChange = { serverExpanded = !serverExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = selectedServer,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("DNS Server") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = serverExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = serverExpanded,
                        onDismissRequest = { serverExpanded = false }
                    ) {
                        servers.forEach { selectionOption ->
                            DropdownMenuItem(
                                text = { Text(selectionOption) },
                                onClick = {
                                    selectedServer = selectionOption
                                    serverExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    coroutineScope.launch {
                        performQuery()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && domainInput.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Query")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (resultText.isNotEmpty()) {
                Text("Result", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = resultText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
