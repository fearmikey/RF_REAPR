package com.fearmikey.rf_reapr.domain.service

/**
 * Pure logic for the opt-in background network watch: compares a sweep against the
 * per-network baseline and reports devices never seen before.
 *
 * Devices are keyed by MAC when known (survives DHCP address changes), else by IP.
 */
object NetworkWatchEvaluator {

    data class SeenDevice(val ip: String, val mac: String?, val hostname: String?) {
        val key: String get() = mac?.lowercase()?.takeIf { it.isNotBlank() && it != "00:00:00:00:00:00" }?.let { "mac:$it" } ?: "ip:$ip"
    }

    data class Result(
        /** True when this network had no baseline yet; the sweep becomes the baseline silently. */
        val firstSweep: Boolean,
        val newDevices: List<SeenDevice>,
        val updatedBaseline: Set<String>
    )

    fun evaluate(baseline: Set<String>?, sweep: List<SeenDevice>): Result {
        val keys = sweep.map { it.key }.toSet()
        if (baseline == null) return Result(firstSweep = true, newDevices = emptyList(), updatedBaseline = keys)
        val fresh = sweep.filter { it.key !in baseline }.distinctBy { it.key }
        return Result(firstSweep = false, newDevices = fresh, updatedBaseline = baseline + keys)
    }

    /** Baselines are stored per network so moving between networks doesn't alert on everything. */
    fun networkKey(gatewayIp: String?, prefixLength: Int, localIp: String): String =
        "${gatewayIp ?: "nogw"}/${prefixLength}@${localIp.substringBeforeLast('.')}"
}
