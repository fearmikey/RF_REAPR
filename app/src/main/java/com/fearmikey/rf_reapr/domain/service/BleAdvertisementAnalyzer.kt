package com.fearmikey.rf_reapr.domain.service

import java.util.Locale
import kotlin.math.pow

enum class BleCategory(val label: String, val isTracker: Boolean = false) {
    APPLE_FIND_MY("Apple Find My / AirTag", true),
    SAMSUNG_SMARTTAG("Samsung SmartTag", true),
    TILE("Tile tracker", true),
    CHIPOLO("Chipolo tracker", true),
    GOOGLE_FIND_MY("Google Find My tag", true),
    APPLE_DEVICE("Apple device"),
    MICROSOFT_DEVICE("Microsoft device"),
    IBEACON("iBeacon"),
    EDDYSTONE("Eddystone beacon"),
    HEART_RATE("Heart-rate / fitness"),
    AUDIO("Audio device"),
    HID("Keyboard / mouse"),
    UNKNOWN("BLE device")
}

enum class BleAddressType(val label: String) {
    PUBLIC("Public"),
    RANDOM_STATIC("Random static"),
    RESOLVABLE_PRIVATE("Private (rotating)"),
    NON_RESOLVABLE("Non-resolvable"),
    UNKNOWN("Unknown")
}

data class BleClassification(
    val category: BleCategory,
    val detail: String? = null
)

/**
 * Classifies BLE advertisements from their manufacturer data and service UUIDs. Pure Kotlin
 * (no android.bluetooth types) so it can be unit tested.
 */
object BleAdvertisementAnalyzer {

    private const val APPLE = 0x004C
    private const val MICROSOFT = 0x0006
    private const val SAMSUNG = 0x0075

    private fun uuid16(u: String): String = u.lowercase(Locale.US).let {
        if (it.length >= 8 && it.endsWith("-0000-1000-8000-00805f9b34fb")) it.substring(4, 8) else it
    }

    /**
     * @param manufacturerData company id -> payload
     * @param serviceUuids full 128-bit UUID strings
     * @param serviceDataUuids UUIDs that carry service data
     */
    fun classify(
        manufacturerData: Map<Int, ByteArray>,
        serviceUuids: List<String>,
        serviceDataUuids: List<String> = emptyList(),
        serviceData: Map<String, ByteArray> = emptyMap()
    ): BleClassification {
        val uuids = (serviceUuids + serviceDataUuids).map(::uuid16).toSet()
        val fmdn = serviceData.entries.firstOrNull { uuid16(it.key) == "feaa" }?.value

        manufacturerData[APPLE]?.let { data ->
            val type = data.firstOrNull()?.toInt()?.and(0xFF)
            return when (type) {
                0x12 -> BleClassification(BleCategory.APPLE_FIND_MY, if (data.size >= 2 && (data[1].toInt() and 0xFF) >= 25) "Separated from owner" else "Near owner")
                0x07 -> BleClassification(BleCategory.APPLE_DEVICE, "AirPods (proximity pairing)")
                0x02 -> {
                    val ib = parseIBeacon(data)
                    BleClassification(BleCategory.IBEACON, ib)
                }
                0x10 -> BleClassification(BleCategory.APPLE_DEVICE, "Nearby info")
                0x0C -> BleClassification(BleCategory.APPLE_DEVICE, "Handoff")
                0x0F -> BleClassification(BleCategory.APPLE_DEVICE, "Nearby action")
                else -> BleClassification(BleCategory.APPLE_DEVICE)
            }
        }
        // Eddystone frames share 0xFEAA with Google's Find My Device Network (frame 0x40/0x41).
        if (fmdn != null) {
            val frame = fmdn.firstOrNull()?.toInt()?.and(0xFF)
            if (frame == 0x40 || frame == 0x41) return BleClassification(BleCategory.GOOGLE_FIND_MY)
            return BleClassification(
                BleCategory.EDDYSTONE,
                when (frame) { 0x00 -> "UID"; 0x10 -> "URL"; 0x20 -> "TLM"; 0x30 -> "EID"; else -> null }
            )
        }
        if ("fd5a" in uuids || ("fd59" in uuids && manufacturerData.containsKey(SAMSUNG))) return BleClassification(BleCategory.SAMSUNG_SMARTTAG)
        if ("feed" in uuids || "feec" in uuids || "fd84" in uuids) return BleClassification(BleCategory.TILE)
        if ("fe33" in uuids || "fe65" in uuids) return BleClassification(BleCategory.CHIPOLO)
        if ("feaa" in uuids) return BleClassification(BleCategory.EDDYSTONE)
        if (manufacturerData.containsKey(MICROSOFT)) return BleClassification(BleCategory.MICROSOFT_DEVICE, "Swift Pair / CDP")
        if ("180d" in uuids || "1816" in uuids || "1814" in uuids) return BleClassification(BleCategory.HEART_RATE)
        if ("1812" in uuids) return BleClassification(BleCategory.HID)
        if ("110b" in uuids || "184e" in uuids || "1850" in uuids || "184f" in uuids || "fe2c" in uuids) return BleClassification(BleCategory.AUDIO)
        return BleClassification(BleCategory.UNKNOWN)
    }

    /** iBeacon: 0x02 0x15 + 16-byte UUID + major + minor + tx power. */
    fun parseIBeacon(data: ByteArray): String? {
        if (data.size < 23 || data[0] != 0x02.toByte() || data[1] != 0x15.toByte()) return null
        val uuid = data.copyOfRange(2, 18).joinToString("") { "%02x".format(it) }
            .let { "${it.substring(0, 8)}-${it.substring(8, 12)}-${it.substring(12, 16)}-${it.substring(16, 20)}-${it.substring(20)}" }
        val major = ((data[18].toInt() and 0xFF) shl 8) or (data[19].toInt() and 0xFF)
        val minor = ((data[20].toInt() and 0xFF) shl 8) or (data[21].toInt() and 0xFF)
        return "$uuid major $major minor $minor"
    }

    /**
     * Address type from the two most significant bits (only meaningful for random addresses;
     * Android doesn't expose public/random before API 35, so OUI-less + bits is a heuristic).
     */
    fun addressType(mac: String, hasKnownOui: Boolean): BleAddressType {
        val first = mac.take(2).toIntOrNull(16) ?: return BleAddressType.UNKNOWN
        if (hasKnownOui) return BleAddressType.PUBLIC
        return when (first and 0xC0) {
            0xC0 -> BleAddressType.RANDOM_STATIC
            0x40 -> BleAddressType.RESOLVABLE_PRIVATE
            0x00 -> BleAddressType.NON_RESOLVABLE
            else -> BleAddressType.UNKNOWN
        }
    }

    /**
     * Log-distance path loss. [advertisedTxPower] is the TX Power Level from the advertisement
     * (power at the antenna); signal at 1 m is roughly 41 dB lower. Without it, -59 dBm at 1 m
     * is assumed. n = 2.0 (free space) to 3.0 (indoors with obstacles).
     */
    fun estimateDistance(rssi: Int, advertisedTxPower: Int? = null, pathLossExponent: Double = 2.5): Double {
        val ref = (advertisedTxPower?.minus(41) ?: -59).coerceIn(-100, 0)
        return 10.0.pow((ref - rssi) / (10.0 * pathLossExponent)).coerceIn(0.1, 100.0)
    }

    /**
     * A tracker that keeps being seen for this long while you're scanning may be travelling
     * with you. Apple/Google use similar windows (10-30 min) for unwanted-tracking alerts.
     */
    const val FOLLOWING_THRESHOLD_MS = 10 * 60 * 1000L

    fun isPossiblyFollowing(category: BleCategory, firstSeen: Long, lastSeen: Long, now: Long): Boolean =
        category.isTracker && lastSeen - firstSeen >= FOLLOWING_THRESHOLD_MS && now - lastSeen < 2 * 60 * 1000L
}
