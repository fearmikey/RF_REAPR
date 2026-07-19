package com.fearmikey.rf_reapr.domain.repository

data class DnsAuditResult(
    val systemDnsServers: List<String>,
    val canResolveKnownDomain: Boolean,
    val resolutionMatch: Boolean,
    val systemIp: String,
    val trustedIps: List<String>,
    val isPrivateIp: Boolean,
    val privateDnsMode: String?,
    val systemError: String? = null,
    val trustedError: String? = null,
    val resolvedHostname: String? = null,
    val isInDnsServerList: Boolean = systemDnsServers.contains(systemIp),
    val isLocalRedirection: Boolean = isInDnsServerList || isPrivateIp,
    val isHijacked: Boolean = !resolutionMatch && canResolveKnownDomain && !isLocalRedirection && systemError == null && trustedError == null,
    val isInconclusive: Boolean = systemError != null || trustedError != null || !canResolveKnownDomain || trustedIps.isEmpty(),
    val testDomain: String = ""
)

interface DnsAuditorRepository {
    suspend fun auditDns(): DnsAuditResult
}
