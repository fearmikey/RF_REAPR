package com.example.rf_reapr.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "evidence_folders")
data class EvidenceFolderEntity(
    @PrimaryKey
    val id: String,
    val projectId: String,
    val name: String,
    val createdAt: Long,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null
)
