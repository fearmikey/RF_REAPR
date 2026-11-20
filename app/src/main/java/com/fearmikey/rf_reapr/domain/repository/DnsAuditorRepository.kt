package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.service.CheckStatus
import com.fearmikey.rf_reapr.domain.service.DnsAuditEvaluator
import com.fearmikey.rf_reapr.domain.service.DnsCheck
import com.fearmikey.rf_reapr.domain.service.DnsObservations

data class DnsAuditResult(
    val observations: DnsObservations,
    val checks: List<DnsCheck>,
    val durationMs: Long
) {
    val overall: CheckStatus get() = DnsAuditEvaluator.overall(checks)
    val systemDnsServers: List<String> get() = observations.dnsServers
}

interface DnsAuditorRepository {
    suspend fun auditDns(): DnsAuditResult
}
