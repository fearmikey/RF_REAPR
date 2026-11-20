package com.fearmikey.rf_reapr.ui.physical

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.domain.service.UsbAuditAnalyzer
import com.fearmikey.rf_reapr.domain.service.UsbDeviceInfo
import com.fearmikey.rf_reapr.domain.service.UsbInterfaceInfo
import com.fearmikey.rf_reapr.ui.scanner.SeverityBadge
import kotlinx.coroutines.delay

private fun UsbDevice.toInfo() = UsbDeviceInfo(
    name = deviceName,
    vendorId = vendorId,
    productId = productId,
    // Descriptor strings can throw without USB permission on some versions.
    manufacturer = runCatching { manufacturerName }.getOrNull(),
    product = runCatching { productName }.getOrNull(),
    deviceClass = deviceClass,
    interfaces = (0 until interfaceCount).map { i ->
        val intf = getInterface(i)
        UsbInterfaceInfo(intf.interfaceClass, intf.interfaceSubclass, intf.interfaceProtocol, intf.endpointCount)
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsbOtgAuditorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var devices by remember { mutableStateOf<List<UsbDeviceInfo>>(emptyList()) }

    // Poll while visible: ATTACHED broadcasts only reach manifest-declared activities.
    LaunchedEffect(Unit) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
        while (true) {
            devices = usbManager?.deviceList?.values?.map { it.toInfo() }.orEmpty()
            delay(1500)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("USB OTG Auditor") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (devices.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Usb, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        Text("No USB devices connected", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Plug a device in through an OTG adapter. The list updates automatically. Use this to check unknown USB sticks, cables and chargers for hidden keyboards or network adapters before trusting them on a computer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(devices, key = { it.name }) { UsbDeviceCard(it) }
            }
        }
    }
}

@Composable
fun UsbDeviceCard(device: UsbDeviceInfo) {
    val findings = remember(device) { UsbAuditAnalyzer.findings(device) }
    val worst = findings.firstOrNull()?.severity
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (worst != null && worst >= RiskLevel.HIGH) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer) else CardDefaults.cardColors()
    ) {
        SelectionContainer {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Usb, contentDescription = null,
                        tint = if (worst != null && worst >= RiskLevel.MEDIUM) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(device.product ?: "Unknown device", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    worst?.let { SeverityBadge(it) }
                }
                Text(
                    "Manufacturer: ${device.manufacturer ?: UsbAuditAnalyzer.vendorName(device.vendorId) ?: "Unknown"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "VID:PID %04X:%04X".format(device.vendorId, device.productId) +
                        (UsbAuditAnalyzer.vendorName(device.vendorId)?.let { " ($it)" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
                Text(device.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                if (findings.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    findings.forEach { f ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(f.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            SeverityBadge(f.severity)
                        }
                        Text(f.detail, style = MaterialTheme.typography.bodySmall)
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text("Interfaces (${device.interfaces.size})", style = MaterialTheme.typography.labelMedium)
                device.interfaces.forEachIndexed { i, intf ->
                    Text(
                        "#$i ${UsbAuditAnalyzer.className(intf.interfaceClass, intf.subclass, intf.protocol)}  " +
                            "[%02X/%02X/%02X] · %d endpoints".format(intf.interfaceClass, intf.subclass, intf.protocol, intf.endpointCount),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
