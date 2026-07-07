package com.example.rf_reapr.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "event_logs")
data class EventLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String, // WIFI, BLE, PORT, WEB, PING, TOPOLOGY
    val timestamp: Long = System.currentTimeMillis(),
    val summary: String,
    val detailJson: String
)
