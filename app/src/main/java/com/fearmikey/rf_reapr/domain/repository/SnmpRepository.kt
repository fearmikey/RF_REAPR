package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.model.SnmpResult
import com.fearmikey.rf_reapr.domain.model.SnmpVarBind

interface SnmpRepository {
    suspend fun queryDevice(
        ipAddress: String,
        community: String,
        version: Int = 1 // 0 for V1, 1 for V2c
    ): Result<SnmpResult>

    /** True if the agent answers a sysDescr GET with this community (short timeout). */
    suspend fun probeCommunity(ipAddress: String, community: String, version: Int = 1): Boolean

    /** Walks the subtree under [rootOid], returning at most [limit] bindings. */
    suspend fun walk(ipAddress: String, community: String, version: Int, rootOid: String, limit: Int = 500): Result<List<SnmpVarBind>>
}
