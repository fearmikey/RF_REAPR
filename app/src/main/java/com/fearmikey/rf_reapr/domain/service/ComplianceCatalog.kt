package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.ComplianceControl
import com.fearmikey.rf_reapr.domain.model.Finding
import com.fearmikey.rf_reapr.domain.model.FindingStatus
import com.fearmikey.rf_reapr.domain.model.FindingTools

/** The static control lists behind the compliance checklists (single source for UI and reports). */
object ComplianceCatalog {

    fun controls(frameworkId: String): List<ComplianceControl> = when (frameworkId) {
        "NIST" -> listOf(
            ComplianceControl("NIST-3.1.1", "NIST", "Authorized User Access", "Limit system access to authorized users, processes acting on behalf of authorized users, and devices (including other systems)."),
            ComplianceControl("NIST-3.1.2", "NIST", "Transaction/Function Limiting", "Limit system access to the types of transactions and functions that authorized users are permitted to execute."),
            ComplianceControl("NIST-3.5.3", "NIST", "Multifactor Authentication", "Use multifactor authentication for local and network access to privileged accounts and for network access to non-privileged accounts."),
            ComplianceControl("NIST-3.11.2", "NIST", "Vulnerability Scanning", "Scan for vulnerabilities in organizational systems and applications periodically and when new vulnerabilities affecting those systems are identified."),
            ComplianceControl("NIST-3.12.1", "NIST", "Security Control Assessment", "Periodically assess the security controls in organizational systems to determine if the controls are effective in their application."),
            ComplianceControl("NIST-3.12.3", "NIST", "Continuous Monitoring", "Monitor organizational systems on an ongoing basis and notify designated organizational officials when a predetermined set of security events occurs."),
            ComplianceControl("NIST-3.13.8", "NIST", "Cryptographic Protection in Transit", "Implement cryptographic mechanisms to prevent unauthorized disclosure of CUI during transmission unless otherwise protected by alternative physical safeguards.")
        )
        "ISO27001" -> listOf(
            ComplianceControl("ISO-A.5.1", "ISO27001", "InfoSec Policies", "Information security policies shall be defined, approved by management, published and communicated to employees and relevant external parties."),
            ComplianceControl("ISO-A.9.2.1", "ISO27001", "User Registration", "A formal user registration and de-registration process shall be implemented to enable assignment of access rights."),
            ComplianceControl("ISO-A.9.4.2", "ISO27001", "Secure Log-on", "Where required by the access control policy, access to systems and applications shall be controlled by a secure log-on procedure."),
            ComplianceControl("ISO-A.10.1.1", "ISO27001", "Cryptographic Controls", "A policy on the use of cryptographic controls for protection of information shall be developed and implemented."),
            ComplianceControl("ISO-A.12.4.1", "ISO27001", "Event Logging", "Event logs recording user activities, exceptions, faults and information security events shall be produced, kept and regularly reviewed."),
            ComplianceControl("ISO-A.12.6.1", "ISO27001", "Technical Vulnerability Management", "Information about technical vulnerabilities of information systems being used shall be obtained in a timely fashion, exposure evaluated and appropriate measures taken."),
            ComplianceControl("ISO-A.13.1.1", "ISO27001", "Network Controls", "Networks shall be managed and controlled to protect information in systems and applications."),
            ComplianceControl("ISO-A.18.1.1", "ISO27001", "Legal Identification", "All relevant legislative statutory, regulatory, contractual requirements and the organization’s approach to meet these requirements shall be explicitly identified.")
        )
        "SOC2" -> listOf(
            ComplianceControl("SOC2-CC1.1", "SOC2", "Integrity & Ethics", "The organization demonstrates a commitment to integrity and ethical values through directives, actions, and behavior."),
            ComplianceControl("SOC2-CC6.1", "SOC2", "Logical Access Restriction", "The entity restricts logical access to relevant information assets to authorized users, processes, and devices."),
            ComplianceControl("SOC2-CC6.7", "SOC2", "Data Transmission Control", "The entity restricts the transmission, movement, and removal of information to authorized users and processes, and protects it during transmission."),
            ComplianceControl("SOC2-CC7.1", "SOC2", "Vulnerability Management", "The entity identifies and manages system vulnerabilities to maintain the security of information and systems."),
            ComplianceControl("SOC2-CC7.2", "SOC2", "Security Monitoring", "The entity monitors its system for security events and vulnerabilities, and evaluates the impact of any identified events.")
        )
        else -> emptyList()
    }
}

/** What kind of control weakness a finding is evidence of. */
enum class ControlTheme(val label: String, val controlIds: List<String>) {
    ACCESS(
        "Access restriction",
        listOf("NIST-3.1.1", "ISO-A.9.4.2", "ISO-A.13.1.1", "SOC2-CC6.1")
    ),
    TRANSMISSION(
        "Protection in transit",
        listOf("NIST-3.13.8", "ISO-A.10.1.1", "SOC2-CC6.7")
    ),
    VULNERABILITY(
        "Vulnerability management",
        listOf("NIST-3.11.2", "ISO-A.12.6.1", "SOC2-CC7.1")
    ),
    MONITORING(
        "Security monitoring",
        listOf("NIST-3.12.3", "ISO-A.12.4.1", "SOC2-CC7.2")
    )
}

/**
 * Maps findings to the compliance controls they are evidence against, so checklists can
 * point at real data. The mapping is a suggestion: a control is never marked automatically.
 */
object ComplianceMapper {

    /** First word of a port finding's title ("Telnet exposed (23/tcp)") for unencrypted services. */
    private val cleartextServices = setOf("telnet", "ftp", "http", "tftp", "rsh", "rlogin", "vnc")

    fun themes(tool: String, title: String): Set<ControlTheme> {
        val t = title.lowercase()
        val themes = mutableSetOf<ControlTheme>()
        if ("cve-" in t || "outdated" in t || "end of life" in t || "takeover" in t) themes += ControlTheme.VULNERABILITY
        when (tool) {
            FindingTools.PORT_SCANNER, FindingTools.NMAP -> {
                if ("exposed" in t) {
                    themes += ControlTheme.ACCESS
                    if (t.substringBefore(' ') in cleartextServices) themes += ControlTheme.TRANSMISSION
                }
            }
            FindingTools.TLS, FindingTools.DNS_AUDIT, FindingTools.CAPTIVE_PORTAL -> themes += ControlTheme.TRANSMISSION
            FindingTools.SNMP, FindingTools.UPNP, FindingTools.NFC, FindingTools.USB -> themes += ControlTheme.ACCESS
            FindingTools.WIFI -> when {
                "evil twin" in t -> themes += ControlTheme.MONITORING
                "wps" in t -> themes += ControlTheme.ACCESS
                else -> themes += ControlTheme.TRANSMISSION
            }
            FindingTools.ARP, FindingTools.CELLULAR -> {
                themes += ControlTheme.MONITORING
                themes += ControlTheme.TRANSMISSION
            }
            FindingTools.NETWORK_MONITOR -> {
                themes += ControlTheme.ACCESS
                themes += ControlTheme.MONITORING
            }
            FindingTools.BLE -> themes += if ("tracker" in t) ControlTheme.MONITORING else ControlTheme.ACCESS
            FindingTools.WEB -> themes += when {
                "hsts" in t || "https" in t || "cookie" in t -> ControlTheme.TRANSMISSION
                else -> ControlTheme.VULNERABILITY
            }
            FindingTools.SUBDOMAIN -> themes += ControlTheme.VULNERABILITY
            FindingTools.SHODAN -> themes += if ("cve" in t) ControlTheme.VULNERABILITY else ControlTheme.ACCESS
            FindingTools.CLOUD, FindingTools.BREACH -> themes += ControlTheme.ACCESS
            FindingTools.THREAT_INTEL -> themes += ControlTheme.MONITORING
        }
        return themes
    }

    fun controlIds(tool: String, title: String): List<String> =
        themes(tool, title).flatMap { it.controlIds }.distinct()

    fun controlIds(finding: Finding): List<String> = controlIds(finding.tool, finding.title)

    /**
     * Findings that still count against each control: open ones and accepted risks. Fixed
     * and false-positive findings no longer suggest a gap.
     */
    fun findingsByControl(findings: List<Finding>): Map<String, List<Finding>> {
        val result = mutableMapOf<String, MutableList<Finding>>()
        findings
            .filter { it.status == FindingStatus.OPEN || it.status == FindingStatus.ACCEPTED }
            .forEach { f -> controlIds(f).forEach { id -> result.getOrPut(id) { mutableListOf() } += f } }
        return result.mapValues { (_, list) -> FindingsExporter.sortForReport(list) }
    }
}
