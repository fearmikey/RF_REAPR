package com.fearmikey.rf_reapr.domain.repository

import kotlinx.coroutines.flow.Flow

data class TracerouteHop(
    val hopNumber: Int,
    val ip: String?,
    val hostname: String?,
    val latencyMs: Long?,
    val isFinal: Boolean = false
)

interface TracerouteRepository {
    fun traceroute(host: String): Flow<List<TracerouteHop>>
}
