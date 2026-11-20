package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.service.NatPmpResult
import com.fearmikey.rf_reapr.domain.service.UpnpFinding
import kotlinx.coroutines.flow.Flow

interface UpnpScannerRepository {
    /**
     * SSDP discovery, description fetch, read-only IGD queries (external IP and existing
     * port mappings) and a NAT-PMP probe of the gateway. Emits progressively richer
     * snapshots; the last one has [UpnpAuditResult.isComplete] = true.
     * Nothing is ever added to or removed from the router.
     */
    fun audit(listenSeconds: Int = 4): Flow<UpnpAuditResult>
}

data class UpnpAuditResult(
    val phase: String,
    val devices: List<UpnpDevice> = emptyList(),
    val natPmp: NatPmpResult? = null,
    val gatewayIp: String? = null,
    val findings: List<UpnpFinding> = emptyList(),
    val isComplete: Boolean = false,
    val error: String? = null
)

data class UpnpDevice(
    val ipAddress: String,
    val friendlyName: String,
    val manufacturer: String,
    val modelName: String,
    val services: List<UpnpService> = emptyList(),
    val location: String,
    val modelNumber: String? = null,
    val serialNumber: String? = null,
    val deviceType: String? = null,
    val server: String? = null,
    val presentationUrl: String? = null,
    /** Every ST/NT advertised in SSDP replies for this device. */
    val advertisedTypes: Set<String> = emptySet(),
    val externalIp: String? = null,
    val portMappings: List<UpnpPortMapping> = emptyList(),
    /** null = not an IGD; false = IGD refused the query (good); true = mappings listed. */
    val mappingsReadable: Boolean? = null,
    val descriptionError: String? = null
) {
    val isInternetGateway: Boolean
        get() = deviceType?.contains("InternetGatewayDevice", true) == true ||
            services.any { it.serviceType.contains("WANIPConnection", true) || it.serviceType.contains("WANPPPConnection", true) } ||
            advertisedTypes.any { it.contains("InternetGatewayDevice", true) || it.contains("WANIPConnection", true) }

    /** Short human label for the device class (MediaRenderer, InternetGatewayDevice...). */
    val deviceClass: String?
        get() = deviceType?.split(':')?.getOrNull(3)
}

data class UpnpService(
    val serviceType: String,
    val serviceId: String,
    val controlUrl: String,
    val eventSubUrl: String,
    val scpdUrl: String
) {
    val shortName: String get() = serviceType.split(':').getOrNull(3) ?: serviceType
}

data class UpnpPortMapping(
    val remoteHost: String,
    val externalPort: Int,
    val protocol: String,
    val internalPort: Int,
    val internalClient: String,
    val enabled: Boolean,
    val description: String,
    val leaseDurationSec: Long
)
