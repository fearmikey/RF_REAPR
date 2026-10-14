package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.model.UsernameCheckResult
import kotlinx.coroutines.flow.Flow

interface UsernameOsintRepository {
    fun checkUsername(username: String): Flow<UsernameCheckResult>
}
