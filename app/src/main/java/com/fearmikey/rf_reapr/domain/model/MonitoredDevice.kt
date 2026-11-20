package com.fearmikey.rf_reapr.domain.model

data class MonitoredDevice(
    val ipAddress: String,
    val hostname: String?,
    val macAddress: String? = null,
    /** Joined after the baseline sweep (or changed MAC) and not yet acknowledged. */
    val isNew: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis(),
    val firstSeen: Long = lastSeen,
    val manufacturer: String? = null,
    val isOnline: Boolean = true,
    /** Locally administered (private/randomized) MAC, so the vendor can't be determined. */
    val isRandomizedMac: Boolean = false
) {
    val displayName: String
        get() = hostname?.takeIf { it != ipAddress } ?: manufacturer ?: "Unknown Device"
}

enum class NetworkEventType {
    /** First sweep completed; everything seen is treated as known. */
    BASELINE,
    JOINED,
    LEFT,
    RETURNED,
    /** Same IP now answers with a different MAC (DHCP reassignment or ARP spoofing). */
    MAC_CHANGED
}

data class NetworkEvent(
    val type: NetworkEventType,
    val ipAddress: String?,
    val title: String,
    val detail: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class MonitorStats(
    val sweepCount: Int = 0,
    val lastSweepAt: Long? = null,
    val lastSweepDurationMs: Long? = null
)
