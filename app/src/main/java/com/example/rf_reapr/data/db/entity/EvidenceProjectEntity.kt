package com.example.rf_reapr.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "evidence_projects")
data class EvidenceProjectEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val createdAt: Long,
    val description: String,
    val showGps: Boolean = true,
    val showDate: Boolean = true,
    val showTimestamp: Boolean = true,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null
)
