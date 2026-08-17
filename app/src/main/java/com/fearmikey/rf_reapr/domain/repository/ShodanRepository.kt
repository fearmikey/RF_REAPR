package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.model.ShodanHostReport
import com.fearmikey.rf_reapr.domain.model.ShodanSearchResponse

interface ShodanRepository {
    suspend fun getHostInfo(ip: String): Result<ShodanHostReport>
    suspend fun search(query: String, page: Int = 1): Result<ShodanSearchResponse>
}
