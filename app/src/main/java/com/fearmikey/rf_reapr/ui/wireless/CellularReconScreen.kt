package com.fearmikey.rf_reapr.ui.wireless

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoWcdma
import android.telephony.CellIdentityNr
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.domain.service.CellAnalyzer
import com.fearmikey.rf_reapr.domain.service.CellSummary
import com.fearmikey.rf_reapr.domain.service.CellTech
import com.fearmikey.rf_reapr.ui.scanner.SeverityBadge
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

@Suppress("DEPRECATION")
private fun CellInfo.toSummary(): CellSummary? {
    val v = CellAnalyzer
    val level = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) cellSignalStrength.level else 0
    return when {
        this is CellInfoLte -> {
            val id = cellIdentity
            val s = cellSignalStrength
            CellSummary(
                CellTech.LTE, isRegistered,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mccString else v.valid(id.mcc)?.toString(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mncString else v.valid(id.mnc)?.toString(),
                v.valid(id.ci)?.toLong(), v.valid(id.tac), v.valid(id.pci),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) v.valid(id.earfcn) else null,
                v.valid(s.dbm), s.level,
                rsrq = v.valid(s.rsrq),
                sinr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) v.valid(s.rssnr) else null,
                timingAdvance = v.valid(s.timingAdvance),
                bandwidthKhz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) v.valid(id.bandwidth) else null,
                operator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.operatorAlphaShort?.toString() else null
            )
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && this is CellInfoNr -> {
            val id = cellIdentity as CellIdentityNr
            val s = cellSignalStrength as CellSignalStrengthNr
            CellSummary(
                CellTech.NR, isRegistered, id.mccString, id.mncString,
                v.valid(id.nci), v.valid(id.tac), v.valid(id.pci), v.valid(id.nrarfcn),
                v.valid(s.ssRsrp) ?: v.valid(s.dbm), s.level,
                rsrq = v.valid(s.ssRsrq), sinr = v.valid(s.ssSinr),
                operator = id.operatorAlphaShort?.toString()
            )
        }
        this is CellInfoWcdma -> {
            val id = cellIdentity
            CellSummary(
                CellTech.WCDMA, isRegistered,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mccString else v.valid(id.mcc)?.toString(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mncString else v.valid(id.mnc)?.toString(),
                v.valid(id.cid)?.toLong(), v.valid(id.lac), v.valid(id.psc),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) v.valid(id.uarfcn) else null,
                v.valid(cellSignalStrength.dbm), cellSignalStrength.level
            )
        }
        this is CellInfoGsm -> {
            val id = cellIdentity
            CellSummary(
                CellTech.GSM, isRegistered,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mccString else v.valid(id.mcc)?.toString(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mncString else v.valid(id.mnc)?.toString(),
                v.valid(id.cid)?.toLong(), v.valid(id.lac),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) v.valid(id.bsic) else null,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) v.valid(id.arfcn) else null,
                v.valid(cellSignalStrength.dbm), cellSignalStrength.level,
                timingAdvance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v.valid(cellSignalStrength.timingAdvance) else null
            )
        }
        this is CellInfoCdma -> CellSummary(
            CellTech.CDMA, isRegistered, null, null,
            v.valid(cellIdentity.basestationId)?.toLong(), v.valid(cellIdentity.networkId), null, null,
            v.valid(cellSignalStrength.dbm), cellSignalStrength.level
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && this is CellInfoTdscdma -> CellSummary(
            CellTech.TDSCDMA, isRegistered, cellIdentity.mccString, cellIdentity.mncString,
            v.valid(cellIdentity.cid)?.toLong(), v.valid(cellIdentity.lac), v.valid(cellIdentity.cpid), v.valid(cellIdentity.uarfcn),
            v.valid(cellSignalStrength.dbm), cellSignalStrength.level
        )
        else -> null
    }.let { it?.copy(level = if (it.level == 0) level else it.level) }
}

@SuppressLint("MissingPermission")
private suspend fun TelephonyManager.freshCellInfo(context: Context): List<CellInfo> {
    // requestCellInfoUpdate (API 29+) asks the modem for new data; allCellInfo may be minutes old.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val fresh = runCatching {
            kotlinx.coroutines.withTimeoutOrNull(4000) {
                suspendCancellableCoroutine<List<CellInfo>> { cont ->
                    requestCellInfoUpdate(ContextCompat.getMainExecutor(context), object : TelephonyManager.CellInfoCallback() {
                        override fun onCellInfo(cellInfo: MutableList<CellInfo>) {
                            if (cont.isActive) cont.resume(cellInfo)
                        }
                        override fun onError(errorCode: Int, detail: Throwable?) {
                            if (cont.isActive) cont.resume(emptyList())
                        }
                    })
                }
            }
        }.getOrNull()
        if (!fresh.isNullOrEmpty()) return fresh
    }
    return runCatching { allCellInfo }.getOrNull().orEmpty()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CellularReconScreen(
    onFindings: (List<com.fearmikey.rf_reapr.domain.model.FindingDraft>) -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var cells by remember { mutableStateOf<List<CellSummary>>(emptyList()) }
    var hasPermission by remember { mutableStateOf(false) }
    var lastUpdate by remember { mutableLongStateOf(0L) }
    val servingHistory = remember { mutableStateListOf<CellSummary>() }
    val telephonyManager = remember { context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        hasPermission = permissions.values.all { it }
    }

    LaunchedEffect(Unit) {
        val fineLoc = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val phoneState = ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        hasPermission = fineLoc && phoneState
        if (!hasPermission) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE))
        }
    }

    // Auto-refresh every 5 s while visible.
    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        while (true) {
            val list = telephonyManager.freshCellInfo(context).mapNotNull { it.toSummary() }
                .sortedWith(compareByDescending<CellSummary> { it.registered }.thenByDescending { it.dbm ?: -200 })
            cells = list
            lastUpdate = System.currentTimeMillis()
            list.filter { it.registered }.forEach { s ->
                if (servingHistory.lastOrNull()?.key != s.key) servingHistory += s
            }
            delay(5000)
        }
    }

    val findings = remember(cells) { CellAnalyzer.findings(cells, servingHistory.toList()) }

    // Record only when the set of findings changes (cells refresh every 5 s).
    LaunchedEffect(findings.map { it.title }.toSet()) {
        if (findings.isNotEmpty()) onFindings(com.fearmikey.rf_reapr.domain.service.FindingAdapters.cellular(findings))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cellular Tower Recon") },
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!hasPermission) {
                item {
                    Text("Location and phone-state permissions are needed to read cell towers.", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = {
                        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE))
                    }) { Text("Grant permissions") }
                }
                return@LazyColumn
            }

            item {
                Column {
                    Text(
                        "Operator: ${telephonyManager.networkOperatorName.ifBlank { "unknown" }}" +
                            (telephonyManager.networkOperator.takeIf { it.length >= 5 }?.let { " (${it.take(3)}-${it.drop(3)})" } ?: ""),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "${cells.size} cells · ${servingHistory.map { it.key }.distinct().size} serving cells this session · refreshes every 5 s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (lastUpdate == 0L) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }

            if (findings.isNotEmpty()) {
                items(findings) { f ->
                    Card(colors = CardDefaults.cardColors(containerColor = if (f.severity >= RiskLevel.MEDIUM) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(f.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                SeverityBadge(f.severity)
                            }
                            Text(f.detail, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (cells.isEmpty() && lastUpdate > 0) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CellTower, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No cell information available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Check that a SIM is inserted, location is on, and airplane mode is off.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            items(cells, key = { it.key + it.registered }) { CellInfoCard(it) }
        }
    }
}

@Composable
fun CellInfoCard(cell: CellSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (cell.registered) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors()
    ) {
        SelectionContainer {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${cell.tech.label}${cell.band?.let { " · $it" } ?: ""}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(if (cell.registered) "SERVING" else "neighbour", style = MaterialTheme.typography.labelSmall)
                }
                SignalBars(cell.level)
                val ids = listOfNotNull(
                    if (cell.mcc != null) "MCC/MNC ${cell.mcc}/${cell.mnc}" else null,
                    cell.areaCode?.let { if (cell.tech == CellTech.LTE || cell.tech == CellTech.NR) "TAC $it" else "LAC $it" },
                    cell.cellId?.let { "CID $it" },
                    cell.physicalId?.let { if (cell.tech == CellTech.GSM) "BSIC $it" else if (cell.tech == CellTech.WCDMA) "PSC $it" else "PCI $it" }
                )
                if (ids.isNotEmpty()) Text(ids.joinToString(" · "), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                val radio = listOfNotNull(
                    cell.dbm?.let { "$it dBm" },
                    cell.rsrq?.let { "RSRQ $it dB" },
                    cell.sinr?.let { "SINR $it dB" },
                    cell.frequencyMhz?.let { String.format(Locale.US, "%.1f MHz", it) },
                    cell.channel?.let { "ch $it" },
                    cell.bandwidthKhz?.let { "${it / 1000} MHz wide" }
                )
                if (radio.isNotEmpty()) Text(radio.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                cell.distanceMeters?.let { Text("Timing advance ≈ ${it} m from tower", style = MaterialTheme.typography.bodySmall) }
                cell.operator?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun SignalBars(level: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        for (i in 1..4) {
            Surface(
                color = if (i <= level) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.size(width = 6.dp, height = (4 + i * 3).dp)
            ) {}
        }
        Spacer(Modifier.width(6.dp))
        Text("$level/4", style = MaterialTheme.typography.labelSmall)
    }
}
