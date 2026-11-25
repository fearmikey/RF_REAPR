package com.fearmikey.rf_reapr.domain.model

/** Triage state of a [Finding]. */
enum class FindingStatus(val label: String) {
    OPEN("Open"),
    ACCEPTED("Risk accepted"),
    FIXED("Fixed"),
    FALSE_POSITIVE("False positive");

    companion object {
        fun parse(value: String?): FindingStatus = entries.firstOrNull { it.name == value } ?: OPEN
    }
}

/**
 * A security finding persisted across scans. Produced by tools as [FindingDraft]s and
 * merged into the store by FindingMerger (de-duplicated by [fingerprint]).
 */
data class Finding(
    val id: Long = 0,
    /** Owning project, or null when no project was active. */
    val projectId: String?,
    val fingerprint: String,
    val tool: String,
    val target: String,
    val severity: RiskLevel,
    val title: String,
    val detail: String,
    val remediation: String? = null,
    val status: FindingStatus = FindingStatus.OPEN,
    val firstSeen: Long,
    val lastSeen: Long,
    val occurrences: Int = 1,
    val notes: String? = null
)

/** What a tool reports; the repository turns it into a [Finding]. */
data class FindingDraft(
    val tool: String,
    val target: String,
    val severity: RiskLevel,
    val title: String,
    val detail: String,
    val remediation: String? = null
)

/** Tool names used as [Finding.tool] (stable: they are part of the fingerprint). */
object FindingTools {
    const val PORT_SCANNER = "Port Scanner"
    const val UPNP = "UPnP Auditor"
    const val SNMP = "SNMP Browser"
    const val TLS = "TLS Scanner"
    const val DNS_AUDIT = "DNS Auditor"
    const val NFC = "NFC Scanner"
    const val ARP = "ARP Detector"
    const val USB = "USB OTG Auditor"
    const val CELLULAR = "Cellular Recon"
    const val WIFI = "Wi-Fi Fingerprinter"
    const val BLE = "Bluetooth Proximity"
    const val CAPTIVE_PORTAL = "Captive Portal"
    const val NETWORK_MONITOR = "Network Monitor"
    const val WEB = "Website Inspector"
    const val SUBDOMAIN = "Subdomain Finder"
    const val NMAP = "Nmap Import"
    const val SHODAN = "Shodan Recon"
    const val THREAT_INTEL = "Threat Intel"
    const val BREACH = "Breach Check"
    const val CLOUD = "Cloud Asset Discovery"
    const val MANUAL = "Manual"
}
