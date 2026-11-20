package com.fearmikey.rf_reapr.domain.service

import kotlin.math.abs

data class PingReply(
    val seq: Int,
    val timeMs: Float,
    val ttl: Int? = null,
    /** Responding address (IP, or hostname when only that is printed). */
    val from: String? = null
)

data class PingStats(
    val transmitted: Int,
    val received: Int,
    val errors: Int,
    val minMs: Float?,
    val avgMs: Float?,
    val maxMs: Float?,
    /** Mean absolute difference between consecutive RTTs (RFC 3550-style jitter estimate). */
    val jitterMs: Float?
) {
    val lossPercent: Float
        get() = if (transmitted == 0) 0f else ((transmitted - received).coerceAtLeast(0) * 100f / transmitted)

    val quality: PingQuality
        get() = when {
            received == 0 -> PingQuality.UNREACHABLE
            lossPercent >= 10f || (avgMs ?: 0f) >= 250f -> PingQuality.POOR
            lossPercent > 0f || (avgMs ?: 0f) >= 100f || (jitterMs ?: 0f) >= 30f -> PingQuality.FAIR
            (avgMs ?: 0f) >= 40f -> PingQuality.GOOD
            else -> PingQuality.EXCELLENT
        }
}

enum class PingQuality(val label: String) {
    EXCELLENT("Excellent"), GOOD("Good"), FAIR("Fair"), POOR("Poor"), UNREACHABLE("Unreachable")
}

/**
 * Parses Linux iputils / toybox `ping` output line by line. Pure Kotlin for unit testing.
 */
object PingOutputParser {

    // "64 bytes from 8.8.8.8: icmp_seq=1 ttl=117 time=12.3 ms"
    // "64 bytes from dns.google (8.8.8.8): icmp_seq=1 ttl=117 time=12.3 ms"
    // "64 bytes from 2001:4860::8888: icmp_seq=1 ttl=117 time=0.98 ms"
    private val replyRegex = Regex(
        """bytes from (.+?):? icmp_seq=(\d+)(?:.*?ttl=(\d+))?.*?time[=<]\s*([\d.]+)\s*ms""",
        RegexOption.IGNORE_CASE
    )
    private val parenAddress = Regex("""\(([^)]+)\)""")

    // "From 192.168.1.1 icmp_seq=3 Destination Host Unreachable"
    private val errorRegex = Regex(
        """icmp_seq[= ](\d+).*?(unreachable|exceeded|no answer|timeout|prohibited)""",
        RegexOption.IGNORE_CASE
    )

    // "4 packets transmitted, 3 received, +1 errors, 25% packet loss, time 3004ms"
    private val summaryRegex = Regex("""(\d+) packets transmitted, (\d+) (?:packets )?received""")

    fun parseReply(line: String): PingReply? {
        val m = replyRegex.find(line) ?: return null
        val rawFrom = m.groupValues[1].trim()
        val from = parenAddress.find(rawFrom)?.groupValues?.get(1) ?: rawFrom
        return PingReply(
            seq = m.groupValues[2].toInt(),
            timeMs = m.groupValues[4].toFloatOrNull() ?: return null,
            ttl = m.groupValues[3].toIntOrNull(),
            from = from
        )
    }

    /** Returns the icmp_seq of an error line (unreachable, TTL exceeded...), or null. */
    fun parseErrorSeq(line: String): Int? =
        if (line.contains("bytes from", ignoreCase = true)) null
        else errorRegex.find(line)?.groupValues?.get(1)?.toIntOrNull()

    /** Returns (transmitted, received) from the summary line, or null. */
    fun parseSummary(line: String): Pair<Int, Int>? =
        summaryRegex.find(line)?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }

    /**
     * Computes statistics from the replies seen so far. While a run is in progress (no
     * summary yet), packets sent is inferred from the highest sequence number observed.
     */
    fun computeStats(
        replies: List<PingReply>,
        errorSeqs: Set<Int> = emptySet(),
        summary: Pair<Int, Int>? = null
    ): PingStats {
        val unique = replies.distinctBy { it.seq }.sortedBy { it.seq }
        val times = unique.map { it.timeMs }
        val maxSeq = maxOf(unique.maxOfOrNull { it.seq } ?: 0, errorSeqs.maxOrNull() ?: 0)
        val received = summary?.second ?: unique.size
        val transmitted = summary?.first ?: maxOf(maxSeq, received)
        val jitter = if (times.size >= 2) {
            times.zipWithNext { a, b -> abs(b - a) }.average().toFloat()
        } else null

        return PingStats(
            transmitted = transmitted,
            received = received,
            errors = errorSeqs.size,
            minMs = times.minOrNull(),
            avgMs = if (times.isEmpty()) null else times.average().toFloat(),
            maxMs = times.maxOrNull(),
            jitterMs = jitter
        )
    }

    /**
     * RTT per sequence number for charting: null where a packet was lost. Limited to the
     * most recent [window] sequence numbers.
     */
    fun rttSeries(replies: List<PingReply>, upToSeq: Int, window: Int = 60): List<Float?> {
        if (upToSeq <= 0) return emptyList()
        val bySeq = replies.associateBy({ it.seq }, { it.timeMs })
        val start = maxOf(1, upToSeq - window + 1)
        return (start..upToSeq).map { bySeq[it] }
    }
}
