package com.fearmikey.rf_reapr.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "evidence_folders")
data class EvidenceFolderEntity(
    @PrimaryKey
    val id: String,
    val projectId: String,
    val parentFolderId: String? = null,
    val name: String,
    val createdAt: Long,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null
)
