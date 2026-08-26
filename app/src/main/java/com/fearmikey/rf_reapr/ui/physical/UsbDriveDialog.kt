package com.fearmikey.rf_reapr.ui.physical

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fearmikey.rf_reapr.domain.model.UsbDriveInfo

/**
 * Lists USB flash drives detected via [android.os.storage.StorageManager] (i.e. plugged in
 * through an OTG adapter) and lets the user grant the app access to one, then write the
 * current DuckyScript payload to its root so it can act as a "ducky drive".
 */
@Composable
fun UsbDriveDialog(
    drives: List<UsbDriveInfo>,
    isFlashing: Boolean,
    onRequestAccess: (UsbDriveInfo) -> Unit,
    onFlash: (UsbDriveInfo) -> Unit,
    onForget: (UsbDriveInfo) -> Unit,
    onUseFilePicker: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Usb, contentDescription = null) },
        title = { Text("USB Ducky Drives") },
        text = {
            Column {
                Text(
                    "Connect a USB flash drive via an OTG adapter to configure it as a ducky drive.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (drives.isEmpty()) {
                    Text(
                        "No USB drives detected yet.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(drives, key = { it.id }) { drive ->
                            UsbDriveRow(
                                drive = drive,
                                isFlashing = isFlashing,
                                onRequestAccess = { onRequestAccess(drive) },
                                onFlash = { onFlash(drive) },
                                onForget = { onForget(drive) }
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            TextButton(onClick = onUseFilePicker) { Text("Use File Picker") }
        }
    )
}

@Composable
private fun UsbDriveRow(
    drive: UsbDriveInfo,
    isFlashing: Boolean,
    onRequestAccess: () -> Unit,
    onFlash: () -> Unit,
    onForget: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(drive.name, style = MaterialTheme.typography.titleSmall)
                val (statusText, statusColor) = when {
                    !drive.isAttached -> "Not connected" to MaterialTheme.colorScheme.error
                    drive.isAuthorized -> "Ready to flash" to MaterialTheme.colorScheme.primary
                    else -> "Access needed" to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(statusText, style = MaterialTheme.typography.bodySmall, color = statusColor)
            }
            if (drive.isAuthorized) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Authorized",
                    tint = if (drive.isAttached) MaterialTheme.colorScheme.primary else Color.Gray
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (drive.isAttached && !drive.isAuthorized) {
                Button(onClick = onRequestAccess) { Text("Grant Access") }
            }
            if (drive.isAttached && drive.isAuthorized) {
                Button(onClick = onFlash, enabled = !isFlashing) { Text("Flash Script") }
            }
            if (drive.isAuthorized) {
                OutlinedButton(onClick = onForget) { Text("Forget") }
            }
        }
    }
}
