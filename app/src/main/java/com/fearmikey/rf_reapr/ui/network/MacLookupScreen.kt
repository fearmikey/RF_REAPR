package com.fearmikey.rf_reapr.ui.network

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacLookupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var macInput by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var databaseMap by remember { mutableStateOf<Map<String, String>?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val jsonString = context.assets.open("oui.json").bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(jsonString)
                val map = mutableMapOf<String, String>()
                val keys = jsonObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key.uppercase()] = jsonObject.getString(key)
                }
                databaseMap = map
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MAC OUI Lookup") },
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
            if (isLoading) {
                CircularProgressIndicator()
                Text("Loading local database...")
            } else {
                OutlinedTextField(
                    value = macInput,
                    onValueChange = { 
                        macInput = it
                        // Optional format as they type?
                    },
                    label = { Text("MAC Address") },
                    placeholder = { Text("e.g. 00:1A:11:xx:xx:xx") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            val cleanMac = macInput.replace("[:-]".toRegex(), "").uppercase()
                            if (cleanMac.length >= 6) {
                                val prefix = "${cleanMac.substring(0, 2)}:${cleanMac.substring(2, 4)}:${cleanMac.substring(4, 6)}"
                                result = databaseMap?.get(prefix) ?: "Vendor not found in offline database"
                            } else {
                                result = "Enter at least 6 hex characters"
                            }
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "Lookup")
                        }
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                if (result != null) {
                    Text(
                        text = "Vendor:",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = result!!,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "This tool uses a stripped-down offline database (oui.json). " +
                           "For a full list, replace the assets/oui.json file with a full IEEE OUI export.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
