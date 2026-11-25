package com.fearmikey.rf_reapr.domain.model

/** ActiveTaskMonitor task name for a running audit (also read by the main-menu card). */
const val AUDIT_TASK_NAME = "Audit Mode"

/**
 * One selectable stage of Audit Mode. Declaration order is execution order: passive /
 * network-level checks first, then host discovery and the per-host checks that need it.
 *
 * @param active sends traffic to other hosts on the network (blocked in Passive Mode).
 * @param needsHosts runs against the host list (from discovery or a manual target list).
 */
enum class AuditCheck(
    val label: String,
    val description: String,
    val tool: String,
    val active: Boolean,
    val needsHosts: Boolean = false
) {
    WIFI(
        "Wi-Fi security",
        "Encryption, WPS and legacy modes on the connected network; evil-twin look-alikes nearby.",
        FindingTools.WIFI, active = false
    ),
    CAPTIVE_PORTAL(
        "Interception check",
        "Probes well-known check servers for captive portals, transparent proxies and TLS interception.",
        FindingTools.CAPTIVE_PORTAL, active = false
    ),
    DNS(
        "DNS resolver security",
        "Compares the network's resolver with DNS-over-HTTPS: hijacking, NXDOMAIN rewriting, DNSSEC, rebinding.",
        FindingTools.DNS_AUDIT, active = true
    ),
    UPNP(
        "Gateway UPnP / NAT-PMP",
        "Read-only SSDP and IGD queries: exposed port mappings and NAT-PMP. Nothing is changed on the router.",
        FindingTools.UPNP, active = true
    ),
    DISCOVERY(
        "Host discovery",
        "Sweeps the local subnet for live devices and adds them to the network map.",
        "Network Discovery", active = true
    ),
    PORTS(
        "Port & service scan",
        "TCP connect scan of every host with banner grabbing and CVE matching.",
        FindingTools.PORT_SCANNER, active = true, needsHosts = true
    ),
    TLS(
        "TLS & certificates",
        "Protocol, cipher-suite and certificate review of every TLS service the port scan finds.",
        FindingTools.TLS, active = true, needsHosts = true
    ),
    SNMP(
        "SNMP default communities",
        "Tries factory-default community strings on every host and inventories responders.",
        FindingTools.SNMP, active = true, needsHosts = true
    );
}

enum class AuditPreset(val label: String, val description: String, val checks: Set<AuditCheck>) {
    QUICK_POSTURE(
        "Network posture",
        "Is this network safe to use? Wi-Fi, interception, DNS and the gateway.",
        setOf(AuditCheck.WIFI, AuditCheck.CAPTIVE_PORTAL, AuditCheck.DNS, AuditCheck.UPNP)
    ),
    LAN_ASSESSMENT(
        "LAN assessment",
        "Find every device and what it exposes.",
        setOf(AuditCheck.DISCOVERY, AuditCheck.PORTS, AuditCheck.TLS, AuditCheck.SNMP, AuditCheck.UPNP)
    ),
    FULL("Full audit", "Every check, in one pass.", AuditCheck.entries.toSet()),
    PASSIVE("Passive only", "Nothing is sent to other devices on the LAN.", setOf(AuditCheck.WIFI, AuditCheck.CAPTIVE_PORTAL));
}

enum class PortDepth(val label: String, val detail: String) {
    QUICK("Quick", "14 high-impact ports"),
    STANDARD("Standard", "~170 common ports")
}

/** Where the per-host checks get their targets. */
sealed interface AuditTargets {
    data object LocalNetwork : AuditTargets
    data class Manual(val hosts: List<String>) : AuditTargets
}

data class AuditConfig(
    val checks: Set<AuditCheck>,
    val targets: AuditTargets = AuditTargets.LocalNetwork,
    val portDepth: PortDepth = PortDepth.QUICK,
    /** Drop hosts that the active engagement's scope marks out-of-scope or excluded. */
    val respectScope: Boolean = true,
    val maxHosts: Int = 64
)

enum class AuditStepStatus { PENDING, RUNNING, DONE, SKIPPED, FAILED, CANCELLED }

data class AuditStepState(
    val check: AuditCheck,
    val status: AuditStepStatus = AuditStepStatus.PENDING,
    /** Pulled in as a dependency of another selected check. */
    val autoAdded: Boolean = false,
    val summary: String? = null,
    /** 0..1 while running, when the step can report it. */
    val progress: Float? = null,
    val findings: List<FindingDraft> = emptyList(),
    val startedAt: Long? = null,
    val finishedAt: Long? = null
)

data class AuditHost(
    val address: String,
    val hostname: String? = null,
    val vendor: String? = null,
    val openPorts: List<OpenPort> = emptyList(),
    val snmpCommunity: String? = null
)

data class AuditRunState(
    val config: AuditConfig? = null,
    val steps: List<AuditStepState> = emptyList(),
    val hosts: List<AuditHost> = emptyList(),
    /** Hosts dropped because the engagement scope excludes them. */
    val outOfScope: List<String> = emptyList(),
    val isRunning: Boolean = false,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val projectName: String? = null,
    val networkLabel: String? = null
) {
    val hasRun: Boolean get() = startedAt != null
    val findings: List<FindingDraft> get() = steps.flatMap { it.findings }
}
