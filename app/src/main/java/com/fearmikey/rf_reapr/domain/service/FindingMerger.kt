package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.Finding
import com.fearmikey.rf_reapr.domain.model.FindingDraft
import com.fearmikey.rf_reapr.domain.model.FindingStatus

/**
 * Pure merge rules for recording tool findings into the persistent store:
 *
 * - Findings are identified by [fingerprint] (tool + target + title, normalized), so a
 *   re-scan updates the existing row instead of duplicating it.
 * - A re-seen finding refreshes severity/detail, bumps lastSeen and occurrences.
 * - A finding previously marked FIXED that shows up again is a regression and is
 *   re-opened. ACCEPTED and FALSE_POSITIVE triage decisions are kept.
 */
object FindingMerger {

    data class Plan(
        val inserts: List<Finding>,
        val updates: List<Finding>,
        /** Previously FIXED findings that re-appeared. */
        val reopened: List<Finding>
    ) {
        val isEmpty: Boolean get() = inserts.isEmpty() && updates.isEmpty()
    }

    private val whitespace = Regex("\\s+")

    fun fingerprint(tool: String, target: String, title: String): String =
        listOf(tool, target, title).joinToString("|") { it.trim().lowercase().replace(whitespace, " ") }

    fun fingerprint(draft: FindingDraft): String = fingerprint(draft.tool, draft.target, draft.title)

    /**
     * @param existing current findings for [projectId] keyed by fingerprint (only the ones
     *   matching the drafts are needed).
     */
    fun plan(
        projectId: String?,
        drafts: List<FindingDraft>,
        existing: Map<String, Finding>,
        now: Long
    ): Plan {
        // Collapse duplicates within one batch, keeping the most severe report.
        val unique = drafts
            .filter { it.title.isNotBlank() && it.target.isNotBlank() }
            .groupBy { fingerprint(it) }
            .mapValues { (_, group) -> group.maxBy { it.severity.ordinal } }

        val inserts = mutableListOf<Finding>()
        val updates = mutableListOf<Finding>()
        val reopened = mutableListOf<Finding>()

        unique.forEach { (fp, draft) ->
            val prior = existing[fp]
            if (prior == null) {
                inserts += Finding(
                    projectId = projectId,
                    fingerprint = fp,
                    tool = draft.tool.trim(),
                    target = draft.target.trim(),
                    severity = draft.severity,
                    title = draft.title.trim(),
                    detail = draft.detail.trim(),
                    remediation = draft.remediation?.trim()?.ifBlank { null },
                    status = FindingStatus.OPEN,
                    firstSeen = now,
                    lastSeen = now,
                    occurrences = 1
                )
            } else {
                val regression = prior.status == FindingStatus.FIXED
                val updated = prior.copy(
                    severity = draft.severity,
                    detail = draft.detail.trim(),
                    remediation = draft.remediation?.trim()?.ifBlank { null } ?: prior.remediation,
                    status = if (regression) FindingStatus.OPEN else prior.status,
                    lastSeen = now,
                    occurrences = prior.occurrences + 1
                )
                updates += updated
                if (regression) reopened += updated
            }
        }
        return Plan(inserts, updates, reopened)
    }
}
