package com.example.rf_reapr.domain.repository

import com.example.rf_reapr.domain.model.RdapDomainInfo
import kotlinx.coroutines.flow.Flow

interface RdapRepository {
    fun queryDomain(domain: String): Flow<RdapStatus>

    sealed class RdapStatus {
        object Idle : RdapStatus()
        object Loading : RdapStatus()
        data class Success(val info: RdapDomainInfo) : RdapStatus()
        data class Error(val message: String) : RdapStatus()
    }
}
