package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.repository.AlertSeverity
import com.fearmikey.rf_reapr.domain.repository.ArpAlert

/** Everything observed about the current network in one monitoring cycle. */
data class NetworkSnapshot(
    val ssid: String?,
    val bssid: String?,
    val gatewayIp: String?,
    val gatewayMac: String?,
    val dnsServers: List<String>,
    /** Full neighbour table when readable (ip -> mac), else empty. */
    val neighbors: Map<String, String> = emptyMap(),
    /** Address that answered a TTL=1 probe to the internet, if measured this cycle. */
    val firstHop: String? = null
) {
    /** Same logical network: same SSID (or both non-Wi-Fi) and same gateway IP. */
    fun sameNetworkAs(other: NetworkSnapshot): Boolean = ssid == other.ssid && gatewayIp == other.gatewayIp
}

/**
 * Detects MITM indicators by comparing consecutive snapshots. Pure logic so it can be
 * unit-tested; the repository just gathers the data.
 */
object ArpMonitorEvaluator {

    private fun known(mac: String?) = mac != null && mac != "Unknown" && mac.isNotBlank()

    fun evaluate(previous: NetworkSnapshot?, current: NetworkSnapshot, now: Long = System.currentTimeMillis()): List<ArpAlert> {
        val alerts = mutableListOf<ArpAlert>()

        // Checks within the current snapshot.
        val gwMac = current.gatewayMac
        if (known(gwMac) && current.gatewayIp != null) {
            val sharing = current.neighbors.filter { (ip, mac) -> mac == gwMac && ip != current.gatewayIp }.keys
            if (sharing.isNotEmpty()) {
                alerts += ArpAlert(
                    now,
                    "Gateway MAC is also claimed by ${sharing.joinToString()}",
                    oldMac = current.gatewayIp,
                    newMac = gwMac!!,
                    severity = AlertSeverity.CRITICAL,
                    detail = "Two IP addresses resolve to the same hardware address. This is the classic signature of ARP spoofing: another host is answering for the gateway."
                )
            }
        }
        val firstHop = current.firstHop
        if (firstHop != null && current.gatewayIp != null && firstHop != current.gatewayIp && HostValidator.isPrivateAddress(firstHop) &&
            previous?.firstHop != firstHop
        ) {
            alerts += ArpAlert(
                now,
                "Traffic passes through $firstHop before the gateway",
                oldMac = current.gatewayIp,
                newMac = firstHop,
                severity = AlertSeverity.CRITICAL,
                detail = "The first router hop should be your gateway (${current.gatewayIp}). An extra local hop means another device is forwarding your traffic, as a man-in-the-middle does (unless you're deliberately behind a second router)."
            )
        }

        if (previous == null) return alerts

        if (!previous.sameNetworkAs(current)) {
            if (previous.ssid == current.ssid && previous.ssid != null && previous.gatewayIp != current.gatewayIp) {
                alerts += ArpAlert(
                    now, "Gateway IP changed on the same network",
                    oldMac = previous.gatewayIp ?: "none", newMac = current.gatewayIp ?: "none",
                    severity = AlertSeverity.WARNING,
                    detail = "The default gateway handed out for \"${current.ssid}\" changed. A rogue DHCP server can do this to redirect traffic."
                )
            } else if (previous.ssid != current.ssid) {
                alerts += ArpAlert(
                    now, "Network changed", oldMac = previous.ssid ?: "none", newMac = current.ssid ?: "none",
                    severity = AlertSeverity.INFO, detail = "Baseline reset for the new network."
                )
            }
            return alerts
        }

        if (known(previous.gatewayMac) && known(current.gatewayMac) && previous.gatewayMac != current.gatewayMac) {
            alerts += ArpAlert(
                now, "Gateway MAC address changed!",
                oldMac = previous.gatewayMac!!, newMac = current.gatewayMac!!,
                severity = AlertSeverity.CRITICAL,
                detail = "The hardware address behind the gateway IP changed without the network changing. Unless the router was replaced, another device is impersonating it (ARP spoofing)."
            )
        }
        if (previous.dnsServers.isNotEmpty() && current.dnsServers.isNotEmpty() && previous.dnsServers.toSet() != current.dnsServers.toSet()) {
            alerts += ArpAlert(
                now, "DNS servers changed",
                oldMac = previous.dnsServers.joinToString(), newMac = current.dnsServers.joinToString(),
                severity = AlertSeverity.WARNING,
                detail = "The resolver configuration changed mid-session. Rogue DHCP servers use this to redirect lookups."
            )
        }
        if (known(previous.bssid) && known(current.bssid) && previous.bssid != current.bssid) {
            alerts += ArpAlert(
                now, "Access point changed (same SSID)",
                oldMac = previous.bssid!!, newMac = current.bssid!!,
                severity = AlertSeverity.INFO,
                detail = "Normal when roaming between access points of one network. Unexpected in a single-AP network, where it could be an evil-twin AP."
            )
        }
        return alerts
    }

    /** Pulls the responding address out of `ping -t 1` output ("From 192.168.1.1 icmp_seq=1 Time to live exceeded"). */
    fun parseFirstHop(pingOutput: String): String? =
        Regex("From ([0-9]{1,3}(?:\\.[0-9]{1,3}){3})(?: \\([^)]*\\))?:? .*(?:Time to live exceeded|ttl exceeded)", RegexOption.IGNORE_CASE)
            .find(pingOutput)?.groupValues?.get(1)
}
