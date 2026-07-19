package com.fearmikey.rf_reapr.domain.repository

import kotlinx.coroutines.flow.Flow

interface CloudAssetScannerRepository {
    fun scanAssets(domain: String): Flow<CloudScanResult>

    data class CloudScanResult(
        val discoveredAssets: List<CloudAsset> = emptyList(),
        val progress: Float = 0f,
        val isFinished: Boolean = false,
        val error: String? = null
    )

    data class CloudAsset(
        val platform: String,
        val url: String,
        val status: String,
        val isPublic: Boolean
    )
}
