package com.example.rf_reapr.domain.repository

import com.example.rf_reapr.domain.model.DnsEnumerationResult
import com.example.rf_reapr.domain.model.HttpSecurityResult
import com.example.rf_reapr.domain.model.RdapDomainInfo
import com.example.rf_reapr.domain.model.TlsAuditResult
import kotlinx.coroutines.flow.Flow

interface WebsiteInspectorRepository {
    fun inspectWebsite(url: String): Flow<WebsiteInspectorStatus>

    data class WebsiteInspectorStatus(
        val httpStatus: InspectorStatus<HttpSecurityResult> = InspectorStatus.Idle,
        val tlsStatus: InspectorStatus<TlsAuditResult> = InspectorStatus.Idle,
        val dnsStatus: InspectorStatus<DnsEnumerationResult> = InspectorStatus.Idle,
        val rdapStatus: InspectorStatus<RdapDomainInfo> = InspectorStatus.Idle
    )

    sealed class InspectorStatus<out T> {
        object Idle : InspectorStatus<Nothing>()
        object Loading : InspectorStatus<Nothing>()
        data class Success<T>(val data: T) : InspectorStatus<T>()
        data class Error(val message: String) : InspectorStatus<Nothing>()
    }
}
