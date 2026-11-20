package com.fearmikey.rf_reapr.domain.service

/**
 * Compares the host sets of two network scan sessions (baseline vs. latest).
 */
object ScanDiff {

    data class Result(
        val added: List<String>,
        val removed: List<String>,
        val unchanged: List<String>
    ) {
        val hasChanges: Boolean get() = added.isNotEmpty() || removed.isNotEmpty()
    }

    fun compare(baseline: Collection<String>, latest: Collection<String>): Result {
        val before = baseline.toSet()
        val after = latest.toSet()
        return Result(
            added = sortIps(after - before),
            removed = sortIps(before - after),
            unchanged = sortIps(after intersect before)
        )
    }

    /** IPv4 addresses in numeric order, anything else after them alphabetically. */
    fun sortIps(ips: Collection<String>): List<String> = ips.sortedWith(
        compareBy<String> { if (HostValidator.isIpv4(it)) 0 else 1 }
            .thenBy { ip -> if (HostValidator.isIpv4(ip)) ip.split('.').fold(0L) { a, o -> (a shl 8) or o.toLong() } else 0L }
            .thenBy { it }
    )
}
