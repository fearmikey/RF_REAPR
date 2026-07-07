package com.example.rf_reapr.data.db.entity

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
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null
)
