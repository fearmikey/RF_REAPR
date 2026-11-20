package com.fearmikey.rf_reapr.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_sessions")
data class ScanSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val networkName: String? = null,
    val gatewayIp: String? = null,
    /** Added in DB v17: project that was active when the scan ran (null = none). */
    val projectId: String? = null
)
