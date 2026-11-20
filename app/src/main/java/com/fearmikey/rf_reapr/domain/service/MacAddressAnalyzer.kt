package com.fearmikey.rf_reapr.domain.service

/**
 * Pure-Kotlin parsing and classification of IEEE 802 MAC addresses.
 *
 * Accepts the common notations (`AA:BB:CC:DD:EE:FF`, `AA-BB-CC-DD-EE-FF`,
 * Cisco `aabb.ccdd.eeff`, and bare `AABBCCDDEEFF`) and decodes the I/G (multicast) and
 * U/L (locally administered) bits of the first octet.
 */
object MacAddressAnalyzer {

    data class MacInfo(
        /** 12 uppercase hex digits, no separators. */
        val normalized: String
    ) {
        private val firstOctet: Int = normalized.substring(0, 2).toInt(16)

        /** First 6 hex digits (the Organizationally Unique Identifier). */
        val oui: String get() = normalized.substring(0, 6)

        /** I/G bit set: a group (multicast) address rather than a single interface. */
        val isMulticast: Boolean get() = firstOctet and 0x01 != 0

        /** U/L bit set: locally administered, i.e. not vendor-assigned. */
        val isLocallyAdministered: Boolean get() = firstOctet and 0x02 != 0

        val isBroadcast: Boolean get() = normalized == "FFFFFFFFFFFF"

        val isAllZeros: Boolean get() = normalized == "000000000000"

        /**
         * Unicast + locally administered. This is what modern phones/laptops use for MAC
         * randomization ("Private Wi-Fi address"), so OUI vendor lookup doesn't apply.
         */
        val isRandomized: Boolean get() = isLocallyAdministered && !isMulticast

        val colonFormat: String get() = normalized.chunked(2).joinToString(":")
        val dashFormat: String get() = normalized.chunked(2).joinToString("-")
        val ciscoFormat: String get() = normalized.lowercase().chunked(4).joinToString(".")
        val ouiColonFormat: String get() = oui.chunked(2).joinToString(":")
    }

    private val separators = Regex("[\\s:.\\-]")
    private val hexOnly = Regex("^[0-9A-F]+$")

    private val macPattern = Regex(
        "(?i)(?<![0-9a-f])(?:" +
            "(?:[0-9a-f]{2}[:-]){5}[0-9a-f]{2}" + // AA:BB:CC:DD:EE:FF / AA-BB-...
            "|(?:[0-9a-f]{4}\\.){2}[0-9a-f]{4}" + // aabb.ccdd.eeff
            "|[0-9a-f]{12}" + // AABBCCDDEEFF
            ")(?![0-9a-f])"
    )

    /** Strips separators and uppercases; returns null if anything but hex remains. */
    fun cleanHex(input: String): String? {
        val cleaned = input.replace(separators, "").uppercase()
        return cleaned.takeIf { it.isNotEmpty() && hexOnly.matches(it) }
    }

    /** Parses a complete MAC address, or returns null if [input] isn't one. */
    fun parse(input: String): MacInfo? {
        val hex = cleanHex(input) ?: return null
        return if (hex.length == 12) MacInfo(hex) else null
    }

    /**
     * Returns the 6-hex-digit OUI for a full MAC *or* a partial prefix of at least 6 hex
     * digits (e.g. "B8:27:EB"), or null if there aren't enough valid hex digits.
     */
    fun ouiOf(input: String): String? {
        val hex = cleanHex(input) ?: return null
        return if (hex.length in 6..12) hex.substring(0, 6) else null
    }

    /** Finds every distinct MAC address in free text (ARP tables, DHCP leases, logs...). */
    fun extractAll(text: String): List<MacInfo> =
        macPattern.findAll(text)
            .mapNotNull { parse(it.value) }
            .distinctBy { it.normalized }
            .toList()
}
