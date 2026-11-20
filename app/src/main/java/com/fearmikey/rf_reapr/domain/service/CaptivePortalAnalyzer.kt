package com.fearmikey.rf_reapr.domain.service

data class PortalProbe(
    val name: String,
    val url: String,
    val expectedCode: Int,
    /** Substring the body must contain (null = body not checked). */
    val expectedBody: String? = null
) {
    val isHttps: Boolean get() = url.startsWith("https://")
}

data class PortalProbeResult(
    val probe: PortalProbe,
    val code: Int? = null,
    val location: String? = null,
    val bodySnippet: String? = null,
    val proxyHeaders: List<String> = emptyList(),
    val elapsedMs: Long = 0,
    val error: String? = null,
    val tlsError: Boolean = false
)

enum class ProbeVerdict { OK, REDIRECTED, MODIFIED, TLS_INTERCEPTED, FAILED }

enum class PortalStatus { OPEN, CAPTIVE_PORTAL, INTERCEPTED, OFFLINE, PARTIAL }

data class PortalVerdict(
    val status: PortalStatus,
    val title: String,
    val detail: String,
    val loginUrl: String? = null
)

/** System view of the network from NetworkCapabilities. */
data class SystemNetworkState(
    val transport: String,
    val validated: Boolean,
    val captivePortalFlag: Boolean,
    val venueInfoUrl: String? = null,
    val userPortalUrl: String? = null
)

object CaptivePortalAnalyzer {

    val PROBES = listOf(
        PortalProbe("Google (HTTP)", "http://connectivitycheck.gstatic.com/generate_204", 204),
        PortalProbe("Apple (HTTP)", "http://captive.apple.com/hotspot-detect.html", 200, "Success"),
        PortalProbe("Firefox (HTTP)", "http://detectportal.firefox.com/success.txt", 200, "success"),
        PortalProbe("Microsoft (HTTP)", "http://www.msftconnecttest.com/connecttest.txt", 200, "Microsoft Connect Test"),
        PortalProbe("Google (HTTPS)", "https://www.gstatic.com/generate_204", 204)
    )

    private val proxyHeaderNames = listOf("via", "x-cache", "x-squid-error", "x-proxy-id", "proxy-connection", "x-forwarded-for", "x-bluecoat-via")

    fun proxyHeaders(headers: Map<String, String>): List<String> =
        headers.filterKeys { it.lowercase() in proxyHeaderNames }.map { "${it.key}: ${it.value}" }

    fun verdictFor(r: PortalProbeResult): ProbeVerdict = when {
        r.tlsError -> ProbeVerdict.TLS_INTERCEPTED
        r.code == null -> ProbeVerdict.FAILED
        r.code in 300..399 -> ProbeVerdict.REDIRECTED
        r.code != r.probe.expectedCode -> ProbeVerdict.MODIFIED
        r.probe.expectedBody != null && r.bodySnippet?.contains(r.probe.expectedBody, ignoreCase = true) != true -> ProbeVerdict.MODIFIED
        else -> ProbeVerdict.OK
    }

    fun evaluate(results: List<PortalProbeResult>, system: SystemNetworkState?): PortalVerdict {
        val verdicts = results.associateWith { verdictFor(it) }
        val http = verdicts.filterKeys { !it.probe.isHttps }
        val https = verdicts.filterKeys { it.probe.isHttps }
        val loginUrl = system?.userPortalUrl
            ?: results.firstOrNull { verdictFor(it) == ProbeVerdict.REDIRECTED }?.location

        if (verdicts.values.all { it == ProbeVerdict.FAILED }) {
            return PortalVerdict(
                PortalStatus.OFFLINE,
                "No internet access",
                "None of the check servers could be reached. The network may be offline, or blocks these hosts." +
                    (if (system?.captivePortalFlag == true) " Android reports a captive portal on this network." else "")
            )
        }
        val redirected = http.count { it.value == ProbeVerdict.REDIRECTED }
        val modified = http.count { it.value == ProbeVerdict.MODIFIED }
        val tls = https.values.any { it == ProbeVerdict.TLS_INTERCEPTED }
        val proxied = results.flatMap { it.proxyHeaders }.distinct()

        return when {
            system?.captivePortalFlag == true || redirected > 0 || (modified > 0 && https.values.any { it != ProbeVerdict.OK }) -> PortalVerdict(
                PortalStatus.CAPTIVE_PORTAL,
                "Captive portal detected",
                buildString {
                    append("HTTP requests are being redirected or answered by the network instead of the real servers. ")
                    append("You probably need to sign in or accept terms before you get internet access.")
                    if (system?.captivePortalFlag == true) append(" Android also flags this network as a captive portal.")
                },
                loginUrl
            )
            tls -> PortalVerdict(
                PortalStatus.INTERCEPTED,
                "HTTPS interception detected",
                "The HTTPS check failed certificate validation. Something on the network is presenting its own certificate (a TLS-inspecting firewall, portal, or attacker). Don't enter passwords on this network."
            )
            modified > 0 -> PortalVerdict(
                PortalStatus.INTERCEPTED,
                "HTTP traffic is being modified",
                "$modified HTTP check${if (modified == 1) "" else "s"} returned unexpected content, while HTTPS works. A transparent proxy or content filter is rewriting unencrypted traffic." +
                    (if (proxied.isNotEmpty()) " Proxy headers: ${proxied.joinToString()}." else "")
            )
            verdicts.values.any { it == ProbeVerdict.FAILED } -> PortalVerdict(
                PortalStatus.PARTIAL,
                "Internet works, some checks blocked",
                "Most checks passed, but ${verdicts.count { it.value == ProbeVerdict.FAILED }} couldn't connect. The network may block specific hosts."
            )
            proxied.isNotEmpty() -> PortalVerdict(
                PortalStatus.PARTIAL,
                "Open internet through a proxy",
                "Content wasn't altered, but responses carry proxy headers (${proxied.joinToString()}), so HTTP traffic passes through a caching proxy."
            )
            else -> PortalVerdict(
                PortalStatus.OPEN,
                "Direct internet access",
                "Every check returned the exact expected response. No portal or interception detected." +
                    (if (system != null && !system.validated) " (Android hasn't validated this network yet.)" else "")
            )
        }
    }
}
