package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.model.RiskLevel
import kotlinx.coroutines.flow.Flow

interface TlsCipherScannerRepository {
    fun scanCiphers(url: String): Flow<TlsScanResult>

    data class TlsScanResult(
        val host: String = "",
        val port: Int = 443,
        val phase: String = "",
        val protocols: List<ProtocolSupport> = emptyList(),
        val supportedCiphers: List<CipherSuiteInfo> = emptyList(),
        val certificate: CertificateInfo? = null,
        val grade: String? = null,
        val progress: Float = 0f,
        val isFinished: Boolean = false,
        val error: String? = null
    )

    data class ProtocolSupport(
        val protocol: String,
        val enabled: Boolean,
        val riskLevel: RiskLevel,
        val note: String? = null
    )

    data class CipherSuiteInfo(
        val protocol: String,
        val cipherSuite: String,
        val riskLevel: RiskLevel,
        val forwardSecrecy: Boolean = false,
        val aead: Boolean = false,
        val reason: String? = null
    )

    data class CertificateInfo(
        val subject: String,
        val issuer: String,
        val notBefore: Long,
        val notAfter: Long,
        val daysUntilExpiry: Long,
        val expired: Boolean,
        val notYetValid: Boolean,
        val selfSigned: Boolean,
        val hostnameMatches: Boolean,
        val keyAlgorithm: String,
        val keyBits: Int,
        val signatureAlgorithm: String,
        val serialNumber: String,
        val sha256Fingerprint: String,
        val sans: List<String>,
        val chainLength: Int,
        val issues: List<CertIssue>
    )

    data class CertIssue(val severity: RiskLevel, val message: String)
}
