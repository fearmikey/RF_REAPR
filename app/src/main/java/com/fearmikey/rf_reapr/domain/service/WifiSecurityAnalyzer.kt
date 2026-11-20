package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.domain.model.WifiAccessPoint

enum class WifiSecurityFamily(val label: String, val rank: Int) {
    OPEN("Open", 0), OWE("Enhanced Open (OWE)", 3), WEP("WEP", 1), WPA("WPA", 2), WPA2("WPA2", 4),
    WPA3("WPA3", 5), ENTERPRISE("Enterprise (802.1X)", 5)
}

data class WifiSecurity(
    val family: WifiSecurityFamily,
    val label: String,
    val risk: RiskLevel,
    val wps: Boolean,
    val issues: List<String>
)

enum class WifiBand(val label: String) { GHZ_2_4("2.4 GHz"), GHZ_5("5 GHz"), GHZ_6("6 GHz"), OTHER("Other") }

data class ChannelAdvice(val band: WifiBand, val bestChannel: Int, val reason: String)

/** Parses Android ScanResult capability strings and spots weak or impersonating networks. */
object WifiSecurityAnalyzer {

    fun band(frequency: Int): WifiBand = when (frequency) {
        in 2400..2500 -> WifiBand.GHZ_2_4
        in 4900..5899 -> WifiBand.GHZ_5
        in 5925..7125 -> WifiBand.GHZ_6
        else -> WifiBand.OTHER
    }

    fun channel(frequency: Int): Int = when {
        frequency == 2484 -> 14
        frequency in 2412..2472 -> (frequency - 2407) / 5
        frequency in 4900..5899 -> (frequency - 5000) / 5
        // 6 GHz (Wi-Fi 6E): channel 1 anchored at 5945 MHz, matching the app's convention.
        frequency in 5945..7125 -> (frequency - 5945) / 5 + 1
        else -> -1
    }

    /** e.g. "[WPA2-PSK-CCMP][RSN-PSK+SAE-CCMP][ESS][WPS]" */
    fun analyze(capabilities: String): WifiSecurity {
        val caps = capabilities.uppercase()
        val wps = "[WPS" in caps
        val issues = mutableListOf<String>()
        val family = when {
            "EAP" in caps || "802.1X" in caps -> WifiSecurityFamily.ENTERPRISE
            "SAE" in caps && "PSK" !in caps -> WifiSecurityFamily.WPA3
            "SAE" in caps -> WifiSecurityFamily.WPA3 // transition mode (WPA2/WPA3)
            "OWE" in caps -> WifiSecurityFamily.OWE
            "RSN" in caps || "WPA2" in caps -> WifiSecurityFamily.WPA2
            "WPA" in caps -> WifiSecurityFamily.WPA
            "WEP" in caps -> WifiSecurityFamily.WEP
            else -> WifiSecurityFamily.OPEN
        }
        val transition = "SAE" in caps && "PSK" in caps
        when (family) {
            WifiSecurityFamily.OPEN -> issues += "No encryption: anyone nearby can read traffic"
            WifiSecurityFamily.WEP -> issues += "WEP can be cracked in minutes"
            WifiSecurityFamily.WPA -> issues += "WPA1 (TKIP) is deprecated and weak"
            else -> Unit
        }
        if ("TKIP" in caps && family != WifiSecurityFamily.WPA && family != WifiSecurityFamily.OPEN) issues += "Allows legacy TKIP cipher"
        if (transition) issues += "WPA2/WPA3 transition mode allows downgrade to WPA2"
        if (wps) issues += "WPS enabled (PIN brute-force / Pixie Dust risk)"
        val risk = when (family) {
            WifiSecurityFamily.OPEN, WifiSecurityFamily.WEP -> RiskLevel.HIGH
            WifiSecurityFamily.WPA -> RiskLevel.MEDIUM
            else -> if (wps) RiskLevel.MEDIUM else if (issues.isNotEmpty()) RiskLevel.LOW else RiskLevel.LOW
        }
        val label = when {
            transition -> "WPA2/WPA3"
            family == WifiSecurityFamily.WPA && ("RSN" in caps || "WPA2" in caps) -> "WPA/WPA2"
            else -> family.label
        }
        return WifiSecurity(family, label, risk, wps, issues)
    }

    /**
     * Evil-twin heuristics (bssid -> reason). Mesh/multi-AP networks legitimately share an
     * SSID, so only mismatches that a real deployment wouldn't have are flagged:
     *  - an open (or weaker-family) AP using the SSID of a secured network
     *  - same SSID with both enterprise and PSK security
     */
    fun findRogues(aps: List<WifiAccessPoint>): Map<String, String> {
        val out = mutableMapOf<String, String>()
        aps.filter { it.ssid.isNotBlank() }.groupBy { it.ssid }.forEach { (ssid, group) ->
            if (group.size < 2) return@forEach
            val withSec = group.map { it to analyze(it.capabilities) }
            val strongest = withSec.maxOf { it.second.family.rank }
            val hasEnterprise = withSec.any { it.second.family == WifiSecurityFamily.ENTERPRISE }
            val hasPsk = withSec.any { it.second.family in setOf(WifiSecurityFamily.WPA2, WifiSecurityFamily.WPA3, WifiSecurityFamily.WPA) }
            withSec.forEach { (ap, sec) ->
                when {
                    sec.family == WifiSecurityFamily.OPEN && strongest >= WifiSecurityFamily.WPA.rank ->
                        out[ap.bssid] = "Open AP using the name of the secured network \"$ssid\": classic evil twin"
                    sec.family == WifiSecurityFamily.WEP && strongest >= WifiSecurityFamily.WPA2.rank ->
                        out[ap.bssid] = "WEP AP sharing the name \"$ssid\" with WPA2/3 APs"
                    hasEnterprise && hasPsk && sec.family != WifiSecurityFamily.ENTERPRISE ->
                        out[ap.bssid] = "PSK AP impersonating enterprise network \"$ssid\" (credential capture risk)"
                }
            }
        }
        return out
    }

    /**
     * Least-congested channel. 2.4 GHz only considers the non-overlapping 1/6/11; others
     * weight each AP by signal (stronger neighbours hurt more).
     */
    fun recommendChannel(aps: List<WifiAccessPoint>, band: WifiBand): ChannelAdvice? {
        val inBand = aps.filter { band(it.frequency) == band }
        val candidates = when (band) {
            WifiBand.GHZ_2_4 -> listOf(1, 6, 11)
            WifiBand.GHZ_5 -> listOf(36, 40, 44, 48, 149, 153, 157, 161, 165)
            WifiBand.GHZ_6 -> listOf(5, 21, 37, 53, 69, 85, 101, 117)
            WifiBand.OTHER -> return null
        }
        fun weight(level: Int) = ((level + 100).coerceAtLeast(1)).toDouble()
        val scores = candidates.associateWith { ch ->
            inBand.sumOf { ap ->
                val apCh = channel(ap.frequency)
                val halfWidthChannels = (ap.bandwidth / 5) / 2
                val overlap = if (band == WifiBand.GHZ_2_4) kotlin.math.abs(apCh - ch) < 5 else kotlin.math.abs(apCh - ch) <= halfWidthChannels
                if (overlap) weight(ap.signalLevel) else 0.0
            }
        }
        val best = scores.minByOrNull { it.value } ?: return null
        val count = inBand.count { ap ->
            val apCh = channel(ap.frequency)
            if (band == WifiBand.GHZ_2_4) kotlin.math.abs(apCh - best.key) < 5 else apCh == best.key
        }
        return ChannelAdvice(band, best.key, if (count == 0) "No overlapping networks" else "$count overlapping network${if (count == 1) "" else "s"}, the fewest in this band")
    }
}
