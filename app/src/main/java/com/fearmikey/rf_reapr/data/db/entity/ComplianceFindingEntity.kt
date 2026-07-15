package com.fearmikey.rf_reapr.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "compliance_findings")
data class ComplianceFindingEntity(
    @PrimaryKey
    val controlId: String, // e.g., "NIST-3.1.1"
    val frameworkId: String,
    val status: String, // Maps to ComplianceStatus enum name
    val notes: String,
    val lastUpdated: Long = System.currentTimeMillis()
)
