package com.fearmikey.rf_reapr.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "evidence")
data class EvidenceEntity(
    @PrimaryKey
    val id: String,
    val filePath: String,
    val timestamp: Long,
    val latitude: Double?,
    val longitude: Double?,
    val controlId: String?,
    val projectId: String?,
    val folderId: String?,
    val notes: String,
    val hasStego: Boolean = false,
    val stegoType: String? = null,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    /** SHA-256 (lower-case hex) of the file as captured; null for items saved before v18 until backfilled. */
    val sha256: String? = null,
    /** The finding this item documents (findings.id), if any. Added in v18. */
    val findingId: Long? = null
)
