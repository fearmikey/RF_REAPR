package com.fearmikey.rf_reapr.ui.network

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fearmikey.rf_reapr.ui.theme.PhysicalRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PacketCaptureScreen(
    onBack: () -> Unit,
    viewModel: PacketCaptureViewModel = viewModel(),
) {
    val context = LocalContext.current
    val isCapturing by viewModel.isCapturing.collectAsStateWithLifecycle()
    val isRootMode by viewModel.isRootMode.collectAsStateWithLifecycle()
    val hasRoot by viewModel.hasRoot.collectAsStateWithLifecycle()
    val hasTcpdump by viewModel.hasTcpdump.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val activePath by viewModel.activeFilePath.collectAsStateWithLifecycle()

    var showRootWarningDialog by remember { mutableStateOf(value = false) }

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startStandardCapture()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Packet Capture") },
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
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Error Message
            if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            // Mode Selection
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Capture Mode",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Standard Capture (No Root)",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        RadioButton(
                            selected = !isRootMode,
                            onClick = { 
                                if (!isCapturing) viewModel.toggleMode(false) 
                            },
                            enabled = !isCapturing
                        )
                    }
                    Text(
                        "Captures Layer 3 IP traffic using Android's VpnService. Works on all devices.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Comprehensive Capture (Root)",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (hasRoot) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                        )
                        RadioButton(
                            selected = isRootMode,
                            onClick = { 
                                if (!isCapturing) {
                                    if (hasRoot) {
                                        showRootWarningDialog = true
                                    } else {
                                        viewModel.toggleMode(true) // Will immediately fail if tried
                                    }
                                }
                            },
                            enabled = !isCapturing
                        )
                    }
                    Text(
                        "Captures full Layer 2/3 frames using tcpdump. Requires rooted device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!hasRoot) {
                        Text(
                            "Root access not detected on this device.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (!hasTcpdump) {
                        Text(
                            "tcpdump binary missing. Root capture unavailable.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            // Status and Controls
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isCapturing) "Capture in Progress" else "Ready to Capture",
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (isCapturing) PhysicalRed else MaterialTheme.colorScheme.onSurface
                    )
                    
                    AnimatedVisibility(visible = (isCapturing && isRootMode) && (activePath != null)) {
                        Text(
                            text = "Writing to: $activePath",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = {
                                if (isRootMode) {
                                    viewModel.startRootCapture()
                                } else {
                                    val vpnIntent = VpnService.prepare(context)
                                    if (vpnIntent != null) {
                                        vpnLauncher.launch(vpnIntent)
                                    } else {
                                        viewModel.startStandardCapture() // Already have permission
                                    }
                                }
                            },
                            enabled = !isCapturing,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Start", style = MaterialTheme.typography.labelMedium)
                        }
                        
                        FilledTonalButton(
                            onClick = {
                                if (isRootMode) {
                                    viewModel.stopRootCapture()
                                } else {
                                    viewModel.stopStandardCapture()
                                }
                            },
                            enabled = isCapturing,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isCapturing) PhysicalRed else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isCapturing) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Stop", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            
            // Info Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(modifier = Modifier.padding(16.dp)) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        "Captured packets are saved as .pcap files. You can export these files from the 'Packet Capture Logs' menu in the toolkit to analyze them in Wireshark.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }

    if (showRootWarningDialog) {
        AlertDialog(
            onDismissRequest = { showRootWarningDialog = false },
            title = { Text("Root Access Required") },
            text = {
                Text(
                    "Comprehensive mode executes 'su' to run tcpdump directly on the network interfaces. " +
                    "Your device will prompt you to grant root permissions to RF_REAPR.\n\n" +
                    "Proceed?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.toggleMode(true)
                        showRootWarningDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PhysicalRed)
                ) {
                    Text("Enable Root Mode")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRootWarningDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
