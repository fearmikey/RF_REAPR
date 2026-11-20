package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.FindingDraft
import com.fearmikey.rf_reapr.domain.model.FindingTools
import com.fearmikey.rf_reapr.domain.model.OpenPort
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.domain.repository.AlertSeverity
import com.fearmikey.rf_reapr.domain.repository.ArpAlert
import com.fearmikey.rf_reapr.domain.repository.TlsCipherScannerRepository.TlsScanResult

/**
 * Converts each tool's native result types into [FindingDraft]s for the shared findings
 * store. Titles must be stable across runs (no timestamps/counters) because they're part
 * of the de-duplication fingerprint; variable data goes into the detail.
 */
object FindingAdapters {

    fun upnp(gatewayIp: String?, findings: List<UpnpFinding>): List<FindingDraft> =
        findings.map { FindingDraft(FindingTools.UPNP, gatewayIp ?: "LAN gateway", it.severity, it.title, it.detail) }

    fun snmp(host: String, findings: List<SnmpFinding>): List<FindingDraft> =
        findings.map { FindingDraft(FindingTools.SNMP, host, it.severity, it.title, it.detail) }

    fun nfc(tagId: String, chipType: String, findings: List<NfcFinding>): List<FindingDraft> =
        findings.map { FindingDraft(FindingTools.NFC, "NFC tag $tagId ($chipType)", it.severity, it.title, it.detail) }

    fun usb(device: UsbDeviceInfo, findings: List<UsbFinding>): List<FindingDraft> {
        val label = "USB %04X:%04X".format(device.vendorId, device.productId) +
            (device.product?.let { " $it" } ?: "")
        return findings.map { FindingDraft(FindingTools.USB, label, it.severity, it.title, it.detail) }
    }

    fun cellular(findings: List<CellFinding>): List<FindingDraft> =
        findings.map { FindingDraft(FindingTools.CELLULAR, "Cellular network", it.severity, it.title, it.detail) }

    /** Only actionable checks (FAIL/WARN) become findings. */
    fun dnsAudit(checks: List<DnsCheck>): List<FindingDraft> = checks.mapNotNull { c ->
        val severity = when (c.status) {
            CheckStatus.FAIL -> RiskLevel.HIGH
            CheckStatus.WARN -> RiskLevel.MEDIUM
            else -> return@mapNotNull null
        }
        FindingDraft(
            FindingTools.DNS_AUDIT, "Device DNS resolver", severity, c.title,
            listOf(c.summary, c.detail).filter { it.isNotBlank() }.joinToString("\n")
        )
    }

    fun arp(gatewayIp: String?, alert: ArpAlert): FindingDraft? {
        val severity = when (alert.severity) {
            AlertSeverity.INFO -> return null
            AlertSeverity.WARNING -> RiskLevel.MEDIUM
            AlertSeverity.CRITICAL -> RiskLevel.CRITICAL
        }
        val macs = if (alert.oldMac.isNotBlank() || alert.newMac.isNotBlank()) "MAC ${alert.oldMac} → ${alert.newMac}" else ""
        return FindingDraft(
            FindingTools.ARP, gatewayIp?.ifBlank { null } ?: "Default gateway", severity,
            // Strip the changing MACs out of the title so repeats of the same attack merge.
            alert.message.replace(Regex("([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}"), "").replace(Regex("\\s+"), " ").trim(),
            listOf(alert.detail, macs).filter { it.isNotBlank() }.joinToString("\n"),
            "Verify the gateway's MAC out-of-band, enable Dynamic ARP Inspection / client isolation on the network."
        )
    }

    fun openPorts(host: String, ports: List<OpenPort>): List<FindingDraft> = buildList {
        ports.forEach { p ->
            val info = PortCatalog.info(p.port)
            val risk = info?.exposureRisk
            if (risk != null && info.exposureNote != null) {
                add(
                    FindingDraft(
                        FindingTools.PORT_SCANNER, host, risk,
                        "${info.name} exposed (${p.port}/tcp)",
                        info.exposureNote + (p.banner?.let { "\nBanner: ${it.take(200)}" } ?: ""),
                        "Disable the service or restrict it to trusted management hosts."
                    )
                )
            }
            p.vulnerabilities.filter { it.isConfirmed }.forEach { v ->
                add(
                    FindingDraft(
                        FindingTools.PORT_SCANNER, host, v.severity,
                        "${v.cveId} in ${p.serviceName} (${p.port}/tcp)",
                        v.description,
                        "Update ${p.serviceName} to a version that fixes ${v.cveId}."
                    )
                )
            }
        }
    }

    fun tls(result: TlsScanResult): List<FindingDraft> {
        if (!result.isFinished || result.error != null || result.host.isBlank()) return emptyList()
        val target = "${result.host}:${result.port}"
        return buildList {
            result.protocols.filter { it.enabled && it.riskLevel >= RiskLevel.MEDIUM }.forEach { p ->
                add(
                    FindingDraft(
                        FindingTools.TLS, target, p.riskLevel, "${p.protocol} enabled",
                        p.note ?: "${p.protocol} is deprecated and has known weaknesses.",
                        "Disable ${p.protocol}; support only TLS 1.2 and TLS 1.3."
                    )
                )
            }
            val weak = result.supportedCiphers.filter { it.riskLevel >= RiskLevel.HIGH }
            if (weak.isNotEmpty()) {
                add(
                    FindingDraft(
                        FindingTools.TLS, target, weak.maxOf { it.riskLevel }, "Weak cipher suites accepted",
                        weak.joinToString("\n") { "${it.protocol} ${it.cipherSuite}" + (it.reason?.let { r -> " — $r" } ?: "") },
                        "Remove CBC/RC4/3DES/export/NULL suites; prefer ECDHE with AES-GCM or ChaCha20-Poly1305."
                    )
                )
            }
            result.certificate?.issues?.forEach { issue ->
                add(
                    FindingDraft(
                        FindingTools.TLS, target, issue.severity, "Certificate: ${issue.message}",
                        result.certificate.let { c -> "Subject: ${c.subject}\nIssuer: ${c.issuer}\nExpires in ${c.daysUntilExpiry} days" }
                    )
                )
            }
        }
    }
}
