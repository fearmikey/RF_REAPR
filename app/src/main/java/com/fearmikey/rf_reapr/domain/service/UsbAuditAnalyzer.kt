package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.RiskLevel

data class UsbInterfaceInfo(val interfaceClass: Int, val subclass: Int, val protocol: Int, val endpointCount: Int)

data class UsbDeviceInfo(
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val manufacturer: String?,
    val product: String?,
    val deviceClass: Int,
    val interfaces: List<UsbInterfaceInfo>
)

data class UsbFinding(val severity: RiskLevel, val title: String, val detail: String)

/** Classifies USB interfaces and flags BadUSB / implant indicators. Pure Kotlin. */
object UsbAuditAnalyzer {

    fun className(c: Int, sub: Int = 0, proto: Int = 0): String = when (c) {
        0x00 -> "Per-interface"
        0x01 -> "Audio"
        0x02 -> "Communications (CDC)"
        0x03 -> when {
            sub == 1 && proto == 1 -> "HID keyboard"
            sub == 1 && proto == 2 -> "HID mouse"
            else -> "HID"
        }
        0x05 -> "Physical"
        0x06 -> "Still image (PTP)"
        0x07 -> "Printer"
        0x08 -> "Mass storage"
        0x09 -> "Hub"
        0x0A -> "CDC data"
        0x0B -> "Smart card"
        0x0D -> "Content security"
        0x0E -> "Video"
        0x0F -> "Personal healthcare"
        0x10 -> "Audio/Video"
        0xDC -> "Diagnostic"
        0xE0 -> if (sub == 1 && proto == 3) "RNDIS network" else if (sub == 1 && proto == 1) "Bluetooth" else "Wireless controller"
        0xEF -> "Miscellaneous"
        0xFE -> "Application specific (DFU)"
        0xFF -> "Vendor specific"
        else -> "Class 0x%02X".format(c)
    }

    private val vendors = mapOf(
        0x05AC to "Apple", 0x046D to "Logitech", 0x045E to "Microsoft", 0x04E8 to "Samsung", 0x18D1 to "Google",
        0x0781 to "SanDisk", 0x0951 to "Kingston", 0x058F to "Alcor", 0x090C to "Silicon Motion", 0x13FE to "Phison",
        0x0BDA to "Realtek", 0x0B95 to "ASIX", 0x2109 to "VIA Labs", 0x1A86 to "QinHeng (CH340)", 0x10C4 to "Silicon Labs",
        0x0403 to "FTDI", 0x067B to "Prolific", 0x0BB4 to "HTC", 0x22B8 to "Motorola", 0x12D1 to "Huawei", 0x2717 to "Xiaomi",
        0x03EB to "Atmel", 0x2341 to "Arduino", 0x2A03 to "Arduino", 0x16C0 to "Van Ooijen (Teensy/V-USB)", 0x16D0 to "MCS (Digispark)",
        0x1B4F to "SparkFun", 0x2E8A to "Raspberry Pi", 0x0483 to "STMicroelectronics", 0x239A to "Adafruit", 0x1209 to "pid.codes (open hardware)",
        0x0BDB to "Ericsson", 0x1D6B to "Linux Foundation", 0x0525 to "Netchip (Linux gadget)"
    )

    fun vendorName(vid: Int): String? = vendors[vid]

    /** Boards commonly used for HID-injection attacks (Rubber Ducky clones, Digispark, etc.). */
    private val attackBoards = mapOf(
        (0x16D0 to 0x0753) to "Digispark (ATtiny85) – popular cheap BadUSB",
        (0x2341 to 0x8036) to "Arduino Leonardo (HID-capable)",
        (0x2341 to 0x8037) to "Arduino Micro (HID-capable)",
        (0x1B4F to 0x9205) to "SparkFun Pro Micro (used by Malduino)",
        (0x1B4F to 0x9206) to "SparkFun Pro Micro (used by Malduino)",
        (0x16C0 to 0x0482) to "Teensy (keyboard/mouse/joystick)",
        (0x16C0 to 0x0486) to "Teensy (raw HID)",
        (0x16C0 to 0x0487) to "Teensy (serial + keyboard)",
        (0x16C0 to 0x27DB) to "V-USB keyboard (DIY HID)",
        (0x03EB to 0x2403) to "Atmel AT90USB (classic Rubber Ducky firmware)",
        (0x0483 to 0x5740) to "STM32 virtual COM (Flipper Zero and others)",
        (0x2E8A to 0x000A) to "Raspberry Pi Pico (can run BadUSB scripts)",
        (0x2E8A to 0x0003) to "Raspberry Pi RP2040 bootloader",
        (0x1D6B to 0x0104) to "Linux USB gadget (P4wnP1, Bash Bunny-style implants)"
    )

    fun findings(d: UsbDeviceInfo): List<UsbFinding> {
        val out = mutableListOf<UsbFinding>()
        val ifaces = d.interfaces
        val keyboard = ifaces.any { it.interfaceClass == 0x03 && it.subclass == 1 && it.protocol == 1 }
        val hid = ifaces.any { it.interfaceClass == 0x03 }
        val storage = ifaces.any { it.interfaceClass == 0x08 }
        val network = ifaces.any { (it.interfaceClass == 0xE0 && it.subclass == 1 && it.protocol == 3) || (it.interfaceClass == 0x02 && it.subclass in setOf(6, 0x0D)) }
        val serial = ifaces.any { it.interfaceClass == 0x02 && it.subclass == 2 } || d.vendorId in setOf(0x1A86, 0x10C4, 0x0403, 0x067B)
        val label = listOfNotNull(d.manufacturer, d.product, d.name).joinToString(" ").lowercase()
        val claimsNonInput = Regex("(flash|disk|drive|storage|stick|charg|cable|adapter|hub|card reader|ethernet|lan)").containsMatchIn(label) &&
            !Regex("(keyboard|keypad|mouse|combo)").containsMatchIn(label)

        attackBoards[d.vendorId to d.productId]?.let {
            out += UsbFinding(RiskLevel.HIGH, "Known HID-injection board", "$it (VID:PID %04X:%04X). These are commonly flashed with keystroke-injection payloads.".format(d.vendorId, d.productId))
        }
        if (keyboard && storage) {
            out += UsbFinding(RiskLevel.HIGH, "Keyboard + mass storage composite", "A single device acting as both a keyboard and a drive is the signature of USB Rubber Ducky / BadUSB style tools.")
        }
        if (keyboard && claimsNonInput) {
            out += UsbFinding(RiskLevel.HIGH, "Unexpected keyboard interface", "\"${d.product ?: d.name}\" doesn't look like an input device but can type keystrokes.")
        } else if (keyboard && out.none { it.severity == RiskLevel.HIGH }) {
            out += UsbFinding(RiskLevel.LOW, "Keyboard interface", "Normal for real keyboards. Verify this is the device you expect; a hidden keyboard can type commands.")
        }
        if (network) {
            out += UsbFinding(
                if (storage || hid) RiskLevel.HIGH else RiskLevel.MEDIUM,
                "USB network adapter",
                "Presents an RNDIS/ECM network interface. Hosts may route traffic through it automatically; implants such as LAN Turtle / Bash Bunny use this to intercept traffic."
            )
        }
        if (hid && ifaces.size >= 3 && !keyboard && d.deviceClass != 0x09) {
            out += UsbFinding(RiskLevel.LOW, "Multi-interface HID", "Composite device with ${ifaces.size} interfaces including HID.")
        }
        if (serial) {
            out += UsbFinding(RiskLevel.LOW, "Serial / debug interface", "USB-serial bridge. Common on dev boards, SDR/radio gear and router console cables.")
        }
        if (ifaces.any { it.interfaceClass == 0xFE && it.subclass == 1 }) {
            out += UsbFinding(RiskLevel.MEDIUM, "Firmware update (DFU) mode", "The device accepts firmware over USB. Unsigned DFU allows reflashing it with malicious firmware.")
        }
        return out.sortedByDescending { it.severity.ordinal }
    }
}
