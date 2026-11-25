package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.AuditRun
import com.fearmikey.rf_reapr.domain.model.EventLog
import com.fearmikey.rf_reapr.domain.model.Evidence
import com.fearmikey.rf_reapr.domain.model.Finding
import com.fearmikey.rf_reapr.domain.model.ReportScanSession
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

enum class TimelineKind(val label: String) {
    SCAN("Network scan"),
    LOG("Tool run"),
    FINDING("Finding"),
    EVIDENCE("Evidence"),
    AUDIT("Audit")
}

data class TimelineEvent(
    val timestamp: Long,
    val kind: TimelineKind,
    val title: String,
    val detail: String? = null,
    val severity: RiskLevel? = null
)

/**
 * Merges everything recorded under an engagement into one chronological list: what was
 * run, what it found and what was photographed, in the order it happened.
 */
object TimelineBuilder {

    /** An AUDIT log entry this close to a stored run describes the same audit. */
    private const val AUDIT_LOG_WINDOW_MS = 15_000L

    /** Newest first. */
    fun build(
        logs: List<EventLog>,
        findings: List<Finding>,
        evidence: List<Evidence>,
        scans: List<ReportScanSession>,
        audits: List<AuditRun>
    ): List<TimelineEvent> {
        val events = mutableListOf<TimelineEvent>()

        audits.forEach { run ->
            events += TimelineEvent(
                timestamp = run.finishedAt,
                kind = TimelineKind.AUDIT,
                title = "Audit finished: grade ${run.grade} (${run.score}/100)" + if (run.completed) "" else ", incomplete",
                detail = listOfNotNull(
                    "${run.findings.size} finding${if (run.findings.size == 1) "" else "s"}",
                    "${run.checks.size} check${if (run.checks.size == 1) "" else "s"}",
                    run.networkLabel
                ).joinToString(" · ")
            )
        }
        logs.forEach { log ->
            val duplicatesRun = log.type == "AUDIT" && audits.any { abs(it.finishedAt - log.timestamp) <= AUDIT_LOG_WINDOW_MS }
            if (!duplicatesRun) {
                events += TimelineEvent(log.timestamp, TimelineKind.LOG, log.summary.ifBlank { log.type }, logTypeLabel(log.type))
            }
        }
        scans.forEach { scan ->
            events += TimelineEvent(
                timestamp = scan.timestamp,
                kind = TimelineKind.SCAN,
                title = "Network discovery: ${scan.nodes.size} host${if (scan.nodes.size == 1) "" else "s"}",
                detail = listOfNotNull(scan.networkName, scan.gatewayIp?.let { "gateway $it" }).joinToString(" · ").ifBlank { null }
            )
        }
        findings.forEach { f ->
            events += TimelineEvent(
                timestamp = f.firstSeen,
                kind = TimelineKind.FINDING,
                title = f.title,
                detail = "${f.target} · ${f.tool} · ${f.status.label}" + if (f.occurrences > 1) " · seen ${f.occurrences}×" else "",
                severity = f.severity
            )
        }
        evidence.forEach { e ->
            events += TimelineEvent(
                timestamp = e.timestamp.time,
                kind = TimelineKind.EVIDENCE,
                title = if (e.notes.isBlank()) "Evidence captured" else "Evidence: ${e.notes.lineSequence().first().take(80)}",
                detail = listOfNotNull(
                    e.sha256?.let { "SHA-256 ${EvidenceHasher.short(it)}" },
                    if (e.latitude != null && e.longitude != null) "%.5f, %.5f".format(Locale.US, e.latitude, e.longitude) else null,
                    if (e.findingId != null) "linked to a finding" else null
                ).joinToString(" · ").ifBlank { null }
            )
        }
        return events.sortedByDescending { it.timestamp }
    }

    /** Events bucketed by local calendar day (start-of-day millis), preserving order. */
    fun groupByDay(events: List<TimelineEvent>): List<Pair<Long, List<TimelineEvent>>> {
        val calendar = Calendar.getInstance()
        return events.groupBy { event ->
            calendar.timeInMillis = event.timestamp
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.timeInMillis
        }.toList()
    }

    /** Chronological (oldest first) Markdown, suitable as a report appendix. */
    fun toMarkdown(projectName: String, events: List<TimelineEvent>): String = buildString {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val time = SimpleDateFormat("HH:mm:ss", Locale.US)
        appendLine("# ${escape(projectName)} — Activity timeline")
        appendLine()
        if (events.isEmpty()) {
            appendLine("_No activity recorded._")
            return@buildString
        }
        var currentDay = ""
        events.sortedBy { it.timestamp }.forEach { event ->
            val date = Date(event.timestamp)
            val dayLabel = day.format(date)
            if (dayLabel != currentDay) {
                if (currentDay.isNotEmpty()) appendLine()
                appendLine("## $dayLabel")
                appendLine()
                appendLine("| Time | Type | Event | Detail |")
                appendLine("|---|---|---|---|")
                currentDay = dayLabel
            }
            val title = (event.severity?.let { "[${it.name}] " } ?: "") + event.title
            appendLine("| ${time.format(date)} | ${event.kind.label} | ${cell(title)} | ${cell(event.detail.orEmpty())} |")
        }
    }

    private fun logTypeLabel(type: String): String = when (type) {
        "WIFI" -> "Wi-Fi scan"
        "BLE" -> "Bluetooth scan"
        "PORT" -> "Port scan"
        "WEB" -> "Website inspection"
        "PING" -> "Ping"
        "IPERF" -> "iPerf test"
        "TOPOLOGY" -> "Network discovery"
        "SNMP" -> "SNMP query"
        "AUDIT" -> "Audit Mode"
        "NFC" -> "NFC scan"
        else -> type.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    private fun escape(text: String): String = text.replace("<", "&lt;").replace(">", "&gt;")

    private fun cell(text: String): String = escape(text).replace("|", "\\|").replace(Regex("\\s*\\n\\s*"), " ")
}
