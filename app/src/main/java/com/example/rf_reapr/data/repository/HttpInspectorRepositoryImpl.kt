package com.example.rf_reapr.data.repository

import com.example.rf_reapr.domain.model.HttpSecurityResult
import com.example.rf_reapr.domain.model.RiskLevel
import com.example.rf_reapr.domain.model.SecurityMisconfiguration
import com.example.rf_reapr.domain.repository.HttpInspectorRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request

class HttpInspectorRepositoryImpl(
    private val client: OkHttpClient
) : HttpInspectorRepository {

    override fun inspectUrl(url: String): Flow<HttpInspectorRepository.InspectorResult> = flow {
        emit(HttpInspectorRepository.InspectorResult.Loading)
        try {
            val formattedUrl = if (!url.startsWith("http")) "https://$url" else url
            val request = Request.Builder().url(formattedUrl).head().build()
            
            client.newCall(request).execute().use { response ->
                val headers = response.headers.toMap()
                val misconfigs = analyzeHeaders(headers)
                
                emit(HttpInspectorRepository.InspectorResult.Success(
                    HttpSecurityResult(
                        url = formattedUrl,
                        statusCode = response.code,
                        headers = headers,
                        misconfigurations = misconfigs
                    )
                ))
            }
        } catch (e: Exception) {
            emit(HttpInspectorRepository.InspectorResult.Error(e.message ?: "Unknown error occurred"))
        }
    }.flowOn(Dispatchers.IO)

    private fun analyzeHeaders(headers: Map<String, String>): List<SecurityMisconfiguration> {
        val misconfigs = mutableListOf<SecurityMisconfiguration>()
        // Normalize headers to case-insensitive keys for comparison
        val normalizedHeaders = headers.mapKeys { it.key.lowercase() }

        if (!normalizedHeaders.containsKey("content-security-policy")) {
            misconfigs.add(SecurityMisconfiguration(
                "Content-Security-Policy",
                "Header is missing",
                RiskLevel.MEDIUM,
                "Implement a strict CSP to prevent XSS and data injection attacks."
            ))
        }

        if (!normalizedHeaders.containsKey("x-frame-options")) {
            misconfigs.add(SecurityMisconfiguration(
                "X-Frame-Options",
                "Header is missing",
                RiskLevel.LOW,
                "Set X-Frame-Options to DENY or SAMEORIGIN to prevent Clickjacking."
            ))
        }

        if (!normalizedHeaders.containsKey("strict-transport-security")) {
            misconfigs.add(SecurityMisconfiguration(
                "Strict-Transport-Security",
                "HSTS is not enabled",
                RiskLevel.MEDIUM,
                "Enable HSTS to force HTTPS connections and prevent SSL stripping."
            ))
        }

        if (!normalizedHeaders.containsKey("x-content-type-options")) {
            misconfigs.add(SecurityMisconfiguration(
                "X-Content-Type-Options",
                "Header is missing",
                RiskLevel.LOW,
                "Set to 'nosniff' to prevent MIME-type sniffing."
            ))
        }

        return misconfigs
    }
}
