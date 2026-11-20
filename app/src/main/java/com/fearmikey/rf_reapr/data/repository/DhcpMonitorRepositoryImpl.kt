package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.domain.model.MonitorStats
import com.fearmikey.rf_reapr.domain.model.MonitoredDevice
import com.fearmikey.rf_reapr.domain.model.NetworkEvent
import com.fearmikey.rf_reapr.domain.model.NetworkEventType
import com.fearmikey.rf_reapr.domain.model.NetworkNode
import com.fearmikey.rf_reapr.domain.repository.DhcpMonitorRepository
import com.fearmikey.rf_reapr.domain.repository.DhcpMonitorRepository.MonitoringStatus
import com.fearmikey.rf_reapr.domain.repository.NetworkDiscoveryRepository
import com.fearmikey.rf_reapr.domain.scanner.NetworkScanner
import com.fearmikey.rf_reapr.domain.service.DeviceIdentificationService
import com.fearmikey.rf_reapr.domain.service.MacAddressAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/**
 * Periodically sweeps the subnet and diffs each sweep against the known device set to
 * detect joins, departures, returns, and MAC changes on a known IP.
 *
 * The first sweep (after start or [resetBaseline]) establishes the baseline; devices seen
 * later are flagged as new. Devices must be missed on [MISSES_BEFORE_OFFLINE] consecutive
 * sweeps before they're marked offline, since TCP/ICMP liveness probes are occasionally
 * dropped.
 */
class DhcpMonitorRepositoryImpl(
    private val discoveryRepository: NetworkDiscoveryRepository
) : DhcpMonitorRepository {

    private val lock = Any()

    private val _activeDevices = MutableStateFlow<List<MonitoredDevice>>(emptyList())
    override val activeDevices: StateFlow<List<MonitoredDevice>> = _activeDevices.asStateFlow()

    private val _events = MutableStateFlow<List<NetworkEvent>>(emptyList())
    override val events: StateFlow<List<NetworkEvent>> = _events.asStateFlow()

    private val _stats = MutableStateFlow(MonitorStats())
    override val stats: StateFlow<MonitorStats> = _stats.asStateFlow()

    private val missedSweeps = mutableMapOf<String, Int>()
    private var hasBaseline = false
    @Volatile private var monitorJob: Job? = null

    override fun startMonitoring(intervalMs: Long): Flow<MonitoringStatus> = channelFlow {
        monitorJob = coroutineContext[Job]
        while (isActive) {
            val sweepStart = System.currentTimeMillis()
            send(MonitoringStatus.Scanning(0f))
            var found: List<NetworkNode>? = null
            discoveryRepository.discoverDevices().collect { result ->
                when (result) {
                    is NetworkScanner.ScanResult.Progress -> send(MonitoringStatus.Scanning(result.progress))
                    is NetworkScanner.ScanResult.Finished -> found = result.foundData
                    is NetworkScanner.ScanResult.Error -> send(MonitoringStatus.Error(result.message))
                    else -> Unit
                }
            }
            found?.let { nodes ->
                processSweep(nodes, sweepStart).forEach { send(it) }
            }
            send(MonitoringStatus.Waiting(System.currentTimeMillis() + intervalMs))
            delay(intervalMs)
        }
    }.flowOn(Dispatchers.Default)

    override fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }

    override fun acknowledgeNewDevices() {
        synchronized(lock) {
            _activeDevices.value = _activeDevices.value.map { it.copy(isNew = false) }
        }
    }

    override fun resetBaseline() {
        synchronized(lock) {
            hasBaseline = false
            missedSweeps.clear()
            _activeDevices.value = emptyList()
            _events.value = emptyList()
            _stats.value = MonitorStats()
        }
    }

    /** Merges one sweep into the device set; returns statuses worth alerting on. */
    private fun processSweep(found: List<NetworkNode>, sweepStart: Long): List<MonitoringStatus> = synchronized(lock) {
        val now = System.currentTimeMillis()
        val current = _activeDevices.value.associateBy { it.ipAddress }.toMutableMap()
        val foundIps = found.mapTo(HashSet()) { it.ipAddress }
        val newEvents = mutableListOf<NetworkEvent>()
        val alerts = mutableListOf<MonitoringStatus>()
        val isBaselineSweep = !hasBaseline

        found.forEach { node ->
            missedSweeps.remove(node.ipAddress)
            val mac = node.macAddress
            val vendor = DeviceIdentificationService.getManufacturer(mac)
            val randomized = mac?.let { MacAddressAnalyzer.parse(it)?.isRandomized } ?: false
            val existing = current[node.ipAddress]

            if (existing == null) {
                val device = MonitoredDevice(
                    ipAddress = node.ipAddress,
                    hostname = node.hostname,
                    macAddress = mac,
                    isNew = !isBaselineSweep,
                    lastSeen = now,
                    firstSeen = now,
                    manufacturer = vendor,
                    isOnline = true,
                    isRandomizedMac = randomized
                )
                current[node.ipAddress] = device
                if (!isBaselineSweep) {
                    newEvents += NetworkEvent(NetworkEventType.JOINED, device.ipAddress, "New device: ${device.displayName}", deviceDetail(device), now)
                    alerts += MonitoringStatus.DeviceDetected(device, "joined the network")
                }
            } else {
                val macChanged = existing.macAddress != null && mac != null && !existing.macAddress.equals(mac, ignoreCase = true)
                val updated = existing.copy(
                    hostname = node.hostname ?: existing.hostname,
                    macAddress = mac ?: existing.macAddress,
                    manufacturer = vendor ?: existing.manufacturer,
                    isRandomizedMac = if (mac != null) randomized else existing.isRandomizedMac,
                    lastSeen = now,
                    isOnline = true,
                    isNew = existing.isNew || macChanged
                )
                current[node.ipAddress] = updated
                if (!existing.isOnline) {
                    newEvents += NetworkEvent(NetworkEventType.RETURNED, updated.ipAddress, "Back online: ${updated.displayName}", updated.ipAddress, now)
                }
                if (macChanged) {
                    val detail = "${updated.ipAddress}: ${existing.macAddress} → $mac"
                    newEvents += NetworkEvent(NetworkEventType.MAC_CHANGED, updated.ipAddress, "MAC changed on ${updated.ipAddress}", detail, now)
                    alerts += MonitoringStatus.DeviceDetected(updated, "MAC changed (${existing.macAddress} → $mac)")
                }
            }
        }

        // Devices that didn't answer this sweep.
        current.values.filter { it.isOnline && it.ipAddress !in foundIps }.forEach { device ->
            val misses = (missedSweeps[device.ipAddress] ?: 0) + 1
            missedSweeps[device.ipAddress] = misses
            if (misses >= MISSES_BEFORE_OFFLINE) {
                val offline = device.copy(isOnline = false)
                current[device.ipAddress] = offline
                newEvents += NetworkEvent(NetworkEventType.LEFT, offline.ipAddress, "Went offline: ${offline.displayName}", offline.ipAddress, now)
                alerts += MonitoringStatus.DeviceLeft(offline)
            }
        }

        if (isBaselineSweep) {
            newEvents += NetworkEvent(
                NetworkEventType.BASELINE,
                null,
                "Baseline established",
                "${found.size} devices recorded as known",
                now
            )
            hasBaseline = true
        }

        _activeDevices.value = current.values.sortedBy { ipSortKey(it.ipAddress) }
        _events.value = (newEvents.asReversed() + _events.value).take(MAX_EVENTS)
        _stats.value = _stats.value.copy(
            sweepCount = _stats.value.sweepCount + 1,
            lastSweepAt = now,
            lastSweepDurationMs = now - sweepStart
        )
        alerts
    }

    private fun deviceDetail(device: MonitoredDevice): String = buildString {
        append(device.ipAddress)
        device.macAddress?.let { append(" · $it") }
        device.manufacturer?.let { append(" · $it") }
        if (device.isRandomizedMac) append(" · randomized MAC")
    }

    private fun ipSortKey(ip: String): Long =
        ip.split(".").fold(0L) { acc, part -> (acc shl 8) + (part.toLongOrNull() ?: 0L) }

    companion object {
        const val MISSES_BEFORE_OFFLINE = 2
        private const val MAX_EVENTS = 200
    }
}
