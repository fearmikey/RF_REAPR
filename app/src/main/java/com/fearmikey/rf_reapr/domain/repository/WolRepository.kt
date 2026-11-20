package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.model.WolTarget
import kotlinx.coroutines.flow.StateFlow

interface WolRepository {
    val savedTargets: StateFlow<List<WolTarget>>

    /**
     * Sends [repeat] magic packets to each of [ports] at [broadcast]. Returns the number of
     * datagrams sent.
     */
    suspend fun sendMagicPacket(packet: ByteArray, broadcast: String, ports: List<Int>, repeat: Int = 3): Result<Int>

    suspend fun saveTarget(target: WolTarget)
    suspend fun deleteTarget(id: String)

    /** Quick liveness check (ICMP / common TCP ports) used to verify a wake-up. */
    suspend fun isHostUp(ip: String): Boolean
}
