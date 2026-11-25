package com.fearmikey.rf_reapr.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One finished Audit Mode run (added in DB v18), kept so runs can be compared over time.
 *
 * [findingsJson] is a snapshot of what that run reported (see AuditRunCodec), independent of
 * the live findings table, so later triage or deletion doesn't rewrite history.
 * [projectId] is "" when no project was active, matching the findings table.
 */
@Entity(tableName = "audit_runs", indices = [Index("projectId")])
data class AuditRunEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(defaultValue = "")
    val projectId: String = "",
    val startedAt: Long,
    val finishedAt: Long,
    val networkLabel: String? = null,
    val grade: String,
    val score: Int,
    /** False when the run was cancelled or a check failed, i.e. results may be partial. */
    val completed: Boolean,
    /** Comma-separated AuditCheck names that finished. */
    val checks: String,
    val hostCount: Int,
    val findingsJson: String,
    val reportMarkdown: String
)
