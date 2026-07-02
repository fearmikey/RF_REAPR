package com.example.rf_reapr.domain.repository

import com.example.rf_reapr.domain.model.TlsAuditResult
import kotlinx.coroutines.flow.Flow

interface TlsAuditorRepository {
    fun auditUrl(url: String): Flow<AuditStatus>

    sealed class AuditStatus {
        object Loading : AuditStatus()
        data class Success(val result: TlsAuditResult) : AuditStatus()
        data class Error(val message: String) : AuditStatus()
    }
}
