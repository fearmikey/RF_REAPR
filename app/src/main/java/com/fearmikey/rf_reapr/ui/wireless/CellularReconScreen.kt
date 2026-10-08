package com.fearmikey.rf_reapr.ui.wireless

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoLte
import android.telephony.TelephonyManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CellularReconScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var cellInfoList by remember { mutableStateOf<List<CellInfo>>(emptyList()) }
    var hasPermission by remember { mutableStateOf(false) }

    val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    @SuppressLint("MissingPermission")
    fun refreshCells() {
        if (hasPermission) {
            cellInfoList = telephonyManager.allCellInfo ?: emptyList()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = permissions.values.all { it }
        if (hasPermission) {
            refreshCells()
        }
    }

    LaunchedEffect(Unit) {
        val fineLoc = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val phoneState = ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        
        hasPermission = fineLoc && phoneState
        
        if (hasPermission) {
            refreshCells()
        } else {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_PHONE_STATE
            ))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cellular Tower Recon") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshCells() }, enabled = hasPermission) {
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
            if (!hasPermission) {
                Text("Location and Phone State permissions are required to scan cell towers.", color = MaterialTheme.colorScheme.error)
            } else if (cellInfoList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CellTower, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No cell towers found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Ensure SIM is inserted and mobile data is on.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Text("Operator: ${telephonyManager.networkOperatorName}", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(cellInfoList) { info ->
                        CellInfoCard(info)
                    }
                }
            }
        }
    }
}

@Composable
fun CellInfoCard(info: CellInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            val level = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                info.cellSignalStrength.level
            } else {
                if (info is CellInfoLte) info.cellSignalStrength.level else 0
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (info.isRegistered) "Connected Tower" else "Neighboring Tower",
                    color = if (info.isRegistered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = "$level / 4",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            if (info is CellInfoLte) {
                val identity = info.cellIdentity
                val signal = info.cellSignalStrength
                Text("Type: LTE", style = MaterialTheme.typography.bodyMedium)
                
                @Suppress("DEPRECATION")
                val mcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) identity.mccString else identity.mcc.toString()
                @Suppress("DEPRECATION")
                val mnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) identity.mncString else identity.mnc.toString()
                
                Text("MCC/MNC: $mcc/$mnc", style = MaterialTheme.typography.bodyMedium)
                Text("Cell ID (CID): ${identity.ci}", style = MaterialTheme.typography.bodyMedium)
                Text("Tracking Area Code (TAC): ${identity.tac}", style = MaterialTheme.typography.bodyMedium)
                Text("Physical Cell ID (PCI): ${identity.pci}", style = MaterialTheme.typography.bodyMedium)
                Text("Signal (dBm): ${signal.dbm}", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("Type: ${info.javaClass.simpleName}", style = MaterialTheme.typography.bodyMedium)
                val dbm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) info.cellSignalStrength.dbm else "N/A"
                Text("Signal (dBm): $dbm", style = MaterialTheme.typography.bodyMedium)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Text("Cell Identity: ${info.cellIdentity}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
        }
    }
}
