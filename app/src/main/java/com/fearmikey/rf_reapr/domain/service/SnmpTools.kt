package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.domain.model.SnmpResult
import java.util.Locale

data class SnmpFinding(val severity: RiskLevel, val title: String, val detail: String)

object SnmpTools {

    /** Common factory-default / guessable community strings. Kept short on purpose. */
    val DEFAULT_COMMUNITIES = listOf(
        "public", "private", "community", "cisco", "admin", "manager", "default",
        "snmp", "monitor", "read", "write", "secret", "router", "switch"
    )

    /** Well-known subtrees for the custom walk shortcuts. */
    val WALK_PRESETS = linkedMapOf(
        "System" to "1.3.6.1.2.1.1",
        "Interfaces" to "1.3.6.1.2.1.2.2",
        "IP routes" to "1.3.6.1.2.1.4.21",
        "ARP table" to "1.3.6.1.2.1.4.22",
        "TCP listeners" to "1.3.6.1.2.1.6.13",
        "Processes" to "1.3.6.1.2.1.25.4.2.1.2",
        "Software" to "1.3.6.1.2.1.25.6.3.1.2",
        "Storage" to "1.3.6.1.2.1.25.2.3"
    )

    private val ifTypes = mapOf(
        1 to "other", 6 to "Ethernet", 23 to "PPP", 24 to "Loopback", 53 to "Virtual", 71 to "Wi-Fi (802.11)",
        117 to "Gigabit Ethernet", 131 to "Tunnel", 135 to "VLAN (L2)", 136 to "VLAN (L3)", 144 to "IEEE 1394",
        161 to "Link aggregation", 166 to "MPLS", 209 to "Bridge", 244 to "WWAN (3GPP)", 237 to "802.16 WMAN"
    )

    fun ifTypeName(raw: Int): String = ifTypes[raw] ?: "type $raw"

    fun ifStatusName(status: Int): String = when (status) {
        1 -> "up"; 2 -> "down"; 3 -> "testing"; 4 -> "unknown"; 5 -> "dormant"; 6 -> "notPresent"; 7 -> "lowerLayerDown"
        else -> "unknown($status)"
    }

    fun formatSpeed(bitsPerSecond: Long): String = when {
        bitsPerSecond <= 0 -> "–"
        bitsPerSecond >= 1_000_000_000 -> String.format(Locale.US, "%.0f Gbps", bitsPerSecond / 1e9)
        bitsPerSecond >= 1_000_000 -> String.format(Locale.US, "%.0f Mbps", bitsPerSecond / 1e6)
        bitsPerSecond >= 1_000 -> String.format(Locale.US, "%.0f kbps", bitsPerSecond / 1e3)
        else -> "$bitsPerSecond bps"
    }

    fun formatOctets(bytes: Long): String = when {
        bytes < 0 -> "–"
        bytes >= 1L shl 40 -> String.format(Locale.US, "%.2f TB", bytes / (1L shl 40).toDouble())
        bytes >= 1L shl 30 -> String.format(Locale.US, "%.2f GB", bytes / (1L shl 30).toDouble())
        bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / (1L shl 20).toDouble())
        bytes >= 1L shl 10 -> String.format(Locale.US, "%.1f KB", bytes / (1L shl 10).toDouble())
        else -> "$bytes B"
    }

    /** Normalizes "1.3.6..." / ".1.3.6..." and rejects anything that isn't a numeric OID. */
    fun normalizeOid(input: String): String? {
        val oid = input.trim().trimStart('.')
        return oid.takeIf { Regex("^\\d+(\\.\\d+)+$").matches(it) }
    }

    /** Vendor hint from sysObjectID's enterprise number. */
    fun vendorFromObjectId(oid: String?): String? {
        val enterprise = oid?.trimStart('.')?.removePrefix("1.3.6.1.4.1.")?.substringBefore('.')?.toIntOrNull() ?: return null
        return when (enterprise) {
            9 -> "Cisco"; 11 -> "HP"; 2636 -> "Juniper"; 14988 -> "MikroTik"; 4526 -> "Netgear"; 8072 -> "Net-SNMP (Linux/BSD)"
            311 -> "Microsoft"; 41112 -> "Ubiquiti"; 2011 -> "Huawei"; 25506 -> "H3C"; 6876 -> "VMware"; 674 -> "Dell"
            12356 -> "Fortinet"; 25461 -> "Palo Alto"; 6574 -> "Synology"; 24681 -> "QNAP"; 1588 -> "Brocade"; 30065 -> "Arista"
            171 -> "D-Link"; 11863 -> "TP-Link"; 3955 -> "Linksys"; 2021 -> "UCD-SNMP"; 1916 -> "Extreme"; 12325 -> "pfSense/BSD"
            else -> "Enterprise $enterprise"
        }
    }

    fun findings(result: SnmpResult, workingDefaults: List<String>): List<SnmpFinding> {
        val out = mutableListOf<SnmpFinding>()
        val usedDefault = result.community in DEFAULT_COMMUNITIES
        if (usedDefault) {
            out += SnmpFinding(
                if (result.community.equals("private", true) || result.community.equals("write", true)) RiskLevel.HIGH else RiskLevel.MEDIUM,
                "Default community \"${result.community}\" accepted",
                "Anyone on the network can read this device's configuration, interfaces and neighbour tables. Change the community string or move to SNMPv3."
            )
        }
        val others = workingDefaults.filter { it != result.community }
        if (others.isNotEmpty()) {
            out += SnmpFinding(
                if (others.any { it.equals("private", true) || it.equals("write", true) }) RiskLevel.HIGH else RiskLevel.MEDIUM,
                "Other default communities also work: ${others.joinToString()}",
                "\"private\" is conventionally the read-write community; if it works, an attacker may be able to change the device's configuration."
            )
        }
        out += SnmpFinding(
            RiskLevel.LOW,
            "SNMP v${if (result.version == 0) "1" else "2c"} is unauthenticated cleartext",
            "Community strings and all data travel unencrypted. SNMPv3 with authPriv is recommended."
        )
        result.sysDescr?.takeIf { Regex("\\d+\\.\\d+").containsMatchIn(it) }?.let {
            out += SnmpFinding(RiskLevel.LOW, "Firmware/OS version disclosed", it.take(200))
        }
        if (result.arpTable.isNotEmpty()) {
            out += SnmpFinding(
                RiskLevel.LOW,
                "${result.arpTable.size} neighbour MAC/IP entries readable",
                "The ARP table reveals other hosts on the network without scanning them."
            )
        }
        return out.sortedByDescending { it.severity.ordinal }
    }
}
