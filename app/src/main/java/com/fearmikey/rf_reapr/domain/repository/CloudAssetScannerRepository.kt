package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.model.FindingDraft
import com.fearmikey.rf_reapr.domain.service.BucketState
import com.fearmikey.rf_reapr.domain.service.CloudProvider
import kotlinx.coroutines.flow.Flow

interface CloudAssetScannerRepository {
    /** Probes cloud storage names derived from [domain]. Cancelling the collector stops the scan. */
    fun scanAssets(domain: String, options: CloudScanOptions = CloudScanOptions()): Flow<CloudScanResult>

    data class CloudScanOptions(
        val providers: Set<CloudProvider> = CloudProvider.entries.filter { it.enabledByDefault }.toSet(),
        /** Extra name fragments on top of the built-in list (product names, team names, …). */
        val extraKeywords: List<String> = emptyList()
    )

    data class CloudScanResult(
        val discoveredAssets: List<CloudAsset> = emptyList(),
        val progress: Float = 0f,
        val isFinished: Boolean = false,
        val error: String? = null,
        val checked: Int = 0,
        val total: Int = 0,
        /** Probes that got no usable answer (timeouts, connection errors). */
        val failedProbes: Int = 0
    )

    /**
     * [platform], [url] and [status] are also the keys of stored WEB_CLOUD log entries that the
     * report builder reads, so their names must not change.
     */
    data class CloudAsset(
        val platform: String,
        val url: String,
        val status: String,
        val isPublic: Boolean,
        val state: BucketState = BucketState.UNKNOWN,
        val name: String = "",
        val host: String = "",
        val sampleKeys: List<String> = emptyList(),
        /** Finding for anonymously readable assets; not part of the stored log. */
        @Transient val draft: FindingDraft? = null
    )
}
