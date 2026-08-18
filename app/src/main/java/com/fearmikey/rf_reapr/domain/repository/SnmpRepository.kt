package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.model.SnmpResult
import kotlinx.coroutines.flow.Flow

interface SnmpRepository {
    suspend fun queryDevice(
        ipAddress: String,
        community: String,
        version: Int = 1 // 0 for V1, 1 for V2c
    ): Result<SnmpResult>
}
