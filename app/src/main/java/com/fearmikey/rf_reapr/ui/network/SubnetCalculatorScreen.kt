package com.fearmikey.rf_reapr.ui.network

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.net.InetAddress
import java.nio.ByteBuffer
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubnetCalculatorScreen(onBack: () -> Unit) {
    var ipInput by remember { mutableStateOf("") }
    var cidrInput by remember { mutableStateOf("24") }
    
    var networkAddress by remember { mutableStateOf("") }
    var broadcastAddress by remember { mutableStateOf("") }
    var netmask by remember { mutableStateOf("") }
    var totalHosts by remember { mutableStateOf("") }
    var usableRange by remember { mutableStateOf("") }

    fun calculateSubnet() {
        try {
            val cidr = cidrInput.toIntOrNull() ?: return
            if (cidr < 0 || cidr > 32) return
            
            val inetAddress = InetAddress.getByName(ipInput)
            val ipBytes = inetAddress.address
            if (ipBytes.size != 4) return // IPv4 only for now
            
            val ipInt = ByteBuffer.wrap(ipBytes).int
            val maskInt = if (cidr == 0) 0 else -1 shl (32 - cidr)
            val networkInt = ipInt and maskInt
            val broadcastInt = networkInt or maskInt.inv()
            
            val networkBytes = ByteBuffer.allocate(4).putInt(networkInt).array()
            val broadcastBytes = ByteBuffer.allocate(4).putInt(broadcastInt).array()
            val maskBytes = ByteBuffer.allocate(4).putInt(maskInt).array()
            
            networkAddress = InetAddress.getByAddress(networkBytes).hostAddress ?: ""
            broadcastAddress = InetAddress.getByAddress(broadcastBytes).hostAddress ?: ""
            netmask = InetAddress.getByAddress(maskBytes).hostAddress ?: ""
            
            val numHosts = 2.0.pow(32 - cidr).toLong()
            if (cidr == 32) {
                totalHosts = "1"
                usableRange = networkAddress
            } else if (cidr == 31) {
                totalHosts = "2"
                usableRange = "$networkAddress - $broadcastAddress"
            } else {
                totalHosts = (numHosts - 2).toString()
                val firstHostBytes = ByteBuffer.allocate(4).putInt(networkInt + 1).array()
                val lastHostBytes = ByteBuffer.allocate(4).putInt(broadcastInt - 1).array()
                usableRange = "${InetAddress.getByAddress(firstHostBytes).hostAddress} - ${InetAddress.getByAddress(lastHostBytes).hostAddress}"
            }
        } catch (_: Exception) {
            networkAddress = "Invalid Input"
            broadcastAddress = ""
            netmask = ""
            totalHosts = ""
            usableRange = ""
        }
    }

    LaunchedEffect(ipInput, cidrInput) {
        calculateSubnet()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Subnet Calculator") },
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
                value = ipInput,
                onValueChange = { ipInput = it },
                label = { Text("IP Address") },
                placeholder = { Text("e.g. 192.168.1.100") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            
            OutlinedTextField(
                value = cidrInput,
                onValueChange = { cidrInput = it },
                label = { Text("CIDR Prefix") },
                placeholder = { Text("e.g. 24") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (networkAddress.isNotEmpty()) {
                if (networkAddress == "Invalid Input") {
                    Text("Invalid IP or CIDR", color = MaterialTheme.colorScheme.error)
                } else {
                    Text("Netmask: $netmask", style = MaterialTheme.typography.bodyLarge)
                    Text("Network Address: $networkAddress", style = MaterialTheme.typography.bodyLarge)
                    Text("Broadcast Address: $broadcastAddress", style = MaterialTheme.typography.bodyLarge)
                    Text("Usable Hosts: $totalHosts", style = MaterialTheme.typography.bodyLarge)
                    Text("Host Range: $usableRange", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
