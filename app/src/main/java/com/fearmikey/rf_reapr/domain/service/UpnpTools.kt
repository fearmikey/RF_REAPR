package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.domain.repository.UpnpDevice
import com.fearmikey.rf_reapr.domain.repository.UpnpPortMapping
import com.fearmikey.rf_reapr.domain.repository.UpnpService
import java.net.URI

/** One SSDP M-SEARCH reply. */
data class SsdpResponse(
    val location: String,
    val server: String?,
    val st: String?,
    val usn: String?
) {
    /** Device UUID from the USN (`uuid:xxxx::urn:...`), used to group replies per device. */
    val uuid: String? get() = usn?.substringAfter("uuid:", "")?.substringBefore("::")?.takeIf { it.isNotBlank() }
}

data class UpnpDescription(
    val friendlyName: String?,
    val manufacturer: String?,
    val modelName: String?,
    val modelNumber: String?,
    val serialNumber: String?,
    val deviceType: String?,
    val presentationUrl: String?,
    val services: List<UpnpService>
)

data class NatPmpResult(val externalIp: String?, val resultCode: Int, val epochSeconds: Long)

data class UpnpFinding(val severity: RiskLevel, val title: String, val detail: String)

/**
 * Pure parsing helpers for the UPnP / NAT-PMP auditor. Regex-based rather than a full XML
 * parser: description documents are small and flat, and this avoids XXE/DTD processing on
 * untrusted XML from LAN devices.
 */
object UpnpTools {

    const val SSDP_ADDRESS = "239.255.255.250"
    const val SSDP_PORT = 1900
    const val NAT_PMP_PORT = 5351

    fun mSearch(st: String = "ssdp:all", mx: Int = 2): String =
        "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: $mx\r\n" +
            "ST: $st\r\n" +
            "USER-AGENT: Android UPnP/1.1 RF-REAPR/1.0\r\n\r\n"

    fun parseSsdpResponse(text: String): SsdpResponse? {
        val headers = text.lineSequence()
            .drop(1)
            .mapNotNull { line ->
                val idx = line.indexOf(':')
                if (idx <= 0) null else line.substring(0, idx).trim().uppercase() to line.substring(idx + 1).trim()
            }
            .toMap()
        val location = headers["LOCATION"]?.takeIf { it.startsWith("http", ignoreCase = true) } ?: return null
        return SsdpResponse(location, headers["SERVER"], headers["ST"] ?: headers["NT"], headers["USN"])
    }

    private fun tag(xml: String, name: String): String? =
        Regex("<(?:\\w+:)?$name(?:\\s[^>]*)?>(.*?)</(?:\\w+:)?$name>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .find(xml)?.groupValues?.get(1)?.trim()?.let(::unescape)?.takeIf { it.isNotEmpty() }

    private fun blocks(xml: String, name: String): List<String> =
        Regex("<(?:\\w+:)?$name(?:\\s[^>]*)?>(.*?)</(?:\\w+:)?$name>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .findAll(xml).map { it.groupValues[1] }.toList()

    private fun unescape(s: String) = s
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    /** Resolves a (possibly relative) URL in a description against URLBase or the LOCATION URL. */
    fun resolveUrl(base: String, path: String?): String? {
        if (path.isNullOrBlank()) return null
        return runCatching { URI(base).resolve(path.trim()).toString() }.getOrNull()
    }

    fun parseDescription(xml: String, location: String): UpnpDescription {
        val base = tag(xml, "URLBase") ?: location
        // The root device's own fields come before its first nested deviceList.
        val rootDevice = blocks(xml, "device").firstOrNull() ?: xml
        val rootHead = rootDevice.substringBefore("<deviceList").substringBefore("<serviceList")
        val services = blocks(xml, "service").mapNotNull { s ->
            val type = tag(s, "serviceType") ?: return@mapNotNull null
            UpnpService(
                serviceType = type,
                serviceId = tag(s, "serviceId").orEmpty(),
                controlUrl = resolveUrl(base, tag(s, "controlURL")).orEmpty(),
                eventSubUrl = resolveUrl(base, tag(s, "eventSubURL")).orEmpty(),
                scpdUrl = resolveUrl(base, tag(s, "SCPDURL")).orEmpty()
            )
        }
        return UpnpDescription(
            friendlyName = tag(rootHead, "friendlyName"),
            manufacturer = tag(rootHead, "manufacturer"),
            modelName = tag(rootHead, "modelName"),
            modelNumber = tag(rootHead, "modelNumber"),
            serialNumber = tag(rootHead, "serialNumber"),
            deviceType = tag(rootHead, "deviceType"),
            presentationUrl = resolveUrl(base, tag(rootHead, "presentationURL")),
            services = services
        )
    }

    /** WANIPConnection / WANPPPConnection services, which expose port-mapping control. */
    fun wanConnectionServices(services: List<UpnpService>): List<UpnpService> =
        services.filter { it.serviceType.contains("WANIPConnection", true) || it.serviceType.contains("WANPPPConnection", true) }

    fun soapEnvelope(serviceType: String, action: String, args: Map<String, String> = emptyMap()): String {
        val argXml = args.entries.joinToString("") { "<${it.key}>${it.value}</${it.key}>" }
        return "<?xml version=\"1.0\"?>\r\n" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
            "<s:Body><u:$action xmlns:u=\"$serviceType\">$argXml</u:$action></s:Body></s:Envelope>"
    }

    fun parseExternalIp(xml: String): String? = tag(xml, "NewExternalIPAddress")

    fun parsePortMapping(xml: String): UpnpPortMapping? {
        val externalPort = tag(xml, "NewExternalPort")?.toIntOrNull() ?: return null
        return UpnpPortMapping(
            remoteHost = tag(xml, "NewRemoteHost").orEmpty(),
            externalPort = externalPort,
            protocol = tag(xml, "NewProtocol").orEmpty().uppercase(),
            internalPort = tag(xml, "NewInternalPort")?.toIntOrNull() ?: 0,
            internalClient = tag(xml, "NewInternalClient").orEmpty(),
            enabled = tag(xml, "NewEnabled").let { it == null || it == "1" || it.equals("true", true) },
            description = tag(xml, "NewPortMappingDescription").orEmpty(),
            leaseDurationSec = tag(xml, "NewLeaseDuration")?.toLongOrNull() ?: 0
        )
    }

    /** UPnP error code from a SOAP fault (e.g. 713 = SpecifiedArrayIndexInvalid). */
    fun parseSoapErrorCode(xml: String): Int? = tag(xml, "errorCode")?.toIntOrNull()

    /** NAT-PMP external address request (version 0, opcode 0). */
    val natPmpRequest: ByteArray = byteArrayOf(0, 0)

    fun parseNatPmpResponse(data: ByteArray, length: Int = data.size): NatPmpResult? {
        if (length < 12) return null
        val version = data[0].toInt() and 0xFF
        val op = data[1].toInt() and 0xFF
        if (version != 0 || op != 128) return null
        val result = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
        val epoch = ((data[4].toLong() and 0xFF) shl 24) or ((data[5].toLong() and 0xFF) shl 16) or
            ((data[6].toLong() and 0xFF) shl 8) or (data[7].toLong() and 0xFF)
        val ip = if (result == 0) (8..11).joinToString(".") { (data[it].toInt() and 0xFF).toString() } else null
        return NatPmpResult(ip, result, epoch)
    }

    private val sensitiveInternalPorts = mapOf(
        21 to "FTP", 22 to "SSH", 23 to "Telnet", 445 to "SMB", 139 to "NetBIOS", 3389 to "RDP",
        5900 to "VNC", 3306 to "MySQL", 5432 to "PostgreSQL", 1433 to "MSSQL", 6379 to "Redis",
        27017 to "MongoDB", 9200 to "Elasticsearch", 5555 to "ADB", 2375 to "Docker", 554 to "RTSP camera",
        80 to "HTTP admin", 8080 to "HTTP admin", 443 to "HTTPS admin", 8443 to "HTTPS admin", 1883 to "MQTT"
    )

    /** Turns raw discovery data into prioritized audit findings. */
    fun findings(devices: List<UpnpDevice>, natPmp: NatPmpResult?, gatewayIp: String?): List<UpnpFinding> {
        val out = mutableListOf<UpnpFinding>()
        val igds = devices.filter { it.isInternetGateway }
        igds.forEach { gw ->
            out += UpnpFinding(
                RiskLevel.MEDIUM,
                "UPnP port mapping enabled on ${gw.ipAddress}",
                "Any device or malware on this LAN can open inbound ports through the router without a password. Disable UPnP IGD on the router unless an app needs it."
            )
            if (gw.ipAddress != gatewayIp && gatewayIp != null) {
                out += UpnpFinding(
                    RiskLevel.MEDIUM,
                    "Unexpected gateway service on ${gw.ipAddress}",
                    "This device advertises itself as an Internet Gateway but isn't your default gateway ($gatewayIp). It could be a second router, a misconfigured device, or a rogue gateway."
                )
            }
            gw.portMappings.filter { it.enabled }.forEach { m ->
                val sensitive = sensitiveInternalPorts[m.internalPort]
                val wildcard = m.remoteHost.isBlank() || m.remoteHost == "*"
                val severity = when {
                    sensitive != null && wildcard -> RiskLevel.HIGH
                    sensitive != null -> RiskLevel.MEDIUM
                    else -> RiskLevel.LOW
                }
                out += UpnpFinding(
                    severity,
                    "Open mapping ${m.protocol} ${m.externalPort} → ${m.internalClient}:${m.internalPort}",
                    buildString {
                        append(if (m.description.isNotBlank()) "\"${m.description}\". " else "")
                        if (sensitive != null) append("Exposes $sensitive to the internet. ")
                        append(if (wildcard) "Reachable from any internet address. " else "Limited to ${m.remoteHost}. ")
                        append(if (m.leaseDurationSec == 0L) "Permanent (no lease expiry)." else "Lease ${m.leaseDurationSec}s.")
                    }
                )
            }
            gw.externalIp?.let { ext ->
                if (HostValidator.isPrivateAddress(ext)) {
                    out += UpnpFinding(
                        RiskLevel.LOW,
                        "Router's WAN address is private ($ext)",
                        "Your router is behind another NAT (ISP CGNAT or a second router), so UPnP mappings here may not actually be reachable from the internet."
                    )
                }
            }
        }
        if (natPmp != null && natPmp.resultCode == 0) {
            out += UpnpFinding(
                RiskLevel.MEDIUM,
                "NAT-PMP enabled on the gateway",
                "The router accepts NAT-PMP/PCP requests, which also let LAN devices open inbound ports without authentication." +
                    (natPmp.externalIp?.let { " Reported external IP: $it." } ?: "")
            )
        }
        devices.forEach { d ->
            val server = d.server ?: return@forEach
            val mini = Regex("miniupnpd/(\\d+)\\.(\\d+)", RegexOption.IGNORE_CASE).find(server)
            if (mini != null) {
                val major = mini.groupValues[1].toInt()
                val minor = mini.groupValues[2].toInt()
                if (major < 2 || (major == 2 && minor < 2)) {
                    out += UpnpFinding(
                        RiskLevel.HIGH,
                        "Outdated MiniUPnPd on ${d.ipAddress}",
                        "Server header \"$server\". MiniUPnPd before 2.2 has known memory-corruption and CallStranger (CVE-2020-12695) issues. Update the router firmware."
                    )
                }
            }
            if (Regex("(Intel|Portable) SDK for UPnP devices/1\\.[0-5]\\.", RegexOption.IGNORE_CASE).containsMatchIn(server)) {
                out += UpnpFinding(
                    RiskLevel.HIGH,
                    "Outdated libupnp on ${d.ipAddress}",
                    "Server header \"$server\". Old Portable/Intel UPnP SDK versions have remotely exploitable SSDP parsing bugs (CVE-2012-5958 family)."
                )
            }
        }
        if (devices.isNotEmpty() && igds.isEmpty() && (natPmp == null || natPmp.resultCode != 0)) {
            out += UpnpFinding(
                RiskLevel.LOW,
                "No port-mapping service found",
                "Devices answer SSDP, but no router offered UPnP IGD or NAT-PMP. That's the safer configuration."
            )
        }
        return out.sortedByDescending { it.severity.ordinal }
    }
}
