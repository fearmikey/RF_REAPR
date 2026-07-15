package com.fearmikey.rf_reapr.domain.model

data class MonitoredDevice(
    val ipAddress: String,
    val hostname: String?,
    val macAddress: String? = null,
    val isNew: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis()
)
