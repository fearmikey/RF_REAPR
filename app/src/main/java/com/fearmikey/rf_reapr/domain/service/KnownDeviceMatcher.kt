package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.FindingDraft
import com.fearmikey.rf_reapr.domain.model.FindingTools
import com.fearmikey.rf_reapr.domain.model.RiskLevel

/** A device the user expects to see on the project's network. */
data class KnownDevice(
    val id: Long = 0,
    val projectId: String?,
    /** Normalized: upper-case, colon-separated. */
    val mac: String,
    val label: String,
    val lastIp: String? = null,
    val notes: String? = null,
    val addedAt: Long
)

/** A device seen on the network, as much as the observing tool knows about it. */
data class ObservedDevice(
    val ip: String,
    val mac: String?,
    val hostname: String? = null,
    val vendor: String? = null
)

enum class DeviceStanding {
    KNOWN,
    UNKNOWN,
    /** No MAC address was learned (e.g. off-subnet host), so it can't be matched. */
    UNIDENTIFIED
}

/**
 * Matches observed devices against a project's known-device list by MAC address, and turns
 * the strangers into findings. Matching is only meaningful once the list has entries; an
 * empty list means the feature isn't in use, not that every device is a rogue.
 */
object KnownDeviceMatcher {

    const val FINDING_TITLE = "Unknown device on the network"

    /** "aa-bb-cc-dd-ee-ff" / "aabb.ccdd.eeff" -> "AA:BB:CC:DD:EE:FF"; null if not a usable MAC. */
    fun normalize(mac: String?): String? {
        val info = mac?.let(MacAddressAnalyzer::parse) ?: return null
        return if (info.isAllZeros || info.isBroadcast) null else info.colonFormat
    }

    fun standing(mac: String?, knownMacs: Set<String>): DeviceStanding {
        val normalized = normalize(mac) ?: return DeviceStanding.UNIDENTIFIED
        return if (normalized in knownMacs) DeviceStanding.KNOWN else DeviceStanding.UNKNOWN
    }

    fun macSet(known: Collection<KnownDevice>): Set<String> = known.map { it.mac }.toSet()

    fun unknown(devices: List<ObservedDevice>, knownMacs: Set<String>): List<ObservedDevice> =
        if (knownMacs.isEmpty()) emptyList() else devices.filter { standing(it.mac, knownMacs) == DeviceStanding.UNKNOWN }

    /**
     * One finding per unknown MAC (the MAC is the target, so the same stranger merges across
     * sweeps even if DHCP hands it a new address). Randomized MACs rate lower: phones rotate
     * them, so on their own they're weak evidence of a rogue device.
     */
    fun findings(devices: List<ObservedDevice>, knownMacs: Set<String>): List<FindingDraft> =
        unknown(devices, knownMacs).mapNotNull { device ->
            val mac = normalize(device.mac) ?: return@mapNotNull null
            val randomized = MacAddressAnalyzer.parse(mac)?.isRandomized == true
            FindingDraft(
                tool = FindingTools.NETWORK_MONITOR,
                target = mac,
                severity = if (randomized) RiskLevel.LOW else RiskLevel.MEDIUM,
                title = FINDING_TITLE,
                detail = listOfNotNull(
                    "IP ${device.ip}",
                    device.hostname?.takeIf { it.isNotBlank() && it != device.ip }?.let { "Hostname $it" },
                    device.vendor?.takeIf { it.isNotBlank() }?.let { "Vendor $it" },
                    if (randomized) "Private (randomized) MAC address: likely a phone or laptop, and it will change over time." else null,
                    "Not on this project's known-device list."
                ).joinToString("\n"),
                remediation = "Identify the device and its owner. If it belongs here, add it to the known devices; " +
                    "otherwise remove it from the network and rotate the Wi-Fi passphrase."
            )
        }

    /** Default label when a device is added without the user naming it. */
    fun defaultLabel(device: ObservedDevice): String =
        device.hostname?.takeIf { it.isNotBlank() && it != device.ip }
            ?: device.vendor?.takeIf { it.isNotBlank() }
            ?: "Device ${device.ip}"
}
