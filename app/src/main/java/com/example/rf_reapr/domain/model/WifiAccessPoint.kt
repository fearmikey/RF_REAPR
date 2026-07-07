package com.example.rf_reapr.domain.model

data class WifiAccessPoint(
    val ssid: String,
    val bssid: String,
    val signalLevel: Int, // RSSI
    val frequency: Int,
    val bandwidth: Int, // in MHz
    val capabilities: String, // Encryption etc.
    val isRogueSuspect: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun getChannel(): Int {
        return when {
            frequency == 2484 -> 14
            frequency in 2412..2472 -> (frequency - 2412) / 5 + 1
            frequency in 5170..5825 -> {
                // 5GHz channels are 20MHz apart, but the center frequency can be any of them
                // This is a simplified calculation for common 5GHz channels
                (frequency - 5000) / 5
            }
            frequency in 5945..7105 -> (frequency - 5945) / 5 + 1
            else -> -1
        }
    }
}
