package com.fearmikey.rf_reapr.data.audit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.fearmikey.rf_reapr.domain.model.AUDIT_TASK_NAME
import com.fearmikey.rf_reapr.domain.model.AuditCheck
import com.fearmikey.rf_reapr.domain.model.AuditConfig
import com.fearmikey.rf_reapr.domain.model.AuditHost
import com.fearmikey.rf_reapr.domain.model.AuditRunState
import com.fearmikey.rf_reapr.domain.model.AuditStepState
import com.fearmikey.rf_reapr.domain.model.AuditStepStatus
import com.fearmikey.rf_reapr.domain.model.AuditTargets
import com.fearmikey.rf_reapr.domain.model.DeviceType
import com.fearmikey.rf_reapr.domain.model.FindingDraft
import com.fearmikey.rf_reapr.domain.model.NetworkNode
import com.fearmikey.rf_reapr.domain.model.OpenPort
import com.fearmikey.rf_reapr.domain.model.PortDepth
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.domain.model.SnmpResult
import com.fearmikey.rf_reapr.domain.model.WifiAccessPoint
import com.fearmikey.rf_reapr.domain.repository.AuditRunRepository
import com.fearmikey.rf_reapr.domain.repository.CaptivePortalRepository
import com.fearmikey.rf_reapr.domain.repository.DnsAuditorRepository
import com.fearmikey.rf_reapr.domain.repository.FindingRepository
import com.fearmikey.rf_reapr.domain.repository.LocalNetworkInfo
import com.fearmikey.rf_reapr.domain.repository.LogRepository
import com.fearmikey.rf_reapr.domain.repository.NetworkDiscoveryRepository
import com.fearmikey.rf_reapr.domain.repository.PortScannerRepository
import com.fearmikey.rf_reapr.domain.repository.ScanSessionRepository
import com.fearmikey.rf_reapr.domain.repository.SettingsRepository
import com.fearmikey.rf_reapr.domain.repository.SnmpRepository
import com.fearmikey.rf_reapr.domain.repository.TlsCipherScannerRepository
import com.fearmikey.rf_reapr.domain.repository.TlsCipherScannerRepository.TlsScanResult
import com.fearmikey.rf_reapr.domain.repository.UpnpAuditResult
import com.fearmikey.rf_reapr.domain.repository.UpnpScannerRepository
import com.fearmikey.rf_reapr.domain.repository.VulnerabilityRepository
import com.fearmikey.rf_reapr.domain.repository.WifiFingerprintRepository
import com.fearmikey.rf_reapr.domain.scanner.NetworkScanner
import com.fearmikey.rf_reapr.domain.service.ActiveEngagement
import com.fearmikey.rf_reapr.domain.service.ActiveTaskMonitor
import com.fearmikey.rf_reapr.domain.service.AuditPlanner
import com.fearmikey.rf_reapr.domain.service.AuditScoring
import com.fearmikey.rf_reapr.domain.service.CheckStatus
import com.fearmikey.rf_reapr.domain.service.DeviceIdentificationService
import com.fearmikey.rf_reapr.domain.service.FindingAdapters
import com.fearmikey.rf_reapr.domain.service.PortCatalog
import com.fearmikey.rf_reapr.domain.service.SnmpTools
import com.fearmikey.rf_reapr.domain.service.WifiSecurityAnalyzer
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs an Audit Mode pass: the selected checks in order, reusing each tool's repository
 * and finding adapter. Lives in AppContainer (process lifetime) so a running audit keeps
 * going when its screen is left, and is registered with [ActiveTaskMonitor] so the
 * foreground service keeps the process alive and "Stop all" cancels it.
 *
 * Findings are recorded into the shared store as each host/check completes, so a
 * cancelled audit keeps whatever it already found.
 */
class AuditEngine(
    context: Context,
    private val scope: CoroutineScope,
    private val settings: SettingsRepository,
    private val discovery: NetworkDiscoveryRepository,
    private val scanSessions: ScanSessionRepository,
    private val ports: PortScannerRepository,
    private val vulnerabilities: VulnerabilityRepository,
    private val tls: TlsCipherScannerRepository,
    private val snmp: SnmpRepository,
    private val upnp: UpnpScannerRepository,
    private val dnsAuditor: DnsAuditorRepository,
    private val wifi: WifiFingerprintRepository,
    private val captivePortal: CaptivePortalRepository,
    private val findings: FindingRepository,
    private val logs: LogRepository,
    /** Finished runs are kept so they can be compared with later ones. */
    private val auditRuns: AuditRunRepository
) {
    private val appContext = context.applicationContext
    private val gson = Gson()

    private val _state = MutableStateFlow(AuditRunState())
    val state: StateFlow<AuditRunState> = _state.asStateFlow()

    private var job: Job? = null
    val isRunning: Boolean get() = job?.isActive == true

    init {
        scope.launch { ActiveTaskMonitor.stopAllSignal.collect { cancel() } }
    }

    /** Starts a run; returns false if one is already in progress. */
    fun start(config: AuditConfig): Boolean {
        if (isRunning) return false
        job = scope.launch { run(config) }
        return true
    }

    fun cancel() {
        job?.cancel()
    }

    /** Forget the last run's results (no-op while running). */
    fun clear() {
        if (!isRunning) _state.value = AuditRunState()
    }

    // ---- Orchestration ----------------------------------------------------------------

    private data class StepOutcome(val status: AuditStepStatus, val summary: String)

    private fun done(summary: String) = StepOutcome(AuditStepStatus.DONE, summary)
    private fun skipped(reason: String) = StepOutcome(AuditStepStatus.SKIPPED, reason)
    private fun failed(reason: String) = StepOutcome(AuditStepStatus.FAILED, reason)

    private suspend fun run(config: AuditConfig) {
        val passive = settings.isPassiveMode.first()
        val plan = AuditPlanner.plan(config, passive)
        val networkInfo = runCatching { discovery.getLocalNetworkInfo() }.getOrNull()

        var hosts = emptyList<AuditHost>()
        var outOfScope = emptyList<String>()
        (config.targets as? AuditTargets.Manual)?.let { manual ->
            val (allowed, dropped) = scoped(manual.hosts, config)
            hosts = allowed.take(config.maxHosts).map { AuditHost(it) }
            outOfScope = dropped
        }

        _state.value = AuditRunState(
            config = config,
            steps = plan.map {
                AuditStepState(
                    check = it.check,
                    status = if (it.skipReason != null) AuditStepStatus.SKIPPED else AuditStepStatus.PENDING,
                    autoAdded = it.autoAdded,
                    summary = it.skipReason
                )
            },
            hosts = hosts,
            outOfScope = outOfScope,
            isRunning = true,
            startedAt = System.currentTimeMillis(),
            projectName = ActiveEngagement.current.value?.name,
            networkLabel = networkInfo?.let { "Gateway ${it.gatewayIp ?: "?"} · ${it.localIp}/${it.prefixLength}" }
        )

        ActiveTaskMonitor.addTask(TASK)
        try {
            for (step in plan.filter { it.skipReason == null }) {
                currentCoroutineContext().ensureActive()
                val check = step.check
                updateStep(check) { it.copy(status = AuditStepStatus.RUNNING, startedAt = System.currentTimeMillis()) }
                ActiveTaskMonitor.updateStatus("$TASK: ${check.label}")
                val outcome = try {
                    when (check) {
                        AuditCheck.WIFI -> runWifi()
                        AuditCheck.CAPTIVE_PORTAL -> runCaptivePortal()
                        AuditCheck.DNS -> runDns()
                        AuditCheck.UPNP -> runUpnp()
                        AuditCheck.DISCOVERY -> runDiscovery(config, networkInfo)
                        AuditCheck.PORTS -> runPorts(config)
                        AuditCheck.TLS -> runTls()
                        AuditCheck.SNMP -> runSnmp()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed(e.message ?: e.javaClass.simpleName)
                }
                updateStep(check) {
                    it.copy(status = outcome.status, summary = outcome.summary, progress = null, finishedAt = System.currentTimeMillis())
                }
            }
        } finally {
            _state.update { s ->
                s.copy(
                    isRunning = false,
                    finishedAt = System.currentTimeMillis(),
                    steps = s.steps.map { st ->
                        if (st.status == AuditStepStatus.RUNNING || st.status == AuditStepStatus.PENDING) {
                            st.copy(status = AuditStepStatus.CANCELLED, progress = null, summary = "Cancelled")
                        } else st
                    }
                )
            }
            ActiveTaskMonitor.removeTask(TASK)
            withContext(NonCancellable) {
                runCatching { saveLog(_state.value) }
                runCatching { auditRuns.save(_state.value) }
            }
        }
    }

    private fun updateStep(check: AuditCheck, transform: (AuditStepState) -> AuditStepState) {
        _state.update { s -> s.copy(steps = s.steps.map { if (it.check == check) transform(it) else it }) }
    }

    private fun progress(check: AuditCheck, fraction: Float, summary: String) =
        updateStep(check) { it.copy(progress = fraction.coerceIn(0f, 1f), summary = summary) }

    private fun updateHost(address: String, transform: (AuditHost) -> AuditHost) {
        _state.update { s -> s.copy(hosts = s.hosts.map { if (it.address == address) transform(it) else it }) }
    }

    /** Records into the shared findings store (tagged with the active project) and the run. */
    private suspend fun addFindings(check: AuditCheck, drafts: List<FindingDraft>) {
        if (drafts.isEmpty()) return
        findings.record(drafts)
        updateStep(check) { it.copy(findings = it.findings + drafts) }
    }

    private fun scoped(hosts: List<String>, config: AuditConfig): Pair<List<String>, List<String>> =
        if (config.respectScope) AuditPlanner.partitionByScope(hosts) { ActiveEngagement.check(it) } else hosts to emptyList()

    private fun plural(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"

    // ---- Steps ------------------------------------------------------------------------

    private suspend fun runWifi(): StepOutcome {
        var latest = emptyList<WifiAccessPoint>()
        // First emission = the system's cached results, second = after our scan request.
        withTimeoutOrNull(WIFI_TIMEOUT_MS) {
            wifi.startWifiScan().take(2).collect { if (it.isNotEmpty()) latest = it }
        }
        if (latest.isEmpty()) {
            return when {
                !wifi.status.value.wifiEnabled -> skipped("Wi-Fi is turned off")
                !hasWifiScanPermission() -> skipped("Location permission is needed to read Wi-Fi scan results")
                else -> done("No access points visible")
            }
        }
        addFindings(AuditCheck.WIFI, FindingAdapters.wifi(latest))
        val connected = latest.firstOrNull { it.isConnected }
        val rogues = latest.filter { it.isRogueSuspect }.map { it.ssid }.distinct().size
        connected?.let { ap ->
            val ssid = ap.ssid.ifBlank { "hidden" }
            _state.update { it.copy(networkLabel = listOfNotNull("\"$ssid\"", it.networkLabel).joinToString(" · ")) }
        }
        return done(buildString {
            append(plural(latest.size, "access point"))
            if (connected != null) {
                append(" · connected to \"${connected.ssid}\" (${WifiSecurityAnalyzer.analyze(connected.capabilities).label})")
            } else {
                append(" · not connected to Wi-Fi")
            }
            if (rogues > 0) append(" · ${plural(rogues, "evil-twin suspect")}")
        })
    }

    private fun hasWifiScanPermission(): Boolean {
        fun granted(p: String) = ContextCompat.checkSelfPermission(appContext, p) == PackageManager.PERMISSION_GRANTED
        return granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && granted(Manifest.permission.NEARBY_WIFI_DEVICES))
    }

    private suspend fun runCaptivePortal(): StepOutcome {
        val check = captivePortal.check()
        addFindings(AuditCheck.CAPTIVE_PORTAL, FindingAdapters.captivePortal(check.system?.transport, check.verdict))
        return done(check.verdict.title)
    }

    private suspend fun runDns(): StepOutcome {
        val result = dnsAuditor.auditDns()
        addFindings(AuditCheck.DNS, FindingAdapters.dnsAudit(result.checks))
        val issues = result.checks.count { it.status == CheckStatus.FAIL || it.status == CheckStatus.WARN }
        return done(
            "${result.overall.name} · ${plural(result.checks.size, "check")}, ${plural(issues, "issue")}" +
                (result.systemDnsServers.takeIf { it.isNotEmpty() }?.let { " · resolver ${it.joinToString()}" } ?: "")
        )
    }

    private suspend fun runUpnp(): StepOutcome {
        var last: UpnpAuditResult? = null
        upnp.audit().collect { snapshot ->
            last = snapshot
            updateStep(AuditCheck.UPNP) { it.copy(summary = snapshot.phase) }
        }
        val result = last ?: return failed("No response")
        result.error?.let { return failed(it) }
        addFindings(AuditCheck.UPNP, FindingAdapters.upnp(result.gatewayIp, result.findings))
        val mappings = result.devices.sumOf { it.portMappings.size }
        return done(
            "${plural(result.devices.size, "UPnP device")}, ${plural(mappings, "port mapping")} · NAT-PMP " +
                if (result.natPmp?.resultCode == 0) "enabled" else "not detected"
        )
    }

    private suspend fun runDiscovery(config: AuditConfig, networkInfo: LocalNetworkInfo?): StepOutcome {
        var found: List<NetworkNode>? = null
        var error: String? = null
        discovery.discoverDevices().collect { result ->
            when (result) {
                is NetworkScanner.ScanResult.Progress ->
                    progress(AuditCheck.DISCOVERY, result.progress, "${plural(result.foundData.size, "device")} so far")
                is NetworkScanner.ScanResult.Finished -> found = result.foundData
                is NetworkScanner.ScanResult.Error -> error = result.message
                else -> Unit
            }
        }
        val nodes = found ?: return failed(error ?: "Discovery returned no result")
        val gatewayIp = networkInfo?.gatewayIp
        val identified = nodes.map { node ->
            node.copy(
                deviceType = DeviceIdentificationService.identifyDevice(
                    ipAddress = node.ipAddress, macAddress = node.macAddress, hostname = node.hostname, gatewayIp = gatewayIp
                ),
                manufacturer = DeviceIdentificationService.getManufacturer(node.macAddress)
            )
        }
        // Replace (not merge) the Network Discovery map, same as a discovery run from that screen:
        // merging by IP would mix devices from a previously scanned network into this one.
        scanSessions.clearAllData()
        scanSessions.saveDiscoveredNodes(identified, "Local Network", gatewayIp)

        val candidates = identified.filter { it.ipAddress != networkInfo?.localIp }
        val (allowed, dropped) = scoped(candidates.map { it.ipAddress }, config)
        val capped = allowed.take(config.maxHosts)
        val byIp = candidates.associateBy { it.ipAddress }
        _state.update { s ->
            s.copy(
                hosts = capped.map { ip -> byIp.getValue(ip).let { AuditHost(ip, it.hostname, it.manufacturer) } },
                outOfScope = dropped
            )
        }
        return done(buildString {
            append("${plural(nodes.size, "device")} found")
            if (dropped.isNotEmpty()) append(" · ${dropped.size} out of scope")
            if (allowed.size > capped.size) append(" · limited to the first ${config.maxHosts}")
        })
    }

    private suspend fun runPorts(config: AuditConfig): StepOutcome {
        val targets = _state.value.hosts
        if (targets.isEmpty()) return skipped("No hosts to scan")
        val portList = if (config.portDepth == PortDepth.QUICK) NetworkNode.HIGH_IMPACT_PORTS else PortCatalog.COMMON_PORTS
        var totalOpen = 0
        var hostsWithPorts = 0
        targets.forEachIndexed { index, host ->
            progress(AuditCheck.PORTS, index.toFloat() / targets.size, "Scanning ${host.address} (${index + 1}/${targets.size})")
            ActiveTaskMonitor.updateStatus("$TASK: ports ${index + 1}/${targets.size}")
            var open = emptyList<OpenPort>()
            ports.scan(host.address, portList).collect { if (it is NetworkScanner.ScanResult.Finished) open = it.foundData }
            val audited = open.map { p ->
                p.copy(vulnerabilities = runCatching { vulnerabilities.lookupVulnerabilities(p.serviceName, p.banner) }.getOrDefault(emptyList()))
            }
            totalOpen += audited.size
            if (audited.isNotEmpty()) hostsWithPorts++
            updateHost(host.address) { it.copy(openPorts = audited) }
            persistPorts(host.address, audited)
            addFindings(AuditCheck.PORTS, FindingAdapters.openPorts(host.address, audited))
        }
        return done("${plural(totalOpen, "open port")} on $hostsWithPorts of ${plural(targets.size, "host")}")
    }

    /** Updates the network-map node (only for hosts already on the map). */
    private suspend fun persistPorts(address: String, open: List<OpenPort>) {
        val node = scanSessions.getNodeByIp(address) ?: return
        val risk = (open.flatMap { p -> p.vulnerabilities.map { it.severity } } + listOfNotNull(PortCatalog.exposureRisk(open.map { it.port })))
            .maxByOrNull { it.ordinal } ?: RiskLevel.LOW
        scanSessions.updateNodeDetails(
            node.copy(
                openPorts = open,
                riskLevel = risk,
                deviceType = if (node.deviceType == DeviceType.UNKNOWN) {
                    DeviceIdentificationService.identifyDevice(node.ipAddress, node.macAddress, node.hostname, open)
                } else node.deviceType
            )
        )
    }

    private suspend fun runTls(): StepOutcome {
        // IPv6 literals can't carry a port through the TLS scanner's parser; keep those on 443.
        val endpoints = AuditPlanner.tlsEndpoints(_state.value.hosts, MAX_TLS_ENDPOINTS)
            .filter { (host, port) -> !host.contains(':') || port == 443 }
        if (endpoints.isEmpty()) return done("No TLS services found by the port scan")
        val grades = mutableListOf<String>()
        var failures = 0
        endpoints.forEachIndexed { index, (host, port) ->
            progress(AuditCheck.TLS, index.toFloat() / endpoints.size, "Reviewing $host:$port (${index + 1}/${endpoints.size})")
            var last: TlsScanResult? = null
            withTimeoutOrNull(TLS_TIMEOUT_MS) {
                tls.scanCiphers(if (host.contains(':')) host else "$host:$port").collect { last = it }
            }
            val result = last
            if (result != null && result.isFinished && result.error == null) {
                addFindings(AuditCheck.TLS, FindingAdapters.tls(result))
                grades += "$host:$port ${result.grade ?: "?"}"
            } else {
                failures++
            }
        }
        return done(
            plural(endpoints.size, "endpoint") +
                (grades.takeIf { it.isNotEmpty() }?.let { " · ${it.joinToString()}" } ?: "") +
                (if (failures > 0) " · $failures unreachable/timed out" else "")
        )
    }

    private suspend fun runSnmp(): StepOutcome {
        val targets = _state.value.hosts
        if (targets.isEmpty()) return skipped("No hosts to probe")
        val probed = AtomicInteger(0)
        val responders = AtomicInteger(0)
        val semaphore = Semaphore(SNMP_PARALLELISM)
        coroutineScope {
            targets.map { host ->
                async {
                    semaphore.withPermit {
                        if (probeSnmp(host)) responders.incrementAndGet()
                        val n = probed.incrementAndGet()
                        progress(AuditCheck.SNMP, n.toFloat() / targets.size, "Probed $n/${targets.size} · ${responders.get()} responding")
                    }
                }
            }.awaitAll()
        }
        return done("${responders.get()} of ${plural(targets.size, "host")} accept a default community")
    }

    /** True if any default community worked; records findings for that host. */
    private suspend fun probeSnmp(host: AuditHost): Boolean {
        val address = host.address
        val primary = PRIMARY_COMMUNITIES.filter { snmp.probeCommunity(address, it, 1) }
        if (primary.isEmpty()) return false
        // Only hosts that answered get the longer default-community list.
        val working = primary + SnmpTools.DEFAULT_COMMUNITIES
            .filter { it !in PRIMARY_COMMUNITIES }
            .filter { snmp.probeCommunity(address, it, 1) }
        val result = snmp.queryDevice(address, working.first(), 1).getOrNull()
            ?: SnmpResult(targetIp = address, community = working.first())
        addFindings(AuditCheck.SNMP, FindingAdapters.snmp(address, SnmpTools.findings(result, working)))
        updateHost(address) { it.copy(snmpCommunity = working.first(), hostname = it.hostname ?: result.sysName) }
        scanSessions.getNodeByIp(address)?.let { node ->
            scanSessions.updateNodeDetails(
                node.copy(
                    snmpData = result,
                    deviceType = if (node.deviceType == DeviceType.UNKNOWN) DeviceType.NETWORK_DEVICE else node.deviceType
                )
            )
        }
        return true
    }

    private suspend fun saveLog(state: AuditRunState) {
        if (state.steps.none { it.status == AuditStepStatus.DONE || it.status == AuditStepStatus.FAILED }) return
        val score = AuditScoring.score(state.findings)
        logs.saveLog(
            type = "AUDIT",
            summary = "Audit Mode: grade ${score.grade}, ${plural(score.total, "finding")} across " +
                plural(state.steps.count { it.status == AuditStepStatus.DONE }, "check") +
                (state.networkLabel?.let { " ($it)" } ?: ""),
            detailJson = gson.toJson(
                mapOf(
                    "tool" to "Audit Mode",
                    "score" to score,
                    "steps" to state.steps.map { mapOf("check" to it.check.label, "status" to it.status, "summary" to it.summary) },
                    "findings" to state.findings,
                    "hosts" to state.hosts,
                    "outOfScope" to state.outOfScope
                )
            )
        )
    }

    private companion object {
        const val TASK = AUDIT_TASK_NAME
        const val WIFI_TIMEOUT_MS = 10_000L
        const val TLS_TIMEOUT_MS = 90_000L
        const val MAX_TLS_ENDPOINTS = 12
        const val SNMP_PARALLELISM = 8
        val PRIMARY_COMMUNITIES = listOf("public", "private")
    }
}
