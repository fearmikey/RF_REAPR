package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.AuditCheck
import com.fearmikey.rf_reapr.domain.model.AuditRun
import com.fearmikey.rf_reapr.domain.model.AuditRunFinding
import com.fearmikey.rf_reapr.domain.model.FindingDraft
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * (De)serializes the per-run findings snapshot stored in audit_runs.findingsJson. Uses Gson's
 * tree API rather than reflection so it behaves the same in minified release builds.
 */
object AuditRunCodec {

    /** One entry per distinct issue, keeping the most severe report of it. */
    fun snapshot(drafts: List<FindingDraft>): List<AuditRunFinding> =
        drafts.filter { it.title.isNotBlank() && it.target.isNotBlank() }
            .groupBy { FindingMerger.fingerprint(it) }
            .map { (fingerprint, group) ->
                val worst = group.maxBy { it.severity.ordinal }
                AuditRunFinding(fingerprint, worst.tool.trim(), worst.target.trim(), worst.severity, worst.title.trim())
            }

    fun encode(findings: List<AuditRunFinding>): String {
        val array = JsonArray()
        findings.forEach { f ->
            array.add(JsonObject().apply {
                addProperty("fp", f.fingerprint)
                addProperty("tool", f.tool)
                addProperty("target", f.target)
                addProperty("severity", f.severity.name)
                addProperty("title", f.title)
            })
        }
        return array.toString()
    }

    /** Tolerant: malformed input or entries yield an empty/shorter list instead of throwing. */
    fun decode(json: String): List<AuditRunFinding> = runCatching {
        JsonParser.parseString(json).asJsonArray.mapNotNull { element ->
            runCatching {
                val o = element.asJsonObject
                AuditRunFinding(
                    fingerprint = o.get("fp").asString,
                    tool = o.get("tool").asString,
                    target = o.get("target").asString,
                    severity = RiskLevel.entries.firstOrNull { it.name == o.get("severity").asString } ?: RiskLevel.LOW,
                    title = o.get("title").asString
                )
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    fun encodeChecks(checks: Collection<AuditCheck>): String = checks.joinToString(",") { it.name }

    fun decodeChecks(value: String): Set<AuditCheck> =
        value.split(',').mapNotNull { name -> AuditCheck.entries.firstOrNull { it.name == name.trim() } }.toSet()
}

/**
 * Compares two audit runs of the same project: what is new, what went away, what is still
 * there. A finding only counts as resolved if the check that produced it ran again; findings
 * from checks the later run skipped are reported separately as not re-tested.
 */
object AuditRunDiff {

    data class Result(
        val previous: AuditRun,
        val latest: AuditRun,
        val newFindings: List<AuditRunFinding>,
        val resolved: List<AuditRunFinding>,
        val persisting: List<AuditRunFinding>,
        /** In the earlier run, but its check didn't run (or didn't finish) this time. */
        val notRetested: List<AuditRunFinding>
    ) {
        val scoreDelta: Int get() = latest.score - previous.score
        val gradeChanged: Boolean get() = latest.grade != previous.grade
        val hasChanges: Boolean get() = newFindings.isNotEmpty() || resolved.isNotEmpty() || gradeChanged

        /** e.g. "2 resolved, 1 new, grade C → B". */
        fun summary(): String = buildList {
            if (resolved.isNotEmpty()) add("${resolved.size} resolved")
            if (newFindings.isNotEmpty()) add("${newFindings.size} new")
            if (persisting.isNotEmpty()) add("${persisting.size} still present")
            if (notRetested.isNotEmpty()) add("${notRetested.size} not re-tested")
            add(if (gradeChanged) "grade ${previous.grade} → ${latest.grade}" else "grade ${latest.grade} unchanged")
        }.joinToString(", ")
    }

    private val severityFirst = compareByDescending<AuditRunFinding> { it.severity.ordinal }.thenBy { it.target }.thenBy { it.title }

    fun compare(previous: AuditRun, latest: AuditRun): Result {
        val before = previous.findings.associateBy { it.fingerprint }
        val after = latest.findings.associateBy { it.fingerprint }
        val retestedTools = latest.checks.map { it.tool }.toSet()
        val gone = before.values.filter { it.fingerprint !in after }
        return Result(
            previous = previous,
            latest = latest,
            newFindings = after.values.filter { it.fingerprint !in before }.sortedWith(severityFirst),
            resolved = gone.filter { it.tool in retestedTools }.sortedWith(severityFirst),
            persisting = after.values.filter { it.fingerprint in before }.sortedWith(severityFirst),
            notRetested = gone.filter { it.tool !in retestedTools }.sortedWith(severityFirst)
        )
    }

    /** Markdown section appended to an exported run report. */
    fun markdown(diff: Result): String = buildString {
        appendLine("## Changes since the previous audit")
        appendLine()
        appendLine("- **Posture grade:** ${diff.previous.grade} (${diff.previous.score}) → ${diff.latest.grade} (${diff.latest.score})")
        appendLine("- **Summary:** ${diff.summary()}")
        appendLine()
        fun section(title: String, items: List<AuditRunFinding>) {
            if (items.isEmpty()) return
            appendLine("### $title")
            appendLine()
            items.forEach { appendLine("- [${it.severity.name}] ${it.title.replace("<", "&lt;").replace(">", "&gt;")} — `${it.target.replace("`", "'")}`") }
            appendLine()
        }
        section("Resolved", diff.resolved)
        section("New", diff.newFindings)
        section("Still present", diff.persisting)
        section("Not re-tested", diff.notRetested)
    }
}
