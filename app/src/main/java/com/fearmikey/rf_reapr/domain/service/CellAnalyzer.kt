package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.RiskLevel

enum class CellTech(val label: String, val generation: Int) { GSM("2G GSM", 2), CDMA("2G/3G CDMA", 2), WCDMA("3G WCDMA", 3), TDSCDMA("3G TD-SCDMA", 3), LTE("4G LTE", 4), NR("5G NR", 5) }

/** Android-independent snapshot of one CellInfo. Null = not reported by the modem. */
data class CellSummary(
    val tech: CellTech,
    val registered: Boolean,
    val mcc: String?,
    val mnc: String?,
    val cellId: Long?,
    val areaCode: Int?,
    val physicalId: Int?,
    val channel: Int?,
    val dbm: Int?,
    val level: Int,
    val rsrq: Int? = null,
    val sinr: Int? = null,
    val timingAdvance: Int? = null,
    val bandwidthKhz: Int? = null,
    val operator: String? = null
) {
    val band: String? get() = CellAnalyzer.bandLabel(tech, channel)
    val frequencyMhz: Double? get() = CellAnalyzer.downlinkMhz(tech, channel)
    /** Rough distance to the tower from timing advance (LTE: 78 m per step, GSM: 550 m). */
    val distanceMeters: Int? get() = timingAdvance?.let { ta ->
        when (tech) {
            CellTech.LTE -> ta * 78
            CellTech.GSM -> ta * 550
            else -> null
        }
    }
    val key: String get() = "${tech.name}:${mcc}-${mnc}:${areaCode}:${cellId}:${physicalId}:${channel}"
}

data class CellFinding(val severity: RiskLevel, val title: String, val detail: String)

object CellAnalyzer {

    /** Android reports Integer.MAX_VALUE (or Long.MAX_VALUE) for "unavailable". */
    fun valid(v: Int?): Int? = v?.takeIf { it != Int.MAX_VALUE && it != Int.MIN_VALUE }
    fun valid(v: Long?): Long? = v?.takeIf { it != Long.MAX_VALUE && it != Int.MAX_VALUE.toLong() && it >= 0 }

    // band, F_DL_low (MHz), N_offs, N range end
    private val lteBands = listOf(
        Triple(1, 2110.0, 0) to 599, Triple(2, 1930.0, 600) to 1199, Triple(3, 1805.0, 1200) to 1949,
        Triple(4, 2110.0, 1950) to 2399, Triple(5, 869.0, 2400) to 2649, Triple(7, 2620.0, 2750) to 3449,
        Triple(8, 925.0, 3450) to 3799, Triple(12, 729.0, 5010) to 5179, Triple(13, 746.0, 5180) to 5279,
        Triple(14, 758.0, 5280) to 5379, Triple(17, 734.0, 5730) to 5849, Triple(20, 791.0, 6150) to 6449,
        Triple(25, 1930.0, 8040) to 8689, Triple(26, 859.0, 8690) to 9039, Triple(28, 758.0, 9210) to 9659,
        Triple(30, 2350.0, 9770) to 9869, Triple(38, 2570.0, 37750) to 38249, Triple(40, 2300.0, 38650) to 39649,
        Triple(41, 2496.0, 39650) to 41589, Triple(42, 3400.0, 41590) to 43589, Triple(66, 2110.0, 66436) to 67335,
        Triple(71, 617.0, 68586) to 68935
    )

    private fun lteBand(earfcn: Int) = lteBands.firstOrNull { (b, end) -> earfcn in b.third..end }?.first

    fun bandLabel(tech: CellTech, channel: Int?): String? {
        val ch = channel ?: return null
        return when (tech) {
            CellTech.LTE -> lteBand(ch)?.let { "B${it.first}" }
            CellTech.NR -> downlinkMhz(tech, ch)?.let { mhz ->
                when {
                    mhz in 617.0..652.0 -> "n71"
                    mhz in 729.0..768.0 -> "n12/n14/n28"
                    mhz in 791.0..821.0 -> "n20"
                    mhz in 869.0..894.0 -> "n5"
                    mhz in 1930.0..1995.0 -> "n2/n25"
                    mhz in 2110.0..2200.0 -> "n1/n66"
                    mhz in 2496.0..2690.0 -> "n41"
                    mhz in 3300.0..4200.0 -> "n77/n78"
                    mhz in 24250.0..52600.0 -> "mmWave"
                    else -> null
                }
            }
            else -> null
        }
    }

    fun downlinkMhz(tech: CellTech, channel: Int?): Double? {
        val ch = channel ?: return null
        return when (tech) {
            CellTech.LTE -> lteBand(ch)?.let { b -> b.second + 0.1 * (ch - b.third) }
            CellTech.NR -> when {
                ch < 600000 -> ch * 0.005
                ch < 2016667 -> 3000.0 + (ch - 600000) * 0.015
                else -> 24250.08 + (ch - 2016667) * 0.06
            }
            CellTech.WCDMA -> if (ch in 0..16383) ch / 5.0 else null // UARFCN = 5 × F(MHz)
            CellTech.GSM -> when (ch) {
                in 0..124 -> 935.0 + 0.2 * ch
                in 512..885 -> 1805.2 + 0.2 * (ch - 512)
                in 975..1023 -> 925.2 + 0.2 * (ch - 975)
                in 128..251 -> 869.2 + 0.2 * (ch - 128)
                else -> null
            }
            else -> null
        }
    }

    /**
     * IMSI-catcher / downgrade indicators. None are proof: fake base stations usually force
     * phones onto 2G (no mutual authentication, breakable A5/1 encryption).
     */
    fun findings(cells: List<CellSummary>, servingHistory: List<CellSummary> = emptyList()): List<CellFinding> {
        val out = mutableListOf<CellFinding>()
        val serving = cells.filter { it.registered }
        val neighbours = cells.filter { !it.registered }
        serving.filter { it.tech == CellTech.GSM }.forEach { _ ->
            val betterAvailable = neighbours.any { it.tech.generation >= 4 && (it.dbm ?: -200) > -115 }
            out += CellFinding(
                if (betterAvailable) RiskLevel.HIGH else RiskLevel.MEDIUM,
                "Connected over 2G GSM",
                "2G has no network authentication and weak A5/1 encryption; IMSI catchers force phones onto it." +
                    if (betterAvailable) " A usable 4G/5G cell is visible, so this looks like a downgrade." else " If your area normally has 4G, consider disabling 2G in Settings → Network."
            )
        }
        serving.forEach { s ->
            if (s.areaCode == 0 || s.cellId == 0L) {
                out += CellFinding(RiskLevel.MEDIUM, "Serving cell reports zero LAC/TAC or cell ID", "Real networks don't use 0; some fake base stations do.")
            }
            val sameTechNeighbours = neighbours.filter { it.tech == s.tech && it.areaCode != null }
            if (s.areaCode != null && sameTechNeighbours.size >= 3 && sameTechNeighbours.none { it.areaCode == s.areaCode }) {
                out += CellFinding(
                    RiskLevel.LOW,
                    "Serving cell's area code differs from all neighbours",
                    "TAC/LAC ${s.areaCode} isn't shared by any of ${sameTechNeighbours.size} nearby ${s.tech.label} cells. Fake cells often advertise a new area code to trigger a location update."
                )
            }
            if ((s.dbm ?: -200) > -50) {
                out += CellFinding(RiskLevel.LOW, "Unusually strong serving signal", "${s.dbm} dBm is very strong. Expected right next to a tower or small cell; also typical of a portable fake base station nearby.")
            }
        }
        val distinctServing = servingHistory.map { it.key }.distinct().size
        if (distinctServing >= 6) {
            out += CellFinding(RiskLevel.LOW, "Frequent serving-cell changes", "$distinctServing different serving cells this session. Normal while moving; suspicious if you're stationary.")
        }
        return out.sortedByDescending { it.severity.ordinal }
    }
}
