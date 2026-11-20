package com.fearmikey.rf_reapr.domain.model

import java.util.Date

/**
 * A project doubles as the engagement: evidence, scan sessions, event logs and findings
 * recorded while it is the active project are tagged with its [id].
 */
data class EvidenceProject(
    val id: String,
    val name: String,
    val createdAt: Date = Date(),
    val description: String = "",
    val showGps: Boolean = true,
    val showDate: Boolean = true,
    val showTimestamp: Boolean = true,
    val isDeleted: Boolean = false,
    val deletedAt: Date? = null,
    /** Authorized scope, one entry per line (see ScopeMatcher). Empty = no scope defined. */
    val scope: String = ""
)
