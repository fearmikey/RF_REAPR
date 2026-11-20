package com.fearmikey.rf_reapr.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A persisted security finding (added in DB v17).
 *
 * Findings are de-duplicated per project by [fingerprint] (tool + target + title), so
 * re-running a scan bumps [lastSeen]/[occurrences] instead of creating duplicates.
 * [projectId] is "" (not null) when no project was active, so the unique index still
 * de-duplicates those rows (SQLite treats NULLs as distinct in unique indexes).
 */
@Entity(
    tableName = "findings",
    indices = [Index(value = ["projectId", "fingerprint"], unique = true)]
)
data class FindingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(defaultValue = "")
    val projectId: String = "",
    val fingerprint: String,
    val tool: String,
    val target: String,
    val severity: String,
    val title: String,
    val detail: String,
    val remediation: String? = null,
    val status: String,
    val firstSeen: Long,
    val lastSeen: Long,
    val occurrences: Int = 1,
    val notes: String? = null
)
