package com.fearmikey.rf_reapr.domain.repository

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow

interface SubdomainFinderRepository {
    fun findSubdomains(domain: String): Flow<SubdomainResult>

    @Immutable
    data class SubdomainResult(
        val subdomains: List<SubdomainItem> = emptyList(),
        val progress: Float = 0f,
        val isFinished: Boolean = false,
        val error: String? = null
    )

    @Immutable
    data class SubdomainItem(
        val hostname: String,
        val ipAddress: String?,
        val source: DiscoverySource = DiscoverySource.BRUTE_FORCE,
        /** CNAME target, when the name is an alias. */
        val cname: String? = null,
        /** Set when the CNAME points at a self-service hosting platform (see SubdomainTakeoverAnalyzer). */
        val takeover: com.fearmikey.rf_reapr.domain.service.TakeoverHint? = null
    )

    enum class DiscoverySource {
        BRUTE_FORCE,
        PASSIVE
    }

    @Immutable
    data class SubdomainListWrapper(
        val items: List<SubdomainItem> = emptyList()
    )
}
