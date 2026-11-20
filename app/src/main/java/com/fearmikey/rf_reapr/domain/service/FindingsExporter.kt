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
    val appVersion: String
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
        append(jsonArray(sortForReport(report.findings).map(::findingJson), indent = "  "))
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
