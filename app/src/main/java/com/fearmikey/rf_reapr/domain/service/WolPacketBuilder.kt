package com.fearmikey.rf_reapr.domain.service

/**
 * Builds Wake-on-LAN "magic packets": 6 × 0xFF, the target MAC repeated 16 times, and an
 * optional 4- or 6-byte SecureOn password. Pure Kotlin for unit testing.
 */
object WolPacketBuilder {

    fun build(mac: MacAddressAnalyzer.MacInfo, secureOn: ByteArray? = null): ByteArray {
        require(secureOn == null || secureOn.size == 4 || secureOn.size == 6) { "SecureOn password must be 4 or 6 bytes" }
        val macBytes = ByteArray(6) { i -> mac.normalized.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        val packet = ByteArray(6 + 16 * 6 + (secureOn?.size ?: 0))
        for (i in 0 until 6) packet[i] = 0xFF.toByte()
        for (rep in 0 until 16) System.arraycopy(macBytes, 0, packet, 6 + rep * 6, 6)
        secureOn?.copyInto(packet, 6 + 16 * 6)
        return packet
    }

    /**
     * Parses a SecureOn password written as 6 hex bytes (AA:BB:CC:DD:EE:FF) or 4 bytes
     * (hex or dotted decimal like 192.168.1.1). Blank = no password.
     */
    fun parseSecureOn(text: String): Result<ByteArray?> {
        val t = text.trim()
        if (t.isEmpty()) return Result.success(null)
        SubnetCalculator.parseIpv4(t)?.let { ip ->
            return Result.success(ByteArray(4) { i -> ((ip ushr (24 - i * 8)) and 0xFF).toByte() })
        }
        val hex = MacAddressAnalyzer.cleanHex(t)
            ?: return Result.failure(IllegalArgumentException("Password must be hex bytes"))
        if (hex.length != 8 && hex.length != 12) return Result.failure(IllegalArgumentException("Password must be 4 or 6 bytes"))
        return Result.success(ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() })
    }
}
