package com.fearmikey.rf_reapr.ui.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.model.NetworkNode
import com.fearmikey.rf_reapr.domain.model.WolTarget
import com.fearmikey.rf_reapr.domain.repository.NetworkDiscoveryRepository
import com.fearmikey.rf_reapr.domain.repository.ScanSessionRepository
import com.fearmikey.rf_reapr.domain.repository.WolRepository
import com.fearmikey.rf_reapr.domain.service.DeviceIdentificationService
import com.fearmikey.rf_reapr.domain.service.HostValidator
import com.fearmikey.rf_reapr.domain.service.MacAddressAnalyzer
import com.fearmikey.rf_reapr.domain.service.SubnetCalculator
import com.fearmikey.rf_reapr.domain.service.WolPacketBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class WolForm(
    val name: String = "",
    val mac: String = "",
    val broadcast: String = LIMITED_BROADCAST,
    val port: Int = 9,
    val alsoPort7: Boolean = true,
    val repeat: Int = 3,
    val secureOn: String = "",
    val verifyIp: String = ""
) {
    companion object {
        const val LIMITED_BROADCAST = "255.255.255.255"
    }
}

sealed interface WolStatus {
    data object Idle : WolStatus
    data class Sent(val packets: Int, val destination: String) : WolStatus
    data class Verifying(val ip: String, val secondsElapsed: Int, val timeoutSeconds: Int) : WolStatus
    data class Awake(val ip: String, val afterSeconds: Int) : WolStatus
    data class NotConfirmed(val ip: String) : WolStatus
    data class Error(val message: String) : WolStatus
}

class WolViewModel(
    private val repository: WolRepository,
    discoveryRepository: NetworkDiscoveryRepository? = null,
    scanSessionRepository: ScanSessionRepository? = null
) : ViewModel() {

    private val _form = MutableStateFlow(WolForm())
    val form: StateFlow<WolForm> = _form.asStateFlow()

    private val _status = MutableStateFlow<WolStatus>(WolStatus.Idle)
    val status: StateFlow<WolStatus> = _status.asStateFlow()

    /** Directed broadcast of the current subnet (often more reliable than 255.255.255.255). */
    private val _subnetBroadcast = MutableStateFlow<String?>(null)
    val subnetBroadcast: StateFlow<String?> = _subnetBroadcast.asStateFlow()

    val savedTargets: StateFlow<List<WolTarget>> = repository.savedTargets

    /** Devices from the last Network Discovery that have a vendor-assigned MAC. */
    val discoveredDevices: StateFlow<List<NetworkNode>> =
        (scanSessionRepository?.getAllPersistedNodes() ?: emptyFlow<List<NetworkNode>>())
            .map { nodes ->
                nodes.filter { node ->
                    node.macAddress?.let { MacAddressAnalyzer.parse(it) }?.let { !it.isMulticast } == true
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var verifyJob: Job? = null

    init {
        discoveryRepository?.let { repo ->
            viewModelScope.launch {
                runCatching { repo.getLocalNetworkInfo() }.getOrNull()?.let { info ->
                    val ip = SubnetCalculator.parseIpv4(info.localIp) ?: return@let
                    val broadcast = SubnetCalculator.format(SubnetCalculator.calculate(ip, info.prefixLength).broadcast)
                    _subnetBroadcast.value = broadcast
                    // Prefer the directed broadcast unless the user already changed it.
                    _form.update { if (it.broadcast == WolForm.LIMITED_BROADCAST) it.copy(broadcast = broadcast) else it }
                }
            }
        }
    }

    fun updateForm(transform: (WolForm) -> WolForm) {
        _form.update(transform)
    }

    fun vendorFor(mac: String): String? = MacAddressAnalyzer.parse(mac)?.let { DeviceIdentificationService.getManufacturer(it.normalized) }

    fun loadTarget(target: WolTarget) {
        _form.value = WolForm(
            name = target.name,
            mac = target.mac,
            broadcast = target.broadcast,
            port = target.port,
            verifyIp = target.ip.orEmpty()
        )
        _status.value = WolStatus.Idle
    }

    fun loadDiscovered(node: NetworkNode) {
        _form.update {
            it.copy(name = node.hostname ?: node.manufacturer ?: node.ipAddress, mac = node.macAddress.orEmpty(), verifyIp = node.ipAddress)
        }
        _status.value = WolStatus.Idle
    }

    fun saveCurrent() {
        val f = _form.value
        val mac = MacAddressAnalyzer.parse(f.mac) ?: run {
            _status.value = WolStatus.Error("Enter a valid MAC address before saving")
            return
        }
        viewModelScope.launch {
            val existing = savedTargets.value.firstOrNull { it.mac.equals(mac.colonFormat, true) }
            repository.saveTarget(
                WolTarget(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    name = f.name.ifBlank { vendorFor(f.mac) ?: mac.colonFormat },
                    mac = mac.colonFormat,
                    broadcast = f.broadcast,
                    port = f.port,
                    ip = HostValidator.normalize(f.verifyIp)
                )
            )
        }
    }

    fun deleteTarget(target: WolTarget) {
        viewModelScope.launch { repository.deleteTarget(target.id) }
    }

    fun wake(target: WolTarget) {
        loadTarget(target)
        send()
    }

    fun send() {
        val f = _form.value
        val mac = MacAddressAnalyzer.parse(f.mac)
        if (mac == null) {
            _status.value = WolStatus.Error("Invalid MAC address (needs 12 hex digits, e.g. 00:1A:2B:3C:4D:5E)")
            return
        }
        if (SubnetCalculator.parseIpv4(f.broadcast) == null) {
            _status.value = WolStatus.Error("Broadcast address must be an IPv4 address")
            return
        }
        val secureOn = WolPacketBuilder.parseSecureOn(f.secureOn).getOrElse {
            _status.value = WolStatus.Error("SecureOn: ${it.message}")
            return
        }
        val verifyIp = f.verifyIp.takeIf { it.isNotBlank() }?.let { HostValidator.normalize(it) }
        if (f.verifyIp.isNotBlank() && verifyIp == null) {
            _status.value = WolStatus.Error("Verification IP is not a valid address")
            return
        }

        val ports = buildList {
            add(f.port)
            if (f.alsoPort7) {
                add(7); add(9)
            }
        }.distinct()

        verifyJob?.cancel()
        viewModelScope.launch {
            repository.sendMagicPacket(WolPacketBuilder.build(mac, secureOn), f.broadcast, ports, f.repeat)
                .onSuccess { count ->
                    _status.value = WolStatus.Sent(count, "${f.broadcast} : ${ports.joinToString("/")}")
                    verifyIp?.let { startVerification(it) }
                }
                .onFailure { _status.value = WolStatus.Error(it.message ?: "Failed to send packet") }
        }
    }

    fun cancelVerification() {
        verifyJob?.cancel()
        (_status.value as? WolStatus.Verifying)?.let { _status.value = WolStatus.Idle }
    }

    /** Polls the device until it answers or [VERIFY_TIMEOUT_SECONDS] elapse. */
    private fun startVerification(ip: String) {
        verifyJob = viewModelScope.launch {
            val start = System.currentTimeMillis()
            while (true) {
                val elapsed = ((System.currentTimeMillis() - start) / 1000).toInt()
                if (elapsed >= VERIFY_TIMEOUT_SECONDS) {
                    _status.value = WolStatus.NotConfirmed(ip)
                    return@launch
                }
                _status.value = WolStatus.Verifying(ip, elapsed, VERIFY_TIMEOUT_SECONDS)
                if (repository.isHostUp(ip)) {
                    _status.value = WolStatus.Awake(ip, ((System.currentTimeMillis() - start) / 1000).toInt())
                    return@launch
                }
                delay(VERIFY_INTERVAL_MS)
            }
        }
    }

    companion object {
        const val VERIFY_TIMEOUT_SECONDS = 120
        private const val VERIFY_INTERVAL_MS = 4_000L
    }
}
