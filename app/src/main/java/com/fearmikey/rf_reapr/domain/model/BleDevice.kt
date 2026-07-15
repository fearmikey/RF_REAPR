package com.fearmikey.rf_reapr.domain.model

data class BleDevice(
    val name: String?,
    val address: String,
    val rssi: Int,
    val serviceUuids: List<String>,
    val manufacturer: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    val estimatedDistance: Double
        get() = calculateDistance(rssi)

    companion object {
        fun calculateDistance(rssi: Int): Double {
            // Basic Path Loss Model: d = 10 ^ ((Measured Power - RSSI) / (10 * n))
            // Measured power at 1m is typically -59 to -65 dBm. n (path loss exponent) is ~2.0 in free space.
            val txPower = -59.0
            return Math.pow(10.0, (txPower - rssi) / 20.0)
        }
    }
}
