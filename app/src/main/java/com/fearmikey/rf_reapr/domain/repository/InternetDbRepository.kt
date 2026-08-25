package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.model.InternetDbResponse

interface InternetDbRepository {
    /**
     * Fast, no-API-key lookup for an IP address.
     */
    suspend fun getIpInfo(ip: String): Result<InternetDbResponse>
}
