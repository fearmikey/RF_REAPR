package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.model.HidPayload
import kotlinx.coroutines.flow.Flow

interface HidRepository {
    fun getAllScripts(): Flow<List<HidPayload>>
    suspend fun getScriptById(id: Long): HidPayload?
    suspend fun saveScript(payload: HidPayload): Long
    suspend fun deleteScript(payload: HidPayload)
}
