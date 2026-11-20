package com.fearmikey.rf_reapr.ui.topology

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.DeviceUnknown
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.fearmikey.rf_reapr.domain.model.DeviceType
import com.fearmikey.rf_reapr.domain.model.NetworkNode
import com.fearmikey.rf_reapr.domain.model.RiskLevel

/** Accent color for the scanning device's own node. Not used by any [RiskLevel]. */
val MyDeviceColor = Color(0xFF29B6F6)

/** Accent color used for SNMP indicators across the map, list, and detail sheet. */
val SnmpColor = Color(0xFF00ACC1)

/** Risk colors, kept in sync with [com.fearmikey.rf_reapr.ui.scanner.SeverityBadge]. */
fun RiskLevel.color(): Color = when (this) {
    RiskLevel.LOW -> Color.Green
    RiskLevel.MEDIUM -> Color.Yellow
    RiskLevel.HIGH -> Color(0xFFFFA500)
    RiskLevel.CRITICAL -> Color.Red
}

fun RiskLevel.displayName(): String = name.lowercase().replaceFirstChar { it.uppercase() }

fun DeviceType.icon(): ImageVector = when (this) {
    DeviceType.GATEWAY -> Icons.Default.Router
    DeviceType.SWITCH -> Icons.Default.Hub
    DeviceType.ACCESS_POINT -> Icons.Default.Wifi
    DeviceType.WORKSTATION -> Icons.Default.Computer
    DeviceType.SERVER -> Icons.Default.Dns
    DeviceType.NETWORK_DEVICE -> Icons.Default.DeviceHub
    DeviceType.MOBILE -> Icons.Default.PhoneAndroid
    DeviceType.IOT -> Icons.Default.Sensors
    DeviceType.UNKNOWN -> Icons.Default.DeviceUnknown
}

fun DeviceType.displayName(): String = when (this) {
    DeviceType.GATEWAY -> "Gateway"
    DeviceType.SWITCH -> "Switch"
    DeviceType.ACCESS_POINT -> "Access Point"
    DeviceType.WORKSTATION -> "Workstation"
    DeviceType.SERVER -> "Server"
    DeviceType.NETWORK_DEVICE -> "Network Device"
    DeviceType.MOBILE -> "Mobile"
    DeviceType.IOT -> "IoT"
    DeviceType.UNKNOWN -> "Unknown"
}

val DeviceType.isInfrastructure: Boolean
    get() = this == DeviceType.GATEWAY || this == DeviceType.SWITCH || this == DeviceType.ACCESS_POINT

val NetworkNode.vulnerabilityCount: Int
    get() = openPorts.sumOf { it.vulnerabilities.size }

/** Best human-readable name for a node, falling back through SNMP and manufacturer to the IP. */
val NetworkNode.displayName: String
    get() = hostname ?: snmpData?.sysName?.takeIf { it.isNotBlank() } ?: ipAddress

/** Plain-text summary of a node, suitable for copying into notes or a report. */
fun NetworkNode.toSummaryText(): String = buildString {
    appendLine("Device: ${hostname ?: "Unknown"}")
    appendLine("IP: $ipAddress")
    macAddress?.let { appendLine("MAC: $it") }
    manufacturer?.let { appendLine("Manufacturer: $it") }
    appendLine("Type: ${deviceType.displayName()}")
    appendLine("Risk: ${riskLevel.displayName()}")
    snmpData?.let { snmp ->
        appendLine("SNMP: ${snmp.sysName ?: "responding"}")
        snmp.sysDescr?.let { appendLine("  Descr: $it") }
    }
    if (openPorts.isEmpty()) {
        appendLine("Open ports: none detected")
    } else {
        appendLine("Open ports:")
        openPorts.sortedBy { it.port }.forEach { port ->
            append("  ${port.port}/${port.serviceName}")
            if (port.vulnerabilities.isNotEmpty()) {
                append(" - ${port.vulnerabilities.joinToString { "${it.cveId} (${it.severity.displayName()})" }}")
            }
            appendLine()
        }
    }
}.trimEnd()

enum class NodeSortOrder(val label: String) {
    IP("IP Address"),
    RISK("Risk"),
    OPEN_PORTS("Open Ports"),
    NAME("Name")
}

/**
 * Pure filtering/sorting logic shared by the map (to dim non-matching nodes) and the list
 * (to hide them). Kept free of Compose so it can be unit tested.
 */
object TopologyFilter {

    /**
     * Returns true when [node] matches the free-text [query] (hostname, IP, MAC, manufacturer,
     * device type, SNMP name, port number, service name, or CVE id) and is in [riskLevels]
     * (an empty set means "any risk").
     */
    fun matches(node: NetworkNode, query: String, riskLevels: Set<RiskLevel>): Boolean {
        if (riskLevels.isNotEmpty() && node.riskLevel !in riskLevels) return false
        val q = query.trim()
        if (q.isEmpty()) return true

        val textFields = sequenceOf(
            node.hostname,
            node.ipAddress,
            node.macAddress,
            node.manufacturer,
            node.deviceType.displayName(),
            node.snmpData?.sysName,
            node.snmpData?.sysDescr
        )
        if (textFields.any { it != null && it.contains(q, ignoreCase = true) }) return true

        // Port and service matching ("22", "ssh", "CVE-2021-...")
        val portQuery = q.removePrefix(":").toIntOrNull()
        return node.openPorts.any { port ->
            (portQuery != null && port.port == portQuery) ||
                port.serviceName.contains(q, ignoreCase = true) ||
                port.vulnerabilities.any { it.cveId.contains(q, ignoreCase = true) }
        }
    }

    fun sort(nodes: List<NetworkNode>, order: NodeSortOrder): List<NetworkNode> = when (order) {
        NodeSortOrder.IP -> nodes.sortedWith(ipComparator)
        NodeSortOrder.RISK -> nodes.sortedWith(
            compareByDescending<NetworkNode> { it.riskLevel.ordinal }
                .thenByDescending { it.vulnerabilityCount }
                .then(ipComparator)
        )
        NodeSortOrder.OPEN_PORTS -> nodes.sortedWith(
            compareByDescending<NetworkNode> { it.openPorts.size }.then(ipComparator)
        )
        NodeSortOrder.NAME -> nodes.sortedWith(
            compareBy<NetworkNode> { it.hostname == null }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
                .then(ipComparator)
        )
    }

    val ipComparator: Comparator<NetworkNode> = Comparator { a, b ->
        val pa = a.ipAddress.split(".").mapNotNull { it.toIntOrNull() }
        val pb = b.ipAddress.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until minOf(pa.size, pb.size)) {
            if (pa[i] != pb[i]) return@Comparator pa[i].compareTo(pb[i])
        }
        pa.size.compareTo(pb.size).takeIf { it != 0 } ?: a.ipAddress.compareTo(b.ipAddress)
    }
}
