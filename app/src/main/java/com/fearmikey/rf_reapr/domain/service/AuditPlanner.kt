package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.AuditCheck
import com.fearmikey.rf_reapr.domain.model.AuditConfig
import com.fearmikey.rf_reapr.domain.model.AuditHost
import com.fearmikey.rf_reapr.domain.model.AuditTargets
import com.fearmikey.rf_reapr.domain.model.FindingDraft
import com.fearmikey.rf_reapr.domain.model.RiskLevel

/**
 * Pure planning logic for Audit Mode: dependency resolution, Passive Mode gating, manual
 * target parsing and engagement-scope filtering. No Android dependencies (unit-tested).
 */
object AuditPlanner {

    data class PlannedStep(
        val check: AuditCheck,
        /** Non-null = the step won't run, and this says why. */
        val skipReason: String? = null,
        val autoAdded: Boolean = false
    )

    /**
     * Orders the selected checks, pulls in dependencies (TLS needs the port scan to find
     * TLS services; per-host checks need discovery unless a manual target list is given)
     * and marks what can't run.
     */
    fun plan(config: AuditConfig, passiveMode: Boolean): List<PlannedStep> {
        val manual = config.targets as? AuditTargets.Manual
        val selected = config.checks.toMutableSet()
        val auto = mutableSetOf<AuditCheck>()
        if (AuditCheck.TLS in selected && AuditCheck.PORTS !in selected) {
            selected += AuditCheck.PORTS
            auto += AuditCheck.PORTS
        }
        if (manual == null && selected.any { it.needsHosts } && AuditCheck.DISCOVERY !in selected) {
            selected += AuditCheck.DISCOVERY
            auto += AuditCheck.DISCOVERY
        }
        return AuditCheck.entries.filter { it in selected }.map { check ->
            val reason = when {
                passiveMode && check.active -> "Passive Mode is on"
                manual != null && check == AuditCheck.DISCOVERY -> "Using the manual target list"
                manual != null && check.needsHosts && manual.hosts.isEmpty() -> "No valid targets entered"
                else -> null
            }
            PlannedStep(check, reason, check in auto)
        }
    }

    data class ParsedTargets(val hosts: List<String>, val invalid: List<String>, val truncated: Boolean)

    private val cidrShape = Regex("^(\\d{1,3}(?:\\.\\d{1,3}){3})/(\\d{1,2})$")
    private val rangeShape = Regex("^(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\\.(\\d{1,3})-(\\d{1,3})$")

    /**
     * Parses a manual target list: hosts, IPs, `a.b.c.d/24`..`/32`, or a last-octet
     * range `a.b.c.10-40`, separated by whitespace, commas or semicolons. Wider CIDRs are
     * rejected rather than silently truncated to their network address.
     */
    fun parseTargets(text: String, limit: Int = 256): ParsedTargets {
        val hosts = LinkedHashSet<String>()
        val invalid = mutableListOf<String>()
        var truncated = false
        text.lineSequence()
            .map { it.substringBefore('#') }
            .flatMap { it.split(Regex("[\\s,;]+")) }
            .filter { it.isNotBlank() }
            .forEach { token ->
                val expanded = when {
                    cidrShape.matches(token) -> expandCidr(token)
                    rangeShape.matches(token) -> expandRange(token)
                    else -> HostValidator.normalize(token)?.let { listOf(it.lowercase()) }
                }
                if (expanded == null) {
                    invalid += token
                } else {
                    expanded.forEach { if (hosts.size < limit) hosts += it else truncated = true }
                }
            }
        return ParsedTargets(hosts.toList(), invalid, truncated)
    }

    private fun expandCidr(token: String): List<String>? {
        val (ip, prefixText) = cidrShape.matchEntire(token)!!.destructured
        val prefix = prefixText.toInt()
        if (!HostValidator.isIpv4(ip) || prefix !in 24..32) return null
        val size = 1L shl (32 - prefix)
        val base = ipToLong(ip) and (0xFFFFFFFFL shl (32 - prefix) and 0xFFFFFFFFL)
        // /31 and /32 have no network/broadcast address to skip.
        val offsets = if (prefix >= 31) 0 until size else 1 until size - 1
        return offsets.map { longToIp(base + it) }
    }

    private fun expandRange(token: String): List<String>? {
        val (prefix, fromText, toText) = rangeShape.matchEntire(token)!!.destructured
        val from = fromText.toInt()
        val to = toText.toInt()
        if (from > to || to > 255 || !HostValidator.isIpv4("$prefix.$from")) return null
        return (from..to).map { "$prefix.$it" }
    }

    private fun ipToLong(ip: String): Long = ip.split('.').fold(0L) { acc, part -> (acc shl 8) or part.toLong() }

    private fun longToIp(value: Long): String = (3 downTo 0).joinToString(".") { ((value shr (it * 8)) and 0xFF).toString() }

    /** Splits hosts into (allowed, dropped) using the engagement scope verdict. */
    fun partitionByScope(hosts: List<String>, verdict: (String) -> ScopeMatcher.Verdict): Pair<List<String>, List<String>> =
        hosts.partition { verdict(it) != ScopeMatcher.Verdict.OUT_OF_SCOPE && verdict(it) != ScopeMatcher.Verdict.EXCLUDED }

    /** (host, port) pairs worth a TLS review, in host order, capped at [limit]. */
    fun tlsEndpoints(hosts: List<AuditHost>, limit: Int = 12): List<Pair<String, Int>> =
        hosts.flatMap { h -> h.openPorts.filter { PortCatalog.isTls(it.port, it.serviceName) }.map { h.address to it.port } }
            .take(limit)
}

data class AuditScore(val score: Int, val grade: String, val counts: Map<RiskLevel, Int>) {
    val total: Int get() = counts.values.sum()
}

object AuditScoring {
    private val grades = listOf("A", "B", "C", "D", "F")

    /**
     * 100 minus a weighted penalty per unique finding, mapped to A–F. A critical finding
     * caps the grade at D and a high one at C, so many lows can't hide one serious issue.
     */
    fun score(findings: List<FindingDraft>): AuditScore {
        val unique = findings.distinctBy { Triple(it.tool, it.target, it.title) }
        val counts = RiskLevel.entries.associateWith { level -> unique.count { it.severity == level } }
        val penalty = counts.getValue(RiskLevel.CRITICAL) * 25 + counts.getValue(RiskLevel.HIGH) * 10 +
            counts.getValue(RiskLevel.MEDIUM) * 4 + counts.getValue(RiskLevel.LOW)
        val score = (100 - penalty).coerceIn(0, 100)
        var index = when {
            score >= 90 -> 0
            score >= 75 -> 1
            score >= 60 -> 2
            score >= 40 -> 3
            else -> 4
        }
        if (counts.getValue(RiskLevel.CRITICAL) > 0) index = maxOf(index, 3)
        else if (counts.getValue(RiskLevel.HIGH) > 0) index = maxOf(index, 2)
        return AuditScore(score, grades[index], counts)
    }
}
