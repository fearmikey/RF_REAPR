package com.example.rf_reapr.domain.model

data class BleDevice(
    val name: String?,
    val address: String,
    val rssi: Int,
    val serviceUuids: List<String>,
    val timestamp: Long = System.currentTimeMillis()
)
