package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.repository.DiscoveredService

enum class ServiceCategory(val label: String) {
    REMOTE_ACCESS("Remote Access"),
    FILE_SHARING("File Sharing"),
    PRINTING("Printing & Scanning"),
    WEB("Web"),
    MEDIA("Media & Casting"),
    SMART_HOME("Smart Home / IoT"),
    APPLE("Apple"),
    DEVICE_INFO("Device Info"),
    OTHER("Other")
}

data class ServiceTypeInfo(
    val label: String,
    val category: ServiceCategory,
    /** Short note for services that commonly matter in a security assessment. */
    val securityNote: String? = null
)

/** A network host and every mDNS/DNS-SD service it advertises. */
data class MdnsHost(
    val address: String?,
    val displayName: String,
    val services: List<DiscoveredService>
) {
    val securityNotes: List<Pair<DiscoveredService, String>>
        get() = services.mapNotNull { s -> MdnsServiceCatalog.describe(s.type).securityNote?.let { s to it } }

    val primaryCategory: ServiceCategory
        get() = services.groupingBy { MdnsServiceCatalog.describe(it.type).category }
            .eachCount()
            .filterKeys { it != ServiceCategory.DEVICE_INFO && it != ServiceCategory.OTHER }
            .maxByOrNull { it.value }?.key
            ?: ServiceCategory.OTHER
}

/**
 * Knowledge base of DNS-SD service types: human-readable names, categories, and security
 * notes, plus the default set of types to browse and grouping helpers. Pure Kotlin.
 */
object MdnsServiceCatalog {

    /** DNS-SD meta-query that enumerates every service type advertised on the link. */
    const val META_QUERY_TYPE = "_services._dns-sd._udp"

    private val catalog: Map<String, ServiceTypeInfo> = mapOf(
        // Remote access
        "_ssh._tcp" to ServiceTypeInfo("SSH", ServiceCategory.REMOTE_ACCESS, "Remote shell exposed; verify key-based auth"),
        "_telnet._tcp" to ServiceTypeInfo("Telnet", ServiceCategory.REMOTE_ACCESS, "Cleartext remote login"),
        "_rfb._tcp" to ServiceTypeInfo("VNC Remote Desktop", ServiceCategory.REMOTE_ACCESS, "VNC is often weakly authenticated and unencrypted"),
        "_teamviewer._tcp" to ServiceTypeInfo("TeamViewer", ServiceCategory.REMOTE_ACCESS),
        "_adb._tcp" to ServiceTypeInfo("Android Debug Bridge", ServiceCategory.REMOTE_ACCESS, "Legacy ADB over TCP can grant an unauthenticated shell"),
        "_adb-tls-connect._tcp" to ServiceTypeInfo("Android Wireless Debugging", ServiceCategory.REMOTE_ACCESS, "Wireless debugging is enabled on this device"),
        "_adb-tls-pairing._tcp" to ServiceTypeInfo("Android Debugging (pairing)", ServiceCategory.REMOTE_ACCESS, "Device is currently in ADB pairing mode"),
        // File sharing
        "_smb._tcp" to ServiceTypeInfo("SMB File Sharing", ServiceCategory.FILE_SHARING, "Check for guest/anonymous shares and SMBv1"),
        "_afpovertcp._tcp" to ServiceTypeInfo("AFP File Sharing", ServiceCategory.FILE_SHARING, "Legacy Apple file sharing protocol"),
        "_nfs._tcp" to ServiceTypeInfo("NFS", ServiceCategory.FILE_SHARING, "NFS exports are often unauthenticated"),
        "_ftp._tcp" to ServiceTypeInfo("FTP", ServiceCategory.FILE_SHARING, "Cleartext credentials and data"),
        "_sftp-ssh._tcp" to ServiceTypeInfo("SFTP", ServiceCategory.FILE_SHARING),
        "_webdav._tcp" to ServiceTypeInfo("WebDAV", ServiceCategory.FILE_SHARING, "Unencrypted WebDAV exposes credentials"),
        "_webdavs._tcp" to ServiceTypeInfo("WebDAV (TLS)", ServiceCategory.FILE_SHARING),
        "_adisk._tcp" to ServiceTypeInfo("Time Machine Disk", ServiceCategory.FILE_SHARING),
        // Printing
        "_ipp._tcp" to ServiceTypeInfo("Printer (IPP)", ServiceCategory.PRINTING),
        "_ipps._tcp" to ServiceTypeInfo("Printer (IPP over TLS)", ServiceCategory.PRINTING),
        "_printer._tcp" to ServiceTypeInfo("Printer (LPD)", ServiceCategory.PRINTING, "LPD accepts print jobs without authentication"),
        "_pdl-datastream._tcp" to ServiceTypeInfo("Printer (Raw 9100)", ServiceCategory.PRINTING, "Raw port 9100 accepts jobs and PJL without authentication"),
        "_scanner._tcp" to ServiceTypeInfo("Scanner", ServiceCategory.PRINTING),
        "_uscan._tcp" to ServiceTypeInfo("Scanner (eSCL)", ServiceCategory.PRINTING),
        "_uscans._tcp" to ServiceTypeInfo("Scanner (eSCL over TLS)", ServiceCategory.PRINTING),
        // Web
        "_http._tcp" to ServiceTypeInfo("Web Interface (HTTP)", ServiceCategory.WEB, "Unencrypted web/admin interface"),
        "_https._tcp" to ServiceTypeInfo("Web Interface (HTTPS)", ServiceCategory.WEB),
        // Media
        "_googlecast._tcp" to ServiceTypeInfo("Google Cast", ServiceCategory.MEDIA),
        "_spotify-connect._tcp" to ServiceTypeInfo("Spotify Connect", ServiceCategory.MEDIA),
        "_airplay._tcp" to ServiceTypeInfo("AirPlay", ServiceCategory.MEDIA),
        "_raop._tcp" to ServiceTypeInfo("AirPlay Audio", ServiceCategory.MEDIA),
        "_daap._tcp" to ServiceTypeInfo("Music Library (DAAP)", ServiceCategory.MEDIA),
        "_sonos._tcp" to ServiceTypeInfo("Sonos", ServiceCategory.MEDIA),
        "_amzn-wplay._tcp" to ServiceTypeInfo("Amazon Fire TV", ServiceCategory.MEDIA),
        "_androidtvremote2._tcp" to ServiceTypeInfo("Android TV Remote", ServiceCategory.MEDIA),
        "_plexmediasvr._tcp" to ServiceTypeInfo("Plex Media Server", ServiceCategory.MEDIA),
        "_nvstream._tcp" to ServiceTypeInfo("NVIDIA GameStream", ServiceCategory.MEDIA),
        // Smart home / IoT
        "_hap._tcp" to ServiceTypeInfo("HomeKit Accessory", ServiceCategory.SMART_HOME),
        "_hap._udp" to ServiceTypeInfo("HomeKit Accessory (Thread)", ServiceCategory.SMART_HOME),
        "_homekit._tcp" to ServiceTypeInfo("HomeKit Hub", ServiceCategory.SMART_HOME),
        "_matter._tcp" to ServiceTypeInfo("Matter Device", ServiceCategory.SMART_HOME),
        "_matterc._udp" to ServiceTypeInfo("Matter (commissioning)", ServiceCategory.SMART_HOME, "Device is open for Matter commissioning"),
        "_hue._tcp" to ServiceTypeInfo("Philips Hue Bridge", ServiceCategory.SMART_HOME),
        "_esphomelib._tcp" to ServiceTypeInfo("ESPHome", ServiceCategory.SMART_HOME, "Verify the native API encryption key is set"),
        "_home-assistant._tcp" to ServiceTypeInfo("Home Assistant", ServiceCategory.SMART_HOME),
        "_mqtt._tcp" to ServiceTypeInfo("MQTT Broker", ServiceCategory.SMART_HOME, "MQTT brokers often allow anonymous access"),
        "_coap._udp" to ServiceTypeInfo("CoAP", ServiceCategory.SMART_HOME),
        "_octoprint._tcp" to ServiceTypeInfo("OctoPrint (3D printer)", ServiceCategory.SMART_HOME, "Exposes printer control; ensure access control is enabled"),
        "_miio._udp" to ServiceTypeInfo("Xiaomi Mi Home", ServiceCategory.SMART_HOME),
        "_meshcop._udp" to ServiceTypeInfo("Thread Border Router", ServiceCategory.SMART_HOME),
        "_elg._tcp" to ServiceTypeInfo("Elgato Device", ServiceCategory.SMART_HOME),
        // Apple ecosystem
        "_companion-link._tcp" to ServiceTypeInfo("Apple Companion Link", ServiceCategory.APPLE),
        "_apple-mobdev2._tcp" to ServiceTypeInfo("Apple Mobile Device Sync", ServiceCategory.APPLE),
        "_touch-able._tcp" to ServiceTypeInfo("Apple Remote", ServiceCategory.APPLE),
        "_sleep-proxy._udp" to ServiceTypeInfo("Bonjour Sleep Proxy", ServiceCategory.APPLE),
        // Device information
        "_device-info._tcp" to ServiceTypeInfo("Device Info", ServiceCategory.DEVICE_INFO),
        "_workstation._tcp" to ServiceTypeInfo("Workstation", ServiceCategory.DEVICE_INFO)
    )

    /**
     * Types browsed directly in addition to the meta-query, so common services are still
     * found on stacks that don't answer `_services._dns-sd._udp`. Deliberately bounded to
     * avoid registering dozens of listeners with NsdManager.
     */
    val defaultBrowseTypes: List<String> = listOf(
        "_http._tcp", "_https._tcp", "_ipp._tcp", "_printer._tcp", "_pdl-datastream._tcp",
        "_ssh._tcp", "_smb._tcp", "_rfb._tcp", "_googlecast._tcp", "_airplay._tcp",
        "_raop._tcp", "_spotify-connect._tcp", "_hap._tcp", "_matter._tcp", "_mqtt._tcp",
        "_adb-tls-connect._tcp", "_device-info._tcp", "_workstation._tcp"
    )

    /**
     * Normalizes the many spellings Android hands back (`._http._tcp`, `_http._tcp.local.`,
     * `_http._tcp.`) to `_http._tcp`.
     */
    fun normalizeType(raw: String): String =
        raw.trim()
            .trim('.')
            .removeSuffix(".local")
            .trim('.')
            .lowercase()

    /**
     * Converts a meta-query result into a browsable type. Android reports these with the
     * service "name" holding the first label (`_http`) and the "type" holding the protocol
     * (`_tcp.local.`). Returns null for anything that doesn't look like a service type.
     */
    fun typeFromMetaQueryResult(serviceName: String, serviceType: String): String? {
        val name = serviceName.trim('.').lowercase()
        val proto = normalizeType(serviceType).substringBefore('.')
        if (!name.startsWith("_") || (proto != "_tcp" && proto != "_udp")) return null
        return "$name.$proto"
    }

    fun describe(type: String): ServiceTypeInfo {
        val normalized = normalizeType(type)
        return catalog[normalized] ?: ServiceTypeInfo(
            label = normalized.substringBefore('.').removePrefix("_").ifBlank { normalized },
            category = ServiceCategory.OTHER
        )
    }

    /** True for services whose web UI can be opened directly in a browser. */
    fun browserUrl(service: DiscoveredService): String? {
        val host = service.host ?: return null
        val hostPart = if (host.contains(':')) "[$host]" else host
        val path = service.attributes["path"]?.takeIf { it.startsWith("/") } ?: ""
        return when (normalizeType(service.type)) {
            "_http._tcp" -> "http://$hostPart:${service.port}$path"
            "_https._tcp" -> "https://$hostPart:${service.port}$path"
            else -> null
        }
    }

    /** Groups services by advertising host, most interesting (security notes, count) first. */
    fun groupByHost(services: List<DiscoveredService>): List<MdnsHost> =
        services.groupBy { it.host }
            .map { (address, hostServices) ->
                MdnsHost(
                    address = address,
                    displayName = deviceName(hostServices) ?: address ?: "Unresolved host",
                    services = hostServices.sortedWith(compareBy({ describe(it.type).category.ordinal }, { it.port }))
                )
            }
            .sortedWith(
                compareByDescending<MdnsHost> { it.securityNotes.isNotEmpty() }
                    .thenByDescending { it.services.size }
                    .thenBy { it.displayName.lowercase() }
            )

    /**
     * Best-effort friendly device name from TXT records (Cast `fn`, printer `ty`, model
     * `md`/`model`) falling back to the most common instance name.
     */
    fun deviceName(services: List<DiscoveredService>): String? {
        val txtKeys = listOf("fn", "ty", "md", "model", "am")
        for (key in txtKeys) {
            services.firstNotNullOfOrNull { s -> s.attributes[key]?.takeIf { it.isNotBlank() } }?.let { return it }
        }
        return services.groupingBy { it.name }.eachCount().maxByOrNull { it.value }?.key
    }
}
