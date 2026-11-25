package com.fearmikey.rf_reapr.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellIdentityNr
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.fearmikey.rf_reapr.data.db.dao.SurveyDao
import com.fearmikey.rf_reapr.data.db.dao.SurveySessionSummary
import com.fearmikey.rf_reapr.data.db.entity.SurveyObservationEntity
import com.fearmikey.rf_reapr.data.db.entity.SurveySessionEntity
import com.fearmikey.rf_reapr.data.local.ActiveProjectStore
import com.fearmikey.rf_reapr.data.survey.WifiScanSource
import com.fearmikey.rf_reapr.domain.repository.SURVEY_TASK_NAME
import com.fearmikey.rf_reapr.domain.repository.SurveyExportFormat
import com.fearmikey.rf_reapr.domain.repository.SurveyOptions
import com.fearmikey.rf_reapr.domain.repository.SurveyRepository
import com.fearmikey.rf_reapr.domain.repository.SurveySessionInfo
import com.fearmikey.rf_reapr.domain.repository.SurveyState
import com.fearmikey.rf_reapr.domain.service.ActiveTaskMonitor
import com.fearmikey.rf_reapr.domain.service.SurveyExporter
import com.fearmikey.rf_reapr.domain.service.SurveyFix
import com.fearmikey.rf_reapr.domain.service.SurveyKind
import com.fearmikey.rf_reapr.domain.service.SurveyMerger
import com.fearmikey.rf_reapr.domain.service.SurveyObservation
import com.fearmikey.rf_reapr.domain.service.SurveySighting
import com.fearmikey.rf_reapr.domain.service.WifiSecurityAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-lifetime geotagged survey. Registers [SURVEY_TASK_NAME] with ActiveTaskMonitor so
 * the foreground service keeps it (and GPS) alive while the screen is off, and so "Stop all"
 * ends it. Scans are passive: nothing is transmitted to the surveyed devices.
 */
class SurveyRepositoryImpl(
    context: Context,
    private val dao: SurveyDao,
    private val activeProjectStore: ActiveProjectStore,
    private val scope: CoroutineScope,
    private val appVersion: String
) : SurveyRepository {

    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val wifiSource = WifiScanSource(appContext)

    private val _state = MutableStateFlow(SurveyState())
    override val state: StateFlow<SurveyState> = _state.asStateFlow()

    private var job: Job? = null
    private val writeLock = Mutex()

    /** Emitters seen this session, per kind, for the live counters. */
    private val seen = ConcurrentHashMap<SurveyKind, MutableSet<String>>()

    /** BLE advertisements buffered between flushes: address -> strongest sighting. */
    private val bleBuffer = ConcurrentHashMap<String, SurveySighting>()

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            _state.update { it.copy(fix = location.toFix()) }
        }

        // Required overrides on older API levels (abstract before API 30).
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) {
            _state.update { it.copy(message = "Location was turned off; observations can't be tagged until it is back on.") }
        }
    }

    private val bleCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = buffer(result)
        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::buffer)

        @SuppressLint("MissingPermission")
        private fun buffer(result: ScanResult) {
            val address = result.device?.address ?: return
            val name = runCatching { result.scanRecord?.deviceName ?: result.device?.name }.getOrNull()
            val sighting = SurveySighting(SurveyKind.BLE, address, name, rssi = result.rssi)
            bleBuffer.merge(address, sighting) { old, new -> if (new.rssi > old.rssi) new.copy(name = new.name ?: old.name) else old.copy(name = old.name ?: new.name) }
        }
    }

    init {
        scope.launch { runCatching { dao.closeOrphanedSessions() } }
        scope.launch { ActiveTaskMonitor.stopAllSignal.collect { stop() } }
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override suspend fun start(options: SurveyOptions): String? {
        if (job?.isActive == true) return "A survey is already running"
        if (!options.wifi && !options.ble && !options.cell) return "Select at least one thing to log"
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) return "Precise location permission is required to geotag observations"
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) return "Turn on Location (GPS) to start a survey"

        val startedAt = System.currentTimeMillis()
        val sessionId = dao.insertSession(
            SurveySessionEntity(
                projectId = activeProjectStore.activeProjectId.value.orEmpty(),
                name = options.name.trim().ifBlank { "Survey" },
                startedAt = startedAt
            )
        )
        seen.clear()
        bleBuffer.clear()
        _state.value = SurveyState(
            isRunning = true, sessionId = sessionId, startedAt = startedAt,
            fix = runCatching { locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
                ?.takeIf { System.currentTimeMillis() - it.time < MAX_FIX_AGE_MS }?.toFix()
        )

        // Add the task before requesting updates so the foreground service is promoted with
        // the location type while the app is still in the foreground.
        ActiveTaskMonitor.addTask(SURVEY_TASK_NAME)
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2_000L, 0f, locationListener, Looper.getMainLooper())
        } catch (e: Exception) {
            ActiveTaskMonitor.removeTask(SURVEY_TASK_NAME)
            dao.endSession(sessionId, System.currentTimeMillis())
            _state.value = SurveyState()
            return "Couldn't start GPS updates: ${e.message ?: e.javaClass.simpleName}"
        }

        val bleStarted = options.ble && startBle()
        job = scope.launch {
            try {
                if (options.wifi) launch { wifiSource.scanResults().collect { readings -> record(sessionId, readings.map { it.toSighting() }) } }
                var tick = 0
                while (isActive) {
                    if (options.wifi && tick % WIFI_REQUEST_EVERY_TICKS == 0) wifiSource.requestScan()
                    if (options.cell) record(sessionId, readCells())
                    if (bleStarted) {
                        val batch = bleBuffer.values.toList()
                        bleBuffer.clear()
                        record(sessionId, batch)
                    }
                    val s = _state.value
                    ActiveTaskMonitor.updateStatus(
                        "$SURVEY_TASK_NAME: " + SurveyKind.entries.joinToString(" · ") { "${s.counts[it] ?: 0} ${it.label}" }
                    )
                    tick++
                    delay(TICK_MS)
                }
            } finally {
                withContext(kotlinx.coroutines.NonCancellable) {
                    runCatching { locationManager.removeUpdates(locationListener) }
                    if (bleStarted) stopBle()
                    runCatching { dao.endSession(sessionId, System.currentTimeMillis()) }
                    ActiveTaskMonitor.removeTask(SURVEY_TASK_NAME)
                    _state.update { it.copy(isRunning = false) }
                }
            }
        }
        return null
    }

    override fun stop() {
        job?.cancel()
    }

    /** Merges one batch of sightings into the session at the current position. */
    private suspend fun record(sessionId: Long, sightings: List<SurveySighting>) {
        if (sightings.isEmpty()) return
        val fix = _state.value.fix?.takeIf { System.currentTimeMillis() - it.timestamp < MAX_FIX_AGE_MS }
        if (fix == null) {
            _state.update { it.copy(skippedNoFix = it.skippedNoFix + 1) }
            return
        }
        writeLock.withLock {
            sightings.groupBy { it.kind }.forEach { (kind, group) ->
                val existing = group.map { it.identifier }.distinct().chunked(500)
                    .flatMap { dao.findObservations(sessionId, kind.name, it) }
                val byKey = existing.associateBy { SurveyMerger.key(kind, it.identifier) }
                val (inserts, updates) = SurveyMerger.merge(group, fix, byKey.mapValues { it.value.toDomain() })
                dao.upsertObservations(
                    inserts.map { it.toEntity(sessionId, 0) },
                    updates.map { it.toEntity(sessionId, byKey.getValue(SurveyMerger.key(kind, it.identifier)).id) }
                )
                seen.getOrPut(kind) { ConcurrentHashMap.newKeySet() }.addAll(group.map { it.identifier.uppercase() })
            }
        }
        _state.update { s -> s.copy(counts = SurveyKind.entries.associateWith { seen[it]?.size ?: 0 }) }
    }

    // ---- Sources -----------------------------------------------------------------------

    private fun com.fearmikey.rf_reapr.data.survey.WifiReading.toSighting() = SurveySighting(
        kind = SurveyKind.WIFI,
        identifier = bssid,
        name = ssid,
        capabilities = capabilities,
        frequencyMhz = frequencyMhz,
        channel = WifiSecurityAnalyzer.channel(frequencyMhz),
        rssi = rssi
    )

    @SuppressLint("MissingPermission")
    private fun startBle(): Boolean {
        val scanPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_SCAN
        else Manifest.permission.ACCESS_FINE_LOCATION
        if (!granted(scanPermission)) return false
        val adapter = (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter ?: return false
        if (!adapter.isEnabled) return false
        val scanner = adapter.bluetoothLeScanner ?: return false
        return runCatching {
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build(), bleCallback)
            true
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    private fun stopBle() {
        runCatching {
            (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.bluetoothLeScanner?.stopScan(bleCallback)
        }
    }

    @SuppressLint("MissingPermission")
    private fun readCells(): List<SurveySighting> {
        val telephony = appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return emptyList()
        val cells: List<CellInfo> = runCatching { telephony.allCellInfo }.getOrNull() ?: return emptyList()
        return cells.mapNotNull { runCatching { it.toSighting() }.getOrNull() }
    }

    /** Null for cells that don't report a full identity (typical for neighbours). */
    @Suppress("DEPRECATION")
    private fun CellInfo.toSighting(): SurveySighting? {
        fun valid(value: Int) = value != Int.MAX_VALUE && value >= 0
        fun valid(value: Long) = value != Long.MAX_VALUE && value >= 0
        fun plmn(mccString: String?, mncString: String?, mcc: Int, mnc: Int): String? = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> if (mccString != null && mncString != null) mccString + mncString else null
            valid(mcc) && valid(mnc) -> "%03d%02d".format(mcc, mnc)
            else -> null
        }
        fun sighting(rat: String, plmn: String?, area: Int, cell: Long, operator: CharSequence?, dbm: Int, channel: Int?): SurveySighting? {
            if (plmn == null || !valid(area) || !valid(cell)) return null
            return SurveySighting(
                kind = SurveyKind.CELL,
                identifier = "${plmn}_${area}_$cell",
                name = operator?.toString()?.takeIf { it.isNotBlank() },
                capabilities = rat,
                channel = channel?.takeIf { valid(it) },
                rssi = dbm
            )
        }
        return when (this) {
            is CellInfoLte -> cellIdentity.let { id ->
                val p = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) plmn(id.mccString, id.mncString, 0, 0) else plmn(null, null, id.mcc, id.mnc)
                val operator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.operatorAlphaLong else null
                sighting("LTE", p, id.tac, id.ci.toLong(), operator, cellSignalStrength.dbm, id.earfcn)
            }
            is CellInfoGsm -> cellIdentity.let { id ->
                val p = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) plmn(id.mccString, id.mncString, 0, 0) else plmn(null, null, id.mcc, id.mnc)
                val operator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.operatorAlphaLong else null
                sighting("GSM", p, id.lac, id.cid.toLong(), operator, cellSignalStrength.dbm, id.arfcn)
            }
            is CellInfoWcdma -> cellIdentity.let { id ->
                val p = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) plmn(id.mccString, id.mncString, 0, 0) else plmn(null, null, id.mcc, id.mnc)
                val operator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.operatorAlphaLong else null
                sighting("WCDMA", p, id.lac, id.cid.toLong(), operator, cellSignalStrength.dbm, id.uarfcn)
            }
            else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && this is CellInfoNr) {
                val id = cellIdentity as CellIdentityNr
                val strength = cellSignalStrength as CellSignalStrengthNr
                sighting("NR", plmn(id.mccString, id.mncString, 0, 0), id.tac, id.nci, id.operatorAlphaLong, strength.dbm, id.nrarfcn)
            } else null
        }
    }

    // ---- Sessions ----------------------------------------------------------------------

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSessions(): Flow<List<SurveySessionInfo>> =
        activeProjectStore.activeProjectId.flatMapLatest { projectId ->
            dao.observeSessions(projectId.orEmpty()).map { list -> list.map { it.toInfo() } }
        }

    override suspend fun deleteSession(id: Long) {
        if (_state.value.isRunning && _state.value.sessionId == id) stop()
        dao.deleteSession(id)
    }

    override suspend fun export(sessionId: Long, format: SurveyExportFormat): String? = withContext(Dispatchers.Default) {
        val session = dao.getSession(sessionId) ?: return@withContext null
        val observations = dao.getObservations(sessionId).map { it.toDomain() }
        when (format) {
            SurveyExportFormat.WIGLE_CSV -> SurveyExporter.wigleCsv(observations, appVersion, Build.MODEL ?: "Android")
            SurveyExportFormat.KML -> SurveyExporter.kml(session.name, observations)
            SurveyExportFormat.GPX -> SurveyExporter.gpx(session.name, observations, appVersion)
        }
    }

    private fun SurveySessionSummary.toInfo() = SurveySessionInfo(
        id = id, name = name, startedAt = startedAt, endedAt = endedAt,
        counts = mapOf(SurveyKind.WIFI to wifiCount, SurveyKind.BLE to bleCount, SurveyKind.CELL to cellCount)
    )

    private fun Location.toFix() = SurveyFix(
        latitude = latitude,
        longitude = longitude,
        altitude = if (hasAltitude()) altitude else null,
        accuracyM = if (hasAccuracy()) accuracy else null,
        // Wall-clock time of receipt: GPS time can lag or jump, and fixes are only compared with "now".
        timestamp = System.currentTimeMillis()
    )

    private fun SurveyObservationEntity.toDomain() = SurveyObservation(
        kind = SurveyKind.entries.firstOrNull { it.name == kind } ?: SurveyKind.WIFI,
        identifier = identifier, name = name, capabilities = capabilities, frequencyMhz = frequencyMhz, channel = channel,
        bestRssi = bestRssi, latitude = latitude, longitude = longitude, altitude = altitude, accuracyM = accuracyM,
        firstSeen = firstSeen, lastSeen = lastSeen, sightings = sightings
    )

    private fun SurveyObservation.toEntity(sessionId: Long, id: Long) = SurveyObservationEntity(
        id = id, sessionId = sessionId, kind = kind.name, identifier = identifier, name = name, capabilities = capabilities,
        frequencyMhz = frequencyMhz, channel = channel, bestRssi = bestRssi, latitude = latitude, longitude = longitude,
        altitude = altitude, accuracyM = accuracyM, firstSeen = firstSeen, lastSeen = lastSeen, sightings = sightings
    )

    private companion object {
        const val TICK_MS = 10_000L
        /** Android allows 4 Wi-Fi scan requests per 2 minutes; one every 30 s stays inside that. */
        const val WIFI_REQUEST_EVERY_TICKS = 3
        const val MAX_FIX_AGE_MS = 20_000L
    }
}
