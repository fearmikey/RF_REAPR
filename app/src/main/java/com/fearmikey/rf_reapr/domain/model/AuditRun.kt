package com.fearmikey.rf_reapr.domain.model

/** What one audit run reported about one issue (a frozen copy, independent of later triage). */
data class AuditRunFinding(
    val fingerprint: String,
    val tool: String,
    val target: String,
    val severity: RiskLevel,
    val title: String
)

/** A finished Audit Mode run kept for history and run-to-run comparison. */
data class AuditRun(
    val id: Long = 0,
    /** Owning project, or null when no project was active. */
    val projectId: String?,
    val startedAt: Long,
    val finishedAt: Long,
    val networkLabel: String?,
    val grade: String,
    val score: Int,
    /** False when the run was cancelled or a check failed (results may be partial). */
    val completed: Boolean,
    /** Checks that actually finished in this run. */
    val checks: Set<AuditCheck>,
    val hostCount: Int,
    val findings: List<AuditRunFinding>,
    val reportMarkdown: String
)
