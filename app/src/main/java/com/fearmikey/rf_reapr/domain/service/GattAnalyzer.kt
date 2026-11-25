package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.FindingDraft
import com.fearmikey.rf_reapr.domain.model.FindingTools
import com.fearmikey.rf_reapr.domain.model.RiskLevel

data class GattCharacteristicInfo(
    val uuid: String,
    /** Bitmask of the BluetoothGattCharacteristic.PROPERTY_* flags. */
    val properties: Int,
    /** Value read from the device (only attempted for standard, readable information characteristics). */
    val value: ByteArray? = null
) {
    val name: String get() = GattCatalog.characteristicName(uuid)
    val isReadable: Boolean get() = properties and GattCatalog.PROPERTY_READ != 0
    val isWritable: Boolean
        get() = properties and (GattCatalog.PROPERTY_WRITE or GattCatalog.PROPERTY_WRITE_NO_RESPONSE or GattCatalog.PROPERTY_SIGNED_WRITE) != 0

    override fun equals(other: Any?): Boolean =
        other is GattCharacteristicInfo && uuid == other.uuid && properties == other.properties && value.contentEquals(other.value)

    override fun hashCode(): Int = 31 * (31 * uuid.hashCode() + properties) + (value?.contentHashCode() ?: 0)
}

data class GattServiceInfo(val uuid: String, val characteristics: List<GattCharacteristicInfo>) {
    val name: String get() = GattCatalog.serviceName(uuid)
    val isStandard: Boolean get() = GattCatalog.shortUuid(uuid) != null
}

data class GattFinding(val severity: RiskLevel, val title: String, val detail: String, val remediation: String)

/** Names for Bluetooth SIG-assigned (and a few widely used vendor) GATT UUIDs. */
object GattCatalog {
    const val PROPERTY_BROADCAST = 0x01
    const val PROPERTY_READ = 0x02
    const val PROPERTY_WRITE_NO_RESPONSE = 0x04
    const val PROPERTY_WRITE = 0x08
    const val PROPERTY_NOTIFY = 0x10
    const val PROPERTY_INDICATE = 0x20
    const val PROPERTY_SIGNED_WRITE = 0x40

    private const val BASE_SUFFIX = "-0000-1000-8000-00805f9b34fb"

    const val NORDIC_UART = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
    const val NORDIC_LEGACY_DFU = "00001530-1212-efde-1523-785feabcd123"
    const val TI_OAD = "f000ffc0-0451-4000-b000-000000000000"

    private val services = mapOf(
        "1800" to "Generic Access", "1801" to "Generic Attribute", "1802" to "Immediate Alert",
        "1803" to "Link Loss", "1804" to "Tx Power", "1805" to "Current Time",
        "1808" to "Glucose", "1809" to "Health Thermometer", "180a" to "Device Information",
        "180d" to "Heart Rate", "180f" to "Battery", "1810" to "Blood Pressure",
        "1812" to "Human Interface Device", "1813" to "Scan Parameters", "1814" to "Running Speed and Cadence",
        "1816" to "Cycling Speed and Cadence", "1818" to "Cycling Power", "1819" to "Location and Navigation",
        "181a" to "Environmental Sensing", "181c" to "User Data", "181d" to "Weight Scale",
        "1820" to "Internet Protocol Support", "1822" to "Pulse Oximeter", "1826" to "Fitness Machine",
        "183b" to "Binary Sensor", "184e" to "Audio Stream Control", "fe59" to "Nordic Secure DFU",
        "fd6f" to "Exposure Notification", "fe2c" to "Google Fast Pair", "fd5a" to "Samsung SmartTag",
        "feed" to "Tile", "fe9f" to "Google"
    )

    private val vendorServices = mapOf(
        NORDIC_UART to "Nordic UART (serial)",
        NORDIC_LEGACY_DFU to "Nordic Legacy DFU",
        TI_OAD to "TI Over-the-Air Download"
    )

    private val characteristics = mapOf(
        "2a00" to "Device Name", "2a01" to "Appearance", "2a04" to "Preferred Connection Parameters",
        "2a05" to "Service Changed", "2a19" to "Battery Level", "2a23" to "System ID",
        "2a24" to "Model Number", "2a25" to "Serial Number", "2a26" to "Firmware Revision",
        "2a27" to "Hardware Revision", "2a28" to "Software Revision", "2a29" to "Manufacturer Name",
        "2a2a" to "Regulatory Certification", "2a50" to "PnP ID", "2a37" to "Heart Rate Measurement",
        "2a38" to "Body Sensor Location", "2a4a" to "HID Information", "2a4b" to "Report Map",
        "2a4d" to "Report", "2a06" to "Alert Level", "2a07" to "Tx Power Level", "2a6e" to "Temperature",
        "2a6f" to "Humidity", "2aa6" to "Central Address Resolution"
    )

    /** "180a" for a UUID on the Bluetooth base UUID, else null (vendor-specific). */
    fun shortUuid(uuid: String): String? {
        val u = uuid.lowercase()
        return if (u.length == 36 && u.startsWith("0000") && u.endsWith(BASE_SUFFIX)) u.substring(4, 8) else null
    }

    fun serviceName(uuid: String): String =
        shortUuid(uuid)?.let { services[it] ?: "Service 0x${it.uppercase()}" }
            ?: vendorServices[uuid.lowercase()]
            ?: "Vendor-specific service"

    fun characteristicName(uuid: String): String =
        shortUuid(uuid)?.let { characteristics[it] ?: "Characteristic 0x${it.uppercase()}" } ?: "Vendor-specific characteristic"

    fun describeProperties(properties: Int): List<String> = buildList {
        if (properties and PROPERTY_READ != 0) add("Read")
        if (properties and PROPERTY_WRITE != 0) add("Write")
        if (properties and PROPERTY_WRITE_NO_RESPONSE != 0) add("Write without response")
        if (properties and PROPERTY_SIGNED_WRITE != 0) add("Signed write")
        if (properties and PROPERTY_NOTIFY != 0) add("Notify")
        if (properties and PROPERTY_INDICATE != 0) add("Indicate")
        if (properties and PROPERTY_BROADCAST != 0) add("Broadcast")
    }

    /** Services whose readable characteristics are safe, standard device information. */
    val INFO_SERVICES = setOf("1800", "180a", "180f")
}

/**
 * Interprets a read-only GATT enumeration. RF-REAPR never writes to a device, so findings
 * describe what an unpaired connection is *offered*; whether a write would be rejected for
 * lack of encryption can't be known without attempting one, and the wording says so.
 */
object GattAnalyzer {

    /** Printable text for string characteristics, otherwise spaced hex. */
    fun decodeValue(uuid: String, bytes: ByteArray?): String? {
        if (bytes == null) return null
        if (bytes.isEmpty()) return "(empty)"
        when (GattCatalog.shortUuid(uuid)) {
            "2a19" -> return "${bytes[0].toInt() and 0xFF}%"
            "2a50" -> if (bytes.size >= 7) {
                val vendor = (bytes[1].toInt() and 0xFF) or ((bytes[2].toInt() and 0xFF) shl 8)
                val product = (bytes[3].toInt() and 0xFF) or ((bytes[4].toInt() and 0xFF) shl 8)
                return "Vendor 0x%04X, product 0x%04X".format(vendor, product)
            }
        }
        val text = runCatching { String(bytes, Charsets.UTF_8) }.getOrNull()?.trimEnd('\u0000')
        return if (!text.isNullOrEmpty() && text.all { it >= ' ' && it != '�' && it.code != 0x7F }) text
        else bytes.joinToString(" ") { "%02X".format(it) }
    }

    fun findings(services: List<GattServiceInfo>, bonded: Boolean): List<GattFinding> {
        if (bonded || services.isEmpty()) return emptyList()
        val findings = mutableListOf<GattFinding>()
        val uuids = services.map { it.uuid.lowercase() }

        val dfu = services.filter {
            GattCatalog.shortUuid(it.uuid) == "fe59" || it.uuid.lowercase() in setOf(GattCatalog.NORDIC_LEGACY_DFU, GattCatalog.TI_OAD)
        }
        if (dfu.isNotEmpty()) {
            findings += GattFinding(
                RiskLevel.MEDIUM, "Firmware update service reachable without pairing",
                dfu.joinToString("\n") { "${it.name} (${it.uuid})" } +
                    "\nThe device offers over-the-air firmware update to an unpaired connection. Unless images are signed, anyone in radio range could replace the firmware.",
                "Confirm the vendor enforces signed firmware images, or require bonding before the update service is exposed."
            )
        }
        if (GattCatalog.NORDIC_UART in uuids) {
            findings += GattFinding(
                RiskLevel.MEDIUM, "Serial (UART) service reachable without pairing",
                "Nordic UART service (${GattCatalog.NORDIC_UART}) is offered to an unpaired connection. These often carry a debug console or the device's control protocol.",
                "Require an encrypted, bonded link for the serial service or disable it in production firmware."
            )
        }

        val writable = services.filter { !it.isStandard }
            .flatMap { s -> s.characteristics.filter { it.isWritable }.map { s to it } }
        if (writable.isNotEmpty()) {
            findings += GattFinding(
                RiskLevel.LOW, "Writable vendor characteristics offered without pairing",
                writable.take(12).joinToString("\n") { (s, c) ->
                    "${c.uuid} in ${s.uuid} (${GattCatalog.describeProperties(c.properties).joinToString()})"
                } + (if (writable.size > 12) "\n… and ${writable.size - 12} more" else "") +
                    "\nNo pairing was needed to connect and list these. The device may still reject writes on an unencrypted link; that was not tested because RF-REAPR does not write to devices.",
                "Check that control characteristics require an encrypted (bonded) connection."
            )
        }

        val serial = services.flatMap { it.characteristics }.firstOrNull { GattCatalog.shortUuid(it.uuid) == "2a25" && it.value?.isNotEmpty() == true }
        if (serial != null) {
            findings += GattFinding(
                RiskLevel.LOW, "Device serial number readable without pairing",
                "Serial Number characteristic returned \"${decodeValue(serial.uuid, serial.value)}\" to an unpaired connection. A fixed, readable identifier lets the device be tracked even if it randomizes its address.",
                "Restrict the Device Information service to bonded connections or omit the serial number."
            )
        }
        return findings
    }

    fun toDrafts(deviceLabel: String, address: String, findings: List<GattFinding>): List<FindingDraft> =
        findings.map {
            FindingDraft(FindingTools.BLE, "BLE $address" + if (deviceLabel.isNotBlank()) " ($deviceLabel)" else "", it.severity, it.title, it.detail, it.remediation)
        }
}
