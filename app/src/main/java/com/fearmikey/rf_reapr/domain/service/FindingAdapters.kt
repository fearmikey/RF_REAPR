package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.FindingDraft
import com.fearmikey.rf_reapr.domain.model.FindingTools
import com.fearmikey.rf_reapr.domain.model.OpenPort
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.domain.model.WifiAccessPoint
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

    /**
     * Wi-Fi posture: weak encryption / WPS / legacy modes on the *connected* network, plus
     * evil-twin suspects (grouped per SSID so repeat scans with new BSSIDs merge).
     * Neighbouring networks' own weak security isn't the user's finding, so it's ignored.
     */
    fun wifi(aps: List<WifiAccessPoint>): List<FindingDraft> = buildList {
        aps.firstOrNull { it.isConnected }?.let { ap ->
            val target = wifiTarget(ap.ssid)
            val sec = WifiSecurityAnalyzer.analyze(ap.capabilities)
            val weakFamily = sec.family in setOf(WifiSecurityFamily.OPEN, WifiSecurityFamily.WEP, WifiSecurityFamily.WPA)
            if (weakFamily) {
                add(
                    FindingDraft(
                        FindingTools.WIFI, target, sec.risk, "Connected Wi-Fi uses ${sec.family.label}",
                        sec.issues.first() + "\nBSSID ${ap.bssid}" + (ap.vendor?.let { " ($it)" } ?: ""),
                        "Switch the network to WPA3-Personal (or WPA2-AES at minimum) with a strong passphrase."
                    )
                )
            }
            if (sec.wps) {
                add(
                    FindingDraft(
                        FindingTools.WIFI, target, RiskLevel.MEDIUM, "WPS enabled on connected Wi-Fi",
                        "The access point advertises Wi-Fi Protected Setup, whose PIN mode can be brute-forced offline (Pixie Dust).",
                        "Disable WPS in the router's wireless settings."
                    )
                )
            }
            // Remaining issues (TKIP allowed, WPA2/WPA3 transition) are hardening items.
            val legacy = sec.issues.drop(if (weakFamily) 1 else 0).filterNot { it.startsWith("WPS") }
            if (legacy.isNotEmpty()) {
                add(
                    FindingDraft(
                        FindingTools.WIFI, target, RiskLevel.LOW, "Connected Wi-Fi allows legacy security modes",
                        legacy.joinToString("\n"),
                        "Disable TKIP and, once all clients support it, WPA2/WPA3 transition mode."
                    )
                )
            }
        }
        aps.filter { it.isRogueSuspect }.groupBy { it.ssid }.forEach { (ssid, suspects) ->
            add(
                FindingDraft(
                    FindingTools.WIFI, wifiTarget(ssid), RiskLevel.HIGH, "Possible evil twin of \"$ssid\"",
                    suspects.joinToString("\n") { ap ->
                        ap.bssid + (ap.vendor?.let { " ($it)" } ?: "") + " ch ${ap.getChannel()} ${ap.signalLevel} dBm: " +
                            (ap.rogueReason ?: "suspicious security mismatch")
                    },
                    "Confirm which BSSIDs belong to the real deployment; enable rogue-AP detection / 802.11w (PMF) on the controller."
                )
            )
        }
    }

    private fun wifiTarget(ssid: String) = if (ssid.isBlank()) "Hidden Wi-Fi network" else "Wi-Fi \"$ssid\""

    /** Only interception / portals / proxies become findings; a clean or offline result doesn't. */
    fun captivePortal(networkLabel: String?, verdict: PortalVerdict): List<FindingDraft> {
        val target = networkLabel?.ifBlank { null }?.let { "$it network" } ?: "Active network"
        val (severity, remediation) = when (verdict.status) {
            PortalStatus.INTERCEPTED -> RiskLevel.HIGH to
                "Treat the network as hostile: use a VPN and don't enter credentials until the interception is explained."
            PortalStatus.CAPTIVE_PORTAL -> RiskLevel.LOW to
                "Portals see unencrypted traffic before sign-in; use HTTPS/VPN and verify the portal belongs to the venue."
            PortalStatus.PARTIAL -> if (verdict.title.contains("proxy", ignoreCase = true)) {
                RiskLevel.LOW to "HTTP traffic is visible to the proxy operator; prefer HTTPS or a VPN."
            } else {
                return emptyList()
            }
            PortalStatus.OPEN, PortalStatus.OFFLINE -> return emptyList()
        }
        return listOf(
            FindingDraft(
                FindingTools.CAPTIVE_PORTAL, target, severity, verdict.title,
                verdict.detail + (verdict.loginUrl?.let { "\nPortal: $it" } ?: ""), remediation
            )
        )
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
