package com.example.rf_reapr.domain.repository

import com.example.rf_reapr.domain.model.DnsEnumerationResult
import kotlinx.coroutines.flow.Flow

interface DnsEnumeratorRepository {
    fun enumerateDomain(domain: String): Flow<EnumerationStatus>

    sealed class EnumerationStatus {
        object Loading : EnumerationStatus()
        data class Success(val result: DnsEnumerationResult) : EnumerationStatus()
        data class Error(val message: String) : EnumerationStatus()
    }
}
