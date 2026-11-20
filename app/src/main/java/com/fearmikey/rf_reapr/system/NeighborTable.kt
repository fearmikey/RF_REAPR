package com.fearmikey.rf_reapr.system

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Reads the kernel neighbour (ARP) table. Android 10+ blocks /proc/net/arp and Android 11+
 * blocks `ip neigh` for normal apps, so callers must handle [Snapshot.available] = false.
 */
object NeighborTable {

    data class Snapshot(val entries: Map<String, String>, val available: Boolean, val source: String)

    private val macRegex = Regex("^([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}$")

    fun read(): Snapshot {
        readProc()?.let { return Snapshot(it, true, "/proc/net/arp") }
        readIpNeigh()?.let { return Snapshot(it, true, "ip neigh") }
        return Snapshot(emptyMap(), false, "unavailable")
    }

    fun macFor(ip: String): String? = read().entries[ip]

    private fun readProc(): Map<String, String>? = try {
        val file = File("/proc/net/arp")
        if (!file.canRead()) null
        else file.bufferedReader().useLines { lines ->
            lines.drop(1).mapNotNull { line ->
                val cols = line.trim().split(Regex("\\s+"))
                val mac = cols.getOrNull(3)?.lowercase()
                if (cols.size >= 4 && mac != null && macRegex.matches(mac) && mac != "00:00:00:00:00:00") cols[0] to mac else null
            }.toMap()
        }
    } catch (_: Exception) {
        null
    }

    private fun readIpNeigh(): Map<String, String>? = try {
        val process = ProcessBuilder("ip", "neigh", "show").redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroy()
        if (process.exitValue() != 0) null
        else parseIpNeigh(output).takeIf { it.isNotEmpty() || output.isBlank() }
    } catch (_: Exception) {
        null
    }

    /** `192.168.1.1 dev wlan0 lladdr aa:bb:cc:dd:ee:ff REACHABLE` -> ip to mac. */
    fun parseIpNeigh(output: String): Map<String, String> = output.lineSequence().mapNotNull { line ->
        val parts = line.trim().split(Regex("\\s+"))
        val idx = parts.indexOf("lladdr")
        val mac = parts.getOrNull(idx + 1)?.lowercase()
        if (idx > 0 && mac != null && macRegex.matches(mac) && !line.contains("FAILED")) parts[0] to mac else null
    }.toMap()
}
