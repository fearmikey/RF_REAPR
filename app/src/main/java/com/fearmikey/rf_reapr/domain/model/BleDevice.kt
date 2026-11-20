package com.fearmikey.rf_reapr.domain.model

import com.fearmikey.rf_reapr.domain.service.BleAddressType
import com.fearmikey.rf_reapr.domain.service.BleAdvertisementAnalyzer
import com.fearmikey.rf_reapr.domain.service.BleCategory

data class BleDevice(
    val name: String?,
    val address: String,
    val rssi: Int,
    val serviceUuids: List<String>,
    val manufacturer: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val firstSeen: Long = timestamp,
    val lastSeen: Long = timestamp,
    /** Advertised TX power (dBm at 1 m), when the device includes it. */
    val txPower: Int? = null,
    val connectable: Boolean? = null,
    val category: BleCategory = BleCategory.UNKNOWN,
    val categoryDetail: String? = null,
    val addressType: BleAddressType = BleAddressType.UNKNOWN,
    val packetCount: Int = 1,
    val isClassic: Boolean = false
) {
    val estimatedDistance: Double
        get() = BleAdvertisementAnalyzer.estimateDistance(rssi, txPower)

    val isTracker: Boolean get() = category.isTracker

    companion object {
        fun calculateDistance(rssi: Int, txPower: Int? = null): Double =
            BleAdvertisementAnalyzer.estimateDistance(rssi, txPower)
    }
}
