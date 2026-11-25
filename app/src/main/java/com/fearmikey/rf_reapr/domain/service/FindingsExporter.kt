package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.Finding
import com.fearmikey.rf_reapr.domain.model.FindingStatus
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** A host row for engagement exports (from the project's scan sessions). */
data class ExportHost(
    val ip: String,
    val mac: String?,
    val hostname: String?,
    val deviceType: String?,
    val risk: RiskLevel,
    val openPorts: List<Int>,
    val lastSeen: Long?
)

/** An evidence item attached to a finding, as listed in exports (the file itself isn't embedded). */
data class ExportEvidence(
    val fileName: String,
    val capturedAt: Long,
    val sha256: String?,
    val integrity: EvidenceIntegrity,
    val notes: String = ""
)

/** Everything an engagement export contains. */
data class EngagementReport(
    val projectName: String,
    val description: String,
    val scope: String,
    val generatedAt: Long,
    val findings: List<Finding>,
    val hosts: List<ExportHost>,
    val scanCount: Int,
    val logCount: Int,
    val evidenceCount: Int,
    val appVersion: String,
    /** Evidence linked to findings, keyed by finding id. */
    val evidenceByFinding: Map<Long, List<ExportEvidence>> = emptyMap()
)

/**
 * Machine-readable (CSV / JSON) and Markdown exports of findings and engagements.
 * Hand-written serialization: no reflection, so it's unaffected by R8 in release builds.
 */
object FindingsExporter {

    private val CSV_HEADER = listOf(
        "id", "severity", "status", "tool", "target", "title", "detail", "remediation",
        "first_seen", "last_seen", "occurrences", "notes"
    )

    // ---- CSV ---------------------------------------------------------------------------

    fun toCsv(findings: List<Finding>): String = buildString {
        appendLine(CSV_HEADER.joinToString(","))
        sortForReport(findings).forEach { f ->
            appendLine(
                listOf(
                    f.id.toString(), f.severity.name, f.status.name, f.tool, f.target, f.title,
                    f.detail, f.remediation.orEmpty(), iso(f.firstSeen), iso(f.lastSeen),
                    f.occurrences.toString(), f.notes.orEmpty()
                ).joinToString(",") { csvCell(it) }
            )
        }
    }

    fun hostsToCsv(hosts: List<ExportHost>): String = buildString {
        appendLine("ip,mac,hostname,device_type,risk,open_ports,last_seen")
        hosts.forEach { h ->
            appendLine(
                listOf(
                    h.ip, h.mac.orEmpty(), h.hostname.orEmpty(), h.deviceType.orEmpty(), h.risk.name,
                    h.openPorts.joinToString(" "), h.lastSeen?.let(::iso).orEmpty()
                ).joinToString(",") { csvCell(it) }
            )
        }
    }

    /**
     * RFC 4180 quoting, plus neutralizing spreadsheet formula injection: values such as
     * banners, SSIDs or hostnames come from the network and may start with `=`, `+`, `-`, `@`.
     */
    fun csvCell(raw: String): String {
        val value = if (raw.isNotEmpty() && raw[0] in "=+-@\t\r") "'$raw" else raw
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' } || value != value.trim()
        return if (needsQuotes) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    // ---- JSON --------------------------------------------------------------------------

    fun toJson(report: EngagementReport): String = buildString {
        append("{\n")
        append("  \"format\": \"rf-reapr-engagement\",\n")
        append("  \"formatVersion\": 1,\n")
        append("  \"generator\": ").append(jsonString("RF-REAPR ${report.appVersion}")).append(",\n")
        append("  \"generatedAt\": ").append(jsonString(iso(report.generatedAt))).append(",\n")
        append("  \"project\": {\n")
        append("    \"name\": ").append(jsonString(report.projectName)).append(",\n")
        append("    \"description\": ").append(jsonString(report.description)).append(",\n")
        append("    \"scope\": ").append(jsonArray(scopeLines(report.scope).map(::jsonString))).append("\n")
        append("  },\n")
        append("  \"stats\": {")
        append("\"findings\": ${report.findings.size}, \"hosts\": ${report.hosts.size}, ")
        append("\"scans\": ${report.scanCount}, \"logs\": ${report.logCount}, \"evidence\": ${report.evidenceCount}}")
        append(",\n")
        append("  \"findings\": ")
        append(
            jsonArray(
                sortForReport(report.findings).map { f ->
                    val evidence = report.evidenceByFinding[f.id].orEmpty().map { e ->
                        jsonObject(
                            "file" to jsonString(e.fileName),
                            "capturedAt" to jsonString(iso(e.capturedAt)),
                            "sha256" to (e.sha256?.let(::jsonString) ?: "null"),
                            "integrity" to jsonString(e.integrity.name)
                        )
                    }
                    findingJson(f).dropLast(1) +
                        ", \"controls\": " + jsonArray(ComplianceMapper.controlIds(f).map(::jsonString)) +
                        ", \"evidence\": " + jsonArray(evidence) + "}"
                },
                indent = "  "
            )
        )
        append(",\n")
        append("  \"hosts\": ")
        append(jsonArray(report.hosts.map(::hostJson), indent = "  "))
        append("\n}\n")
    }

    private fun findingJson(f: Finding): String = jsonObject(
        "id" to f.id.toString(),
        "severity" to jsonString(f.severity.name),
        "status" to jsonString(f.status.name),
        "tool" to jsonString(f.tool),
        "target" to jsonString(f.target),
        "title" to jsonString(f.title),
        "detail" to jsonString(f.detail),
        "remediation" to (f.remediation?.let(::jsonString) ?: "null"),
        "firstSeen" to jsonString(iso(f.firstSeen)),
        "lastSeen" to jsonString(iso(f.lastSeen)),
        "occurrences" to f.occurrences.toString(),
        "notes" to (f.notes?.let(::jsonString) ?: "null")
    )

    private fun hostJson(h: ExportHost): String = jsonObject(
        "ip" to jsonString(h.ip),
        "mac" to (h.mac?.let(::jsonString) ?: "null"),
        "hostname" to (h.hostname?.let(::jsonString) ?: "null"),
        "deviceType" to (h.deviceType?.let(::jsonString) ?: "null"),
        "risk" to jsonString(h.risk.name),
        "openPorts" to jsonArray(h.openPorts.map { it.toString() }),
        "lastSeen" to (h.lastSeen?.let { jsonString(iso(it)) } ?: "null")
    )

    private fun jsonObject(vararg pairs: Pair<String, String>): String =
        pairs.joinToString(", ", prefix = "{", postfix = "}") { (k, v) -> "${jsonString(k)}: $v" }

    private fun jsonArray(items: List<String>, indent: String? = null): String = when {
        items.isEmpty() -> "[]"
        indent == null -> items.joinToString(", ", prefix = "[", postfix = "]")
        else -> items.joinToString(",\n", prefix = "[\n", postfix = "\n$indent]") { "$indent  $it" }
    }

    fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (c < ' ' || c == ' ' || c == ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }

    // ---- Markdown ----------------------------------------------------------------------

    fun toMarkdown(report: EngagementReport): String = buildString {
        val findings = sortForReport(report.findings)
        appendLine("# ${md(report.projectName)} — Security Assessment")
        appendLine()
        appendLine("_Generated ${human(report.generatedAt)} by RF-REAPR ${md(report.appVersion)}_")
        appendLine()
        if (report.description.isNotBlank()) {
            appendLine(md(report.description))
            appendLine()
        }

        appendLine("## Summary")
        appendLine()
        val open = findings.filter { it.status == FindingStatus.OPEN }
        appendLine("| Severity | Open | Total |")
        appendLine("|---|---:|---:|")
        RiskLevel.entries.reversed().forEach { sev ->
            appendLine("| ${sev.name} | ${open.count { it.severity == sev }} | ${findings.count { it.severity == sev }} |")
        }
        appendLine()
        appendLine("- Hosts observed: ${report.hosts.size}")
        appendLine("- Network scans: ${report.scanCount}")
        appendLine("- Tool log entries: ${report.logCount}")
        appendLine("- Evidence items: ${report.evidenceCount}")
        appendLine()

        appendLine("## Scope")
        appendLine()
        val scope = scopeLines(report.scope)
        if (scope.isEmpty()) appendLine("_No scope was defined for this project._")
        else scope.forEach { appendLine("- `${it.replace("`", "'")}`") }
        appendLine()

        appendLine("## Findings")
        appendLine()
        if (findings.isEmpty()) {
            appendLine("_No findings recorded._")
            appendLine()
        }
        findings.forEachIndexed { i, f ->
            appendLine("### ${i + 1}. [${f.severity.name}] ${md(f.title)}")
            appendLine()
            appendLine("- **Target:** `${f.target.replace("`", "'")}`")
            appendLine("- **Tool:** ${md(f.tool)}")
            appendLine("- **Status:** ${f.status.label}")
            appendLine("- **First / last seen:** ${human(f.firstSeen)} / ${human(f.lastSeen)} (${f.occurrences}×)")
            appendLine()
            appendLine(md(f.detail))
            f.remediation?.let {
                appendLine()
                appendLine("**Remediation:** ${md(it)}")
            }
            f.notes?.takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine("**Auditor notes:** ${md(it)}")
            }
            ComplianceMapper.controlIds(f).takeIf { it.isNotEmpty() }?.let {
                appendLine()
                appendLine("**Related controls:** ${it.joinToString(", ")}")
            }
            report.evidenceByFinding[f.id]?.takeIf { it.isNotEmpty() }?.let { items ->
                appendLine()
                appendLine("**Evidence:**")
                appendLine()
                items.forEach { e ->
                    appendLine("- `${e.fileName.replace("`", "'")}` captured ${human(e.capturedAt)}, SHA-256 `${e.sha256 ?: "not recorded"}` (${e.integrity.label})")
                }
            }
            appendLine()
        }

        if (report.hosts.isNotEmpty()) {
            appendLine("## Hosts")
            appendLine()
            appendLine("| IP | Hostname | MAC | Type | Risk | Open ports |")
            appendLine("|---|---|---|---|---|---|")
            report.hosts.forEach { h ->
                appendLine(
                    "| ${cell(h.ip)} | ${cell(h.hostname)} | ${cell(h.mac)} | ${cell(h.deviceType)} | " +
                        "${h.risk.name} | ${cell(h.openPorts.joinToString(", "))} |"
                )
            }
            appendLine()
        }
    }

    // ---- HTML --------------------------------------------------------------------------

    /** Self-contained HTML (inline CSS, no scripts or remote resources) for viewing or printing. */
    fun toHtml(report: EngagementReport): String = buildString {
        val findings = sortForReport(report.findings)
        val open = findings.filter { it.status == FindingStatus.OPEN }
        appendLine("<!DOCTYPE html>")
        appendLine("<html lang=\"en\"><head><meta charset=\"utf-8\">")
        appendLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        appendLine("<title>${html(report.projectName)} — Security Assessment</title>")
        appendLine(
            "<style>body{font-family:system-ui,sans-serif;max-width:60em;margin:2em auto;padding:0 1em;color:#1b1b1f;line-height:1.45}" +
                "h1{margin-bottom:.2em}h2{border-bottom:1px solid #ccc;padding-bottom:.2em;margin-top:2em}" +
                "table{border-collapse:collapse;width:100%}th,td{border:1px solid #ccc;padding:.35em .6em;text-align:left;vertical-align:top}" +
                "th{background:#f0f0f4}.finding{border:1px solid #ccc;border-left-width:6px;border-radius:4px;padding:.6em 1em;margin:1em 0;page-break-inside:avoid}" +
                ".CRITICAL{border-left-color:#c62828}.HIGH{border-left-color:#ef6c00}.MEDIUM{border-left-color:#c8a000}.LOW{border-left-color:#2e7d32}" +
                ".sev{font-weight:700;font-size:.8em;padding:.1em .5em;border-radius:3px;color:#fff;margin-right:.5em}" +
                ".sev.CRITICAL{background:#c62828}.sev.HIGH{background:#ef6c00}.sev.MEDIUM{background:#c8a000}.sev.LOW{background:#2e7d32}" +
                ".meta{color:#555;font-size:.9em}code{background:#f0f0f4;padding:.05em .3em;border-radius:3px;word-break:break-all}" +
                "pre{white-space:pre-wrap;word-break:break-word;font-family:inherit;margin:.5em 0}</style></head><body>"
        )
        appendLine("<h1>${html(report.projectName)} — Security Assessment</h1>")
        appendLine("<p class=\"meta\">Generated ${human(report.generatedAt)} by RF-REAPR ${html(report.appVersion)}</p>")
        if (report.description.isNotBlank()) appendLine("<p>${html(report.description)}</p>")

        appendLine("<h2>Summary</h2><table><tr><th>Severity</th><th>Open</th><th>Total</th></tr>")
        RiskLevel.entries.reversed().forEach { sev ->
            appendLine("<tr><td>${sev.name}</td><td>${open.count { it.severity == sev }}</td><td>${findings.count { it.severity == sev }}</td></tr>")
        }
        appendLine("</table>")
        appendLine(
            "<p class=\"meta\">Hosts observed: ${report.hosts.size} · Network scans: ${report.scanCount} · " +
                "Tool log entries: ${report.logCount} · Evidence items: ${report.evidenceCount}</p>"
        )

        appendLine("<h2>Scope</h2>")
        val scope = scopeLines(report.scope)
        if (scope.isEmpty()) appendLine("<p><em>No scope was defined for this project.</em></p>")
        else appendLine(scope.joinToString("", "<ul>", "</ul>") { "<li><code>${html(it)}</code></li>" })

        appendLine("<h2>Findings</h2>")
        if (findings.isEmpty()) appendLine("<p><em>No findings recorded.</em></p>")
        findings.forEachIndexed { i, f ->
            appendLine("<div class=\"finding ${f.severity.name}\">")
            appendLine("<h3><span class=\"sev ${f.severity.name}\">${f.severity.name}</span>${i + 1}. ${html(f.title)}</h3>")
            appendLine(
                "<p class=\"meta\">Target <code>${html(f.target)}</code> · ${html(f.tool)} · ${f.status.label} · " +
                    "first ${human(f.firstSeen)}, last ${human(f.lastSeen)} (${f.occurrences}×)</p>"
            )
            if (f.detail.isNotBlank()) appendLine("<pre>${html(f.detail)}</pre>")
            f.remediation?.let { appendLine("<p><strong>Remediation:</strong> ${html(it)}</p>") }
            f.notes?.takeIf { it.isNotBlank() }?.let { appendLine("<p><strong>Auditor notes:</strong> ${html(it)}</p>") }
            ComplianceMapper.controlIds(f).takeIf { it.isNotEmpty() }?.let {
                appendLine("<p class=\"meta\">Related controls: ${html(it.joinToString(", "))}</p>")
            }
            report.evidenceByFinding[f.id]?.takeIf { it.isNotEmpty() }?.let { items ->
                appendLine("<p><strong>Evidence</strong></p><ul>")
                items.forEach { e ->
                    appendLine(
                        "<li><code>${html(e.fileName)}</code> captured ${human(e.capturedAt)}, SHA-256 " +
                            "<code>${html(e.sha256 ?: "not recorded")}</code> (${e.integrity.label})</li>"
                    )
                }
                appendLine("</ul>")
            }
            appendLine("</div>")
        }

        if (report.hosts.isNotEmpty()) {
            appendLine("<h2>Hosts</h2><table><tr><th>IP</th><th>Hostname</th><th>MAC</th><th>Type</th><th>Risk</th><th>Open ports</th></tr>")
            report.hosts.forEach { h ->
                appendLine(
                    "<tr><td>${html(h.ip)}</td><td>${html(h.hostname.orEmpty())}</td><td>${html(h.mac.orEmpty())}</td>" +
                        "<td>${html(h.deviceType.orEmpty())}</td><td>${h.risk.name}</td><td>${html(h.openPorts.joinToString(", "))}</td></tr>"
                )
            }
            appendLine("</table>")
        }
        appendLine("</body></html>")
    }

    /** HTML text/attribute escaping for untrusted values (banners, SSIDs, hostnames). */
    fun html(text: String): String = buildString {
        text.forEach { c ->
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }

    // ---- DefectDojo --------------------------------------------------------------------

    private val cvePattern = Regex("CVE-\\d{4}-\\d{4,}", RegexOption.IGNORE_CASE)
    private val hostPortPattern = Regex("^([A-Za-z0-9._-]+)(?::(\\d{1,5}))?$")

    /**
     * DefectDojo "Generic Findings Import" JSON. Triage state maps onto DefectDojo's flags
     * (active / false_p / risk_accepted / is_mitigated).
     */
    fun toDefectDojoJson(report: EngagementReport): String = buildString {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val items = sortForReport(report.findings).map { f ->
            val pairs = mutableListOf(
                "title" to jsonString(f.title),
                "description" to jsonString(
                    listOf("Target: ${f.target}", "Source: ${f.tool} (RF-REAPR ${report.appVersion})", f.detail, f.notes?.let { "Auditor notes: $it" })
                        .filter { !it.isNullOrBlank() }.joinToString("\n\n")
                ),
                "severity" to jsonString(f.severity.name.lowercase().replaceFirstChar { it.uppercase() }),
                "mitigation" to jsonString(f.remediation.orEmpty()),
                "date" to jsonString(day.format(Date(f.firstSeen))),
                "active" to (f.status == FindingStatus.OPEN).toString(),
                "verified" to "false",
                "false_p" to (f.status == FindingStatus.FALSE_POSITIVE).toString(),
                "risk_accepted" to (f.status == FindingStatus.ACCEPTED).toString(),
                "is_mitigated" to (f.status == FindingStatus.FIXED).toString(),
                "static_finding" to "false",
                "dynamic_finding" to "true",
                "unique_id_from_tool" to jsonString(f.fingerprint),
                "component_name" to jsonString(f.tool),
                "nb_occurences" to f.occurrences.toString()
            )
            cvePattern.find(f.title)?.let { pairs += "cve" to jsonString(it.value.uppercase()) }
            hostPortPattern.find(f.target.trim())?.let { match ->
                val endpoint = mutableListOf("host" to jsonString(match.groupValues[1]))
                match.groupValues[2].toIntOrNull()?.takeIf { it in 1..65535 }?.let { endpoint += "port" to it.toString() }
                pairs += "endpoints" to jsonArray(listOf(jsonObject(*endpoint.toTypedArray())))
            }
            jsonObject(*pairs.toTypedArray())
        }
        append("{\n  \"findings\": ")
        append(jsonArray(items, indent = "  "))
        append("\n}\n")
    }

    // ---- helpers -----------------------------------------------------------------------

    /** Most severe first, then open before triaged, then most recent. */
    fun sortForReport(findings: List<Finding>): List<Finding> = findings.sortedWith(
        compareByDescending<Finding> { it.severity.ordinal }
            .thenBy { if (it.status == FindingStatus.OPEN) 0 else 1 }
            .thenByDescending { it.lastSeen }
    )

    fun scopeLines(scope: String): List<String> =
        scope.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList()

    /** Escape Markdown control characters in untrusted text (banners, hostnames, SSIDs). */
    private fun md(text: String): String = text.replace(Regex("([\\\\`*_{}\\[\\]<>#|])"), "\\\\$1")

    private fun cell(text: String?): String = if (text.isNullOrBlank()) "—" else md(text).replace("\n", " ")

    private fun iso(epochMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(epochMs))

    private fun human(epochMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(epochMs))
}
