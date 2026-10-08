package com.fearmikey.rf_reapr.ui.physical

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsbOtgAuditorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var devices by remember { mutableStateOf<List<UsbDevice>>(emptyList()) }

    fun refreshDevices() {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
        devices = usbManager?.deviceList?.values?.toList() ?: emptyList()
    }

    LaunchedEffect(Unit) {
        refreshDevices()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("USB OTG Auditor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshDevices() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (devices.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Usb, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No USB devices connected.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Connect a device via OTG to audit.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(devices) { device ->
                        UsbDeviceCard(device)
                    }
                }
            }
        }
    }
}

@Composable
fun UsbDeviceCard(device: UsbDevice) {
    var hasSuspiciousInterface = false
    
    // Check interfaces for HID Keyboard (often used in BadUSB)
    for (i in 0 until device.interfaceCount) {
        val intf = device.getInterface(i)
        if (intf.interfaceClass == UsbConstants.USB_CLASS_HID) {
            // Very basic heuristic for HID keyboard
            if (intf.interfaceSubclass == 1 && intf.interfaceProtocol == 1) {
                hasSuspiciousInterface = true
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Usb, 
                    contentDescription = null,
                    tint = if (hasSuspiciousInterface) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = device.productName ?: "Unknown Device", 
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            Text("Manufacturer: ${device.manufacturerName ?: "Unknown"}", style = MaterialTheme.typography.bodyMedium)
            Text("Vendor ID: ${String.format("0x%04X", device.vendorId)}", style = MaterialTheme.typography.bodyMedium)
            Text("Product ID: ${String.format("0x%04X", device.productId)}", style = MaterialTheme.typography.bodyMedium)
            Text("Interfaces: ${device.interfaceCount}", style = MaterialTheme.typography.bodyMedium)
            
            if (hasSuspiciousInterface) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Suspicious: Device exposes an HID Keyboard interface.", 
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            
            Text("Interface Details:", style = MaterialTheme.typography.labelMedium)
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                Text(
                    "- Class: ${intf.interfaceClass}, Subclass: ${intf.interfaceSubclass}, Protocol: ${intf.interfaceProtocol}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}
