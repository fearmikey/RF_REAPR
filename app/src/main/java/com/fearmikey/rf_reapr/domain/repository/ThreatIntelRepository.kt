package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.model.ThreatIntelResult

sealed class ThreatIntelStatus {
    data object Idle : ThreatIntelStatus()
    data object Loading : ThreatIntelStatus()
    data class Success(val result: ThreatIntelResult) : ThreatIntelStatus()
    data class Error(val message: String) : ThreatIntelStatus()
}

interface ThreatIntelRepository {
    suspend fun checkIp(ipAddress: String, apiKey: String): ThreatIntelStatus
}
