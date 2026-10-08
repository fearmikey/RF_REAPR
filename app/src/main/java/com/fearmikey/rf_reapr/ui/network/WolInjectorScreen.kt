package com.fearmikey.rf_reapr.ui.network

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WolInjectorScreen(onBack: () -> Unit) {
    var macInput by remember { mutableStateOf("") }
    var ipInput by remember { mutableStateOf("255.255.255.255") }
    var portInput by remember { mutableStateOf("9") }
    
    var resultMessage by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    
    val coroutineScope = rememberCoroutineScope()

    suspend fun sendMagicPacket() {
        isSending = true
        resultMessage = "Sending..."
        
        withContext(Dispatchers.IO) {
            try {
                val cleanMac = macInput.replace("[:-]".toRegex(), "")
                if (cleanMac.length != 12) {
                    resultMessage = "Invalid MAC address format (must be 12 hex chars)"
                    return@withContext
                }
                
                val macBytes = ByteArray(6)
                for (i in 0..5) {
                    macBytes[i] = cleanMac.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                }
                
                val bytes = ByteArray(6 + 16 * macBytes.size)
                for (i in 0..5) {
                    bytes[i] = 0xff.toByte()
                }
                for (i in 6 until bytes.size step macBytes.size) {
                    System.arraycopy(macBytes, 0, bytes, i, macBytes.size)
                }
                
                val address = InetAddress.getByName(ipInput)
                val port = portInput.toIntOrNull() ?: 9
                val packet = DatagramPacket(bytes, bytes.size, address, port)
                
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    socket.send(packet)
                }
                resultMessage = "Magic packet sent successfully to $ipInput:$port!"
            } catch (e: Exception) {
                resultMessage = "Error: ${e.message}"
            } finally {
                isSending = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wake-on-LAN Injector") },
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
                value = macInput,
                onValueChange = { macInput = it },
                label = { Text("Target MAC Address") },
                placeholder = { Text("00:1A:2B:3C:4D:5E") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = ipInput,
                    onValueChange = { ipInput = it },
                    label = { Text("Broadcast IP") },
                    modifier = Modifier.weight(2f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                
                OutlinedTextField(
                    value = portInput,
                    onValueChange = { portInput = it },
                    label = { Text("Port") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            
            Button(
                onClick = { coroutineScope.launch { sendMagicPacket() } },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSending && macInput.isNotBlank() && ipInput.isNotBlank()
            ) {
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send Magic Packet")
                }
            }
            
            if (resultMessage.isNotEmpty()) {
                Text(
                    text = resultMessage,
                    color = if (resultMessage.startsWith("Error") || resultMessage.startsWith("Invalid")) 
                            MaterialTheme.colorScheme.error 
                          else 
                            MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
