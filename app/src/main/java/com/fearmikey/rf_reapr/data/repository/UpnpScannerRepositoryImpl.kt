package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.domain.repository.NetworkDiscoveryRepository
import com.fearmikey.rf_reapr.domain.repository.UpnpAuditResult
import com.fearmikey.rf_reapr.domain.repository.UpnpDevice
import com.fearmikey.rf_reapr.domain.repository.UpnpPortMapping
import com.fearmikey.rf_reapr.domain.repository.UpnpScannerRepository
import com.fearmikey.rf_reapr.domain.service.NatPmpResult
import com.fearmikey.rf_reapr.domain.service.SsdpResponse
import com.fearmikey.rf_reapr.domain.service.UpnpTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.util.concurrent.TimeUnit

class UpnpScannerRepositoryImpl(
    okHttpClient: OkHttpClient,
    private val discoveryRepository: NetworkDiscoveryRepository
) : UpnpScannerRepository {

    private val http = okHttpClient.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    override fun audit(listenSeconds: Int): Flow<UpnpAuditResult> = flow {
        val gatewayIp = runCatching { discoveryRepository.getLocalNetworkInfo().gatewayIp }.getOrNull()
        emit(UpnpAuditResult(phase = "Sending SSDP discovery…", gatewayIp = gatewayIp))

        val replies = try {
            runInterruptible { ssdpDiscover(listenSeconds) }
        } catch (e: Exception) {
            emit(UpnpAuditResult(phase = "Failed", gatewayIp = gatewayIp, isComplete = true, error = "SSDP discovery failed: ${e.message}"))
            return@flow
        }

        // Group replies per device (by UUID, falling back to LOCATION).
        val grouped = replies.groupBy { (_, r) -> r.uuid ?: r.location }
        var devices = grouped.values.map { group ->
            val (ip, first) = group.first()
            UpnpDevice(
                ipAddress = ip,
                friendlyName = ip,
                manufacturer = "Unknown",
                modelName = "Unknown",
                location = first.location,
                server = group.firstNotNullOfOrNull { it.second.server },
                advertisedTypes = group.mapNotNull { it.second.st }.toSet()
            )
        }.sortedWith(compareBy({ it.ipAddress != gatewayIp }, { it.ipAddress }))
        emit(UpnpAuditResult(phase = "Reading ${devices.size} device descriptions…", devices = devices, gatewayIp = gatewayIp))

        devices = coroutineScope {
            val limit = Semaphore(4)
            devices.map { d -> async { limit.withPermit { enrich(d) } } }.awaitAll()
        }
        emit(UpnpAuditResult(phase = "Probing NAT-PMP…", devices = devices, gatewayIp = gatewayIp))

        val natPmp = gatewayIp?.let { runCatching { runInterruptible { natPmpProbe(it) } }.getOrNull() }
        emit(
            UpnpAuditResult(
                phase = "Complete",
                devices = devices,
                natPmp = natPmp,
                gatewayIp = gatewayIp,
                findings = UpnpTools.findings(devices, natPmp, gatewayIp),
                isComplete = true
            )
        )
    }.flowOn(Dispatchers.IO)

    /** Returns (senderIp, reply) pairs. Sends M-SEARCH twice since UDP multicast is lossy. */
    private fun ssdpDiscover(listenSeconds: Int): List<Pair<String, SsdpResponse>> {
        val out = mutableListOf<Pair<String, SsdpResponse>>()
        val seen = HashSet<String>()
        DatagramSocket().use { socket ->
            socket.soTimeout = 500
            val group = InetAddress.getByName(UpnpTools.SSDP_ADDRESS)
            listOf("ssdp:all", "urn:schemas-upnp-org:device:InternetGatewayDevice:1").forEach { st ->
                val bytes = UpnpTools.mSearch(st).toByteArray()
                repeat(2) { socket.send(DatagramPacket(bytes, bytes.size, group, UpnpTools.SSDP_PORT)) }
            }
            val buffer = ByteArray(8192)
            val deadline = System.currentTimeMillis() + listenSeconds * 1000L
            while (System.currentTimeMillis() < deadline && !Thread.currentThread().isInterrupted) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val reply = UpnpTools.parseSsdpResponse(text) ?: continue
                    val sender = packet.address.hostAddress ?: continue
                    if (seen.add("$sender|${reply.usn}|${reply.st}|${reply.location}")) out += sender to reply
                } catch (_: SocketTimeoutException) {
                    // keep listening until the deadline
                }
            }
        }
        return out
    }

    private fun enrich(device: UpnpDevice): UpnpDevice {
        // Only fetch descriptions from the device that answered (a reply could otherwise point
        // the app at an arbitrary internet URL).
        val locationHost = runCatching { URI(device.location).host }.getOrNull()
        if (locationHost == null || locationHost.trim('[', ']') != device.ipAddress) {
            return device.copy(descriptionError = "LOCATION points to a different host ($locationHost); not fetched")
        }
        val xml = try {
            get(device.location)
        } catch (e: Exception) {
            return device.copy(descriptionError = "Description unavailable: ${e.message ?: e.javaClass.simpleName}")
        }
        val desc = UpnpTools.parseDescription(xml, device.location)
        var enriched = device.copy(
            friendlyName = desc.friendlyName ?: device.friendlyName,
            manufacturer = desc.manufacturer ?: device.manufacturer,
            modelName = desc.modelName ?: device.modelName,
            modelNumber = desc.modelNumber,
            serialNumber = desc.serialNumber,
            deviceType = desc.deviceType,
            presentationUrl = desc.presentationUrl,
            services = desc.services
        )
        val wan = UpnpTools.wanConnectionServices(desc.services).firstOrNull { it.controlUrl.isNotBlank() }
        if (wan != null) {
            val externalIp = runCatching {
                UpnpTools.parseExternalIp(soap(wan.controlUrl, wan.serviceType, "GetExternalIPAddress"))
            }.getOrNull()
            val (mappings, readable) = listPortMappings(wan.controlUrl, wan.serviceType)
            enriched = enriched.copy(externalIp = externalIp, portMappings = mappings, mappingsReadable = readable)
        }
        return enriched
    }

    /** Reads existing mappings with GetGenericPortMappingEntry (read-only). */
    private fun listPortMappings(controlUrl: String, serviceType: String): Pair<List<UpnpPortMapping>, Boolean> {
        val mappings = mutableListOf<UpnpPortMapping>()
        for (index in 0 until MAX_MAPPINGS) {
            val response = try {
                soap(controlUrl, serviceType, "GetGenericPortMappingEntry", mapOf("NewPortMappingIndex" to index.toString()))
            } catch (_: Exception) {
                break
            }
            val mapping = UpnpTools.parsePortMapping(response) ?: break
            mappings += mapping
        }
        // An IGD that answered GetExternalIPAddress but listed nothing may just have no
        // mappings; either way the control endpoint is reachable.
        return mappings to true
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
            val body = response.body ?: throw IllegalStateException("empty body")
            return readCapped(body)
        }
    }

    private fun soap(controlUrl: String, serviceType: String, action: String, args: Map<String, String> = emptyMap()): String {
        val body = UpnpTools.soapEnvelope(serviceType, action, args)
            .toRequestBody("text/xml; charset=\"utf-8\"".toMediaType())
        val request = Request.Builder()
            .url(controlUrl)
            .post(body)
            .header("SOAPAction", "\"$serviceType#$action\"")
            .header("User-Agent", USER_AGENT)
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.let(::readCapped).orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("UPnP error ${UpnpTools.parseSoapErrorCode(text) ?: response.code}")
            }
            return text
        }
    }

    /** Reads at most [MAX_BODY] bytes so a hostile device can't exhaust memory. */
    private fun readCapped(body: okhttp3.ResponseBody): String {
        val source = body.source()
        source.request(MAX_BODY)
        return source.buffer.readUtf8(minOf(source.buffer.size, MAX_BODY))
    }

    private fun natPmpProbe(gatewayIp: String): NatPmpResult? {
        DatagramSocket().use { socket ->
            socket.soTimeout = 750
            val address = InetAddress.getByName(gatewayIp)
            val buffer = ByteArray(16)
            // RFC 6886 retransmits with doubling timeouts; three tries is enough on a LAN.
            repeat(3) {
                socket.send(DatagramPacket(UpnpTools.natPmpRequest, UpnpTools.natPmpRequest.size, address, UpnpTools.NAT_PMP_PORT))
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    return UpnpTools.parseNatPmpResponse(packet.data, packet.length)
                } catch (_: SocketTimeoutException) {
                    // retry
                }
            }
        }
        return null
    }

    private companion object {
        const val MAX_MAPPINGS = 128
        const val MAX_BODY = 256L * 1024
        const val USER_AGENT = "Android UPnP/1.1 RF-REAPR/1.0"
    }
}
