package com.fearmikey.rf_reapr.data.db.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "session_node_cross_ref",
    primaryKeys = ["sessionId", "ipAddress"],
    indices = [Index(value = ["ipAddress"])]
)
data class SessionNodeCrossRef(
    val sessionId: Long,
    val ipAddress: String
)
