package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.domain.repository.ThreatIntelRepository
import com.fearmikey.rf_reapr.domain.repository.ThreatIntelStatus
import com.fearmikey.rf_reapr.domain.service.AbuseIpDbParser
import com.fearmikey.rf_reapr.domain.service.PublicIpGuard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class ThreatIntelRepositoryImpl(
    private val okHttpClient: OkHttpClient
) : ThreatIntelRepository {

    override suspend fun checkIp(ipAddress: String, apiKey: String): ThreatIntelStatus = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        if (ipAddress.isBlank()) return@withContext ThreatIntelStatus.Error("Enter an IP address.")

        // Validate locally first: nothing is sent for hostnames or internal addresses.
        val ip = when (val verdict = PublicIpGuard.check(ipAddress)) {
            is PublicIpGuard.Verdict.Public -> verdict.ip
            is PublicIpGuard.Verdict.NotRoutable -> return@withContext ThreatIntelStatus.Error(PublicIpGuard.refusal(verdict, "AbuseIPDB"))
            PublicIpGuard.Verdict.NotAnIp -> return@withContext ThreatIntelStatus.Error("\"${ipAddress.trim()}\" is not an IPv4 or IPv6 address.")
        }
        if (key.isEmpty()) {
            return@withContext ThreatIntelStatus.Error("An AbuseIPDB API key is required. Set it under Settings → API Keys.")
        }

        val url = "https://api.abuseipdb.com/api/v2/check".toHttpUrl().newBuilder()
            .addQueryParameter("ipAddress", ip)
            .addQueryParameter("maxAgeInDays", "90")
            .addQueryParameter("verbose", "")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Key", key)
            .header("Accept", "application/json")
            .header("User-Agent", "RF-REAPR (Android security toolkit)")
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = AbuseIpDbParser.parseError(body)
                    return@withContext ThreatIntelStatus.Error(
                        when (response.code) {
                            401 -> "AbuseIPDB rejected the API key (401). Check it under Settings → API Keys."
                            422 -> detail ?: "AbuseIPDB could not process that address (422)."
                            429 -> "Daily AbuseIPDB quota used up (429)." +
                                (response.header("Retry-After")?.toLongOrNull()?.let { " Try again in ${it / 60 + 1} min." } ?: "")
                            else -> detail ?: "AbuseIPDB request failed with HTTP ${response.code}."
                        }
                    )
                }
                AbuseIpDbParser.parse(body, ip)?.let { ThreatIntelStatus.Success(it) }
                    ?: ThreatIntelStatus.Error("AbuseIPDB returned a response that could not be read.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ThreatIntelStatus.Error(e.localizedMessage ?: "Network error while contacting AbuseIPDB.")
        }
    }
}
