package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.model.RiskLevel
import kotlinx.coroutines.flow.Flow

interface TlsCipherScannerRepository {
    fun scanCiphers(url: String): Flow<TlsScanResult>

    data class TlsScanResult(
        val supportedCiphers: List<CipherSuiteInfo> = emptyList(),
        val progress: Float = 0f,
        val isFinished: Boolean = false,
        val error: String? = null
    )

    data class CipherSuiteInfo(
        val protocol: String,
        val cipherSuite: String,
        val riskLevel: RiskLevel,
        val reason: String? = null
    )
}
