package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.model.Breach
import com.fearmikey.rf_reapr.domain.model.Paste

interface HibpRepository {
    suspend fun getBreachedAccounts(account: String): Result<List<Breach>>
    suspend fun getPastes(account: String): Result<List<Paste>>
}
