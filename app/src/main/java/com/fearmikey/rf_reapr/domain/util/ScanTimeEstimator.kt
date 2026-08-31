package com.fearmikey.rf_reapr.domain.util

/**
 * Produces rough low/high second estimates for how long a given workflow step will take.
 *
 * These are heuristic ranges intended to set user expectations before starting a
 * potentially long-running scan - actual duration depends on how many hosts respond,
 * network conditions, router load, etc. Estimates are refined once real data (subnet
 * host count, discovered device count) becomes available.
 */
object ScanTimeEstimator {

    data class EstimateRange(val lowSeconds: Int, val highSeconds: Int) {
        val label: String
            get() {
                val low = formatSeconds(lowSeconds)
                val high = formatSeconds(highSeconds)
                return if (low == high) low else "$low\u2013$high"
            }

        companion object {
            private fun formatSeconds(seconds: Int): String {
                if (seconds < 60) return "${seconds}s"
                val minutes = seconds / 60
                val remainingSeconds = seconds % 60
                return if (remainingSeconds == 0) "${minutes}m" else "${minutes}m ${remainingSeconds}s"
            }
        }
    }

    private const val DISCOVERY_CONCURRENCY = 50
    private const val DISCOVERY_LOW_PER_ROUND = 0.3
    private const val DISCOVERY_HIGH_PER_ROUND = 3.3

    private const val PORT_SCAN_LOW_PER_DEVICE = 0.5
    private const val PORT_SCAN_HIGH_PER_DEVICE = 2.0

    // Default assumptions used before we know the real subnet size / device count
    // (a typical home/office /24 network with modest device density).
    private const val DEFAULT_HOST_COUNT = 254
    private const val DEFAULT_DEVICE_COUNT = 15

    /**
     * Estimate for a given workflow step id.
     *
     * @param hostCount number of live IPs to probe in the local subnet (used by
     * `net_disc`). Defaults to a /24-sized network when unknown.
     * @param deviceCount number of devices discovered so far (used by steps that operate
     * per-device, like `port_scan`). Defaults to a reasonable guess when unknown.
     */
    fun estimateFor(stepId: String, hostCount: Int? = null, deviceCount: Int? = null): EstimateRange {
        return when (stepId) {
            "net_disc" -> {
                val hosts = hostCount ?: DEFAULT_HOST_COUNT
                val rounds = kotlin.math.ceil(hosts.toDouble() / DISCOVERY_CONCURRENCY).coerceAtLeast(1.0)
                EstimateRange(
                    lowSeconds = (rounds * DISCOVERY_LOW_PER_ROUND).toInt().coerceAtLeast(1),
                    highSeconds = (rounds * DISCOVERY_HIGH_PER_ROUND).toInt().coerceAtLeast(2)
                )
            }
            "port_scan" -> {
                val devices = deviceCount ?: DEFAULT_DEVICE_COUNT
                EstimateRange(
                    lowSeconds = (devices * PORT_SCAN_LOW_PER_DEVICE).toInt().coerceAtLeast(1),
                    highSeconds = (devices * PORT_SCAN_HIGH_PER_DEVICE).toInt().coerceAtLeast(2)
                )
            }
            "svc_disc" -> EstimateRange(15, 16) // fixed 15s discovery window
            "dns_audit" -> EstimateRange(2, 5)
            "upnp_audit" -> EstimateRange(5, 6) // fixed 5s SSDP window
            "wifi_scan" -> EstimateRange(20, 21) // fixed 20s scan window
            "ble_scan" -> EstimateRange(20, 21) // fixed 20s scan window
            "sdr_sweep" -> EstimateRange(15, 20)
            "subdomain_finder" -> EstimateRange(10, 60)
            "cloud_scanner" -> EstimateRange(5, 30)
            "website_inspector" -> EstimateRange(5, 20)
            "tls_scanner" -> EstimateRange(5, 20)
            "shodan_search" -> EstimateRange(2, 8)
            "hibp_audit" -> EstimateRange(2, 8)
            "evidence_capture" -> EstimateRange(0, 0) // manual/user-driven, no fixed duration
            else -> EstimateRange(5, 15)
        }
    }

    /**
     * Sums the estimate ranges for a list of step ids.
     */
    fun totalEstimate(stepIds: Collection<String>, hostCount: Int? = null, deviceCount: Int? = null): EstimateRange {
        var low = 0
        var high = 0
        stepIds.forEach { id ->
            val estimate = estimateFor(id, hostCount, deviceCount)
            low += estimate.lowSeconds
            high += estimate.highSeconds
        }
        return EstimateRange(low, high)
    }
}
