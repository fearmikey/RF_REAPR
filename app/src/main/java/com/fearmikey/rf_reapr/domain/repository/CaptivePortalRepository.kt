package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.service.PortalProbeResult
import com.fearmikey.rf_reapr.domain.service.PortalVerdict
import com.fearmikey.rf_reapr.domain.service.SystemNetworkState

data class CaptivePortalCheck(
    val system: SystemNetworkState?,
    val results: List<PortalProbeResult>,
    val verdict: PortalVerdict
)

/** Captive portal / interception probing, shared by the detector screen and Audit Mode. */
interface CaptivePortalRepository {
    /** What Android itself thinks of the active network (validated / captive flag). */
    fun systemState(): SystemNetworkState?

    /** Runs every probe in CaptivePortalAnalyzer.PROBES in parallel and evaluates them. */
    suspend fun check(): CaptivePortalCheck
}
