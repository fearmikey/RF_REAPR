package com.fearmikey.rf_reapr.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.fearmikey.rf_reapr.domain.model.DeviceType
import com.fearmikey.rf_reapr.domain.model.OpenPort
import com.fearmikey.rf_reapr.domain.model.RiskLevel

@Entity(tableName = "network_nodes")
data class NetworkNodeEntity(
    @PrimaryKey
    val ipAddress: String,
    val macAddress: String? = null,
    val hostname: String? = null,
    val riskLevel: RiskLevel = RiskLevel.LOW,
    val lastSeen: Long = System.currentTimeMillis(),
    val openPorts: List<OpenPort> = emptyList(),
    val deviceType: DeviceType = DeviceType.UNKNOWN,
    val parentId: String? = null
)
