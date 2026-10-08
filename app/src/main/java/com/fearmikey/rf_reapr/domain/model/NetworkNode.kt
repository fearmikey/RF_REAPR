package com.fearmikey.rf_reapr.domain.model

/**
 * Represents a device discovered on the network.
 */
data class NetworkNode(
    val id: String,
    val ipAddress: String,
    val macAddress: String? = null,
    val hostname: String? = null,
    val riskLevel: RiskLevel = RiskLevel.LOW,
    val openPorts: List<OpenPort> = emptyList(),
    val deviceType: DeviceType = DeviceType.UNKNOWN,
    val manufacturer: String? = null,
    val parentId: String? = null,
    val snmpData: SnmpResult? = null
) {
    val hasPublicIp: Boolean
        get() = isPublicIp(ipAddress)

    companion object {
        val HIGH_IMPACT_PORTS = listOf(21, 22, 23, 53, 80, 443, 445, 161, 389, 1433, 3306, 3389, 8080, 8443)

        fun isPublicIp(ip: String): Boolean {
            if (ip.isBlank()) return false
            return try {
                val parts = ip.trim().split(".")
                if (parts.size == 4) {
                    val p0 = parts[0].toIntOrNull() ?: return false
                    val p1 = parts[1].toIntOrNull() ?: return false
                    val p2 = parts[2].toIntOrNull() ?: return false
                    val p3 = parts[3].toIntOrNull() ?: return false

                    if (p0 < 0 || p0 > 255 || p1 < 0 || p1 > 255 || p2 < 0 || p2 > 255 || p3 < 0 || p3 > 255) return false

                    // Private / Local / Reserved IP ranges
                    if (p0 == 10) return false // 10.0.0.0/8
                    if (p0 == 172 && p1 in 16..31) return false // 172.16.0.0/12
                    if (p0 == 192 && p1 == 168) return false // 192.168.0.0/16
                    if (p0 == 100 && p1 in 64..127) return false // CGNAT 100.64.0.0/10
                    if (p0 == 169 && p1 == 254) return false // Link Local 169.254.0.0/16
                    if (p0 == 127 || p0 == 0) return false // Loopback / Unspecified
                    if (p0 >= 224) return false // Multicast / Reserved

                    return true
                }

                val addr = java.net.InetAddress.getByName(ip)
                !addr.isSiteLocalAddress &&
                !addr.isLoopbackAddress &&
                !addr.isLinkLocalAddress &&
                !addr.isAnyLocalAddress &&
                !addr.isMulticastAddress
            } catch (_: Exception) {
                false
            }
        }
    }
}

/**
 * Represents a connection between two nodes (e.g., Gateway to Device).
 */
data class NodesEdge(
    val fromNodeId: String,
    val toNodeId: String,
    val connectionType: String = "Subnet Link"
)

/**
 * Container for the entire network graph structure.
 */
data class NetworkGraph(
    val nodes: List<NetworkNode> = emptyList(),
    val edges: List<NodesEdge> = emptyList()
)
