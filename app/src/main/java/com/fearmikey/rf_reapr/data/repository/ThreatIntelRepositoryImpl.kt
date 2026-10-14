package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.domain.model.ThreatIntelReport
import com.fearmikey.rf_reapr.domain.model.ThreatIntelResult
import com.fearmikey.rf_reapr.domain.repository.ThreatIntelRepository
import com.fearmikey.rf_reapr.domain.repository.ThreatIntelStatus
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

class ThreatIntelRepositoryImpl(
    private val okHttpClient: OkHttpClient
) : ThreatIntelRepository {

    override suspend fun checkIp(ipAddress: String, apiKey: String): ThreatIntelStatus = withContext(Dispatchers.IO) {
        val trimmedIp = ipAddress.trim()
        val trimmedKey = apiKey.trim()

        if (trimmedIp.isEmpty()) {
            return@withContext ThreatIntelStatus.Error("Please enter an IP address")
        }

        if (trimmedKey.isEmpty()) {
            return@withContext ThreatIntelStatus.Error("AbuseIPDB API key is required. Please set it in Settings -> 3rd Party Service API Keys.")
        }

        val urlBuilder = "https://api.abuseipdb.com/api/v2/check".toHttpUrlOrNull()?.newBuilder()
            ?: return@withContext ThreatIntelStatus.Error("Invalid API URL")

        urlBuilder.addQueryParameter("ipAddress", trimmedIp)
        urlBuilder.addQueryParameter("maxAgeInDays", "90")
        urlBuilder.addQueryParameter("verbose", "")

        val url = urlBuilder.build()

        val request = Request.Builder()
            .url(url)
            .header("Key", trimmedKey)
            .header("Accept", "application/json")
            .header("User-Agent", "RF-REAPR-Toolkit/1.0 (Android; Security-Audit-Tool)")
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    return@withContext when (code) {
                        401 -> ThreatIntelStatus.Error("Invalid API Key (401 Unauthorized)")
                        429 -> ThreatIntelStatus.Error("API Rate Limit Exceeded (429 Too Many Requests)")
                        else -> ThreatIntelStatus.Error("AbuseIPDB request failed with HTTP $code")
                    }
                }

                val body = response.body?.string() ?: return@withContext ThreatIntelStatus.Error("Empty response from AbuseIPDB")
                val json = JsonParser.parseString(body).asJsonObject
                val data = json.getAsJsonObject("data") ?: return@withContext ThreatIntelStatus.Error("Invalid JSON structure")

                val hostnamesList = mutableListOf<String>()
                data.getAsJsonArray("hostnames")?.forEach {
                    if (!it.isJsonNull) hostnamesList.add(it.asString)
                }

                val reportsList = mutableListOf<ThreatIntelReport>()
                data.getAsJsonArray("reports")?.forEach {
                    if (it.isJsonObject) {
                        val obj = it.asJsonObject
                        val categories = mutableListOf<Int>()
                        obj.getAsJsonArray("categories")?.forEach { cat ->
                            if (cat.isJsonPrimitive) categories.add(cat.asInt)
                        }
                        reportsList.add(
                            ThreatIntelReport(
                                reportedAt = obj.get("reportedAt")?.asString ?: "",
                                comment = obj.get("comment")?.asString ?: "",
                                categories = categories,
                                reporterCountryCode = if (obj.has("reporterCountryCode") && !obj.get("reporterCountryCode").isJsonNull) obj.get("reporterCountryCode").asString else null
                            )
                        )
                    }
                }

                val result = ThreatIntelResult(
                    ipAddress = data.get("ipAddress")?.asString ?: trimmedIp,
                    isPublic = data.get("isPublic")?.asBoolean ?: true,
                    ipVersion = data.get("ipVersion")?.asInt ?: 4,
                    isWhitelisted = data.get("isWhitelisted")?.asBoolean ?: false,
                    abuseConfidenceScore = data.get("abuseConfidenceScore")?.asInt ?: 0,
                    countryCode = if (data.has("countryCode") && !data.get("countryCode").isJsonNull) data.get("countryCode").asString else null,
                    countryName = if (data.has("countryName") && !data.get("countryName").isJsonNull) data.get("countryName").asString else null,
                    usageType = if (data.has("usageType") && !data.get("usageType").isJsonNull) data.get("usageType").asString else null,
                    isp = if (data.has("isp") && !data.get("isp").isJsonNull) data.get("isp").asString else null,
                    domain = if (data.has("domain") && !data.get("domain").isJsonNull) data.get("domain").asString else null,
                    hostnames = hostnamesList,
                    totalReports = data.get("totalReports")?.asInt ?: 0,
                    numDistinctUsers = data.get("numDistinctUsers")?.asInt ?: 0,
                    lastReportedAt = if (data.has("lastReportedAt") && !data.get("lastReportedAt").isJsonNull) data.get("lastReportedAt").asString else null,
                    reports = reportsList
                )

                ThreatIntelStatus.Success(result)
            }
        } catch (e: Exception) {
            ThreatIntelStatus.Error(e.localizedMessage ?: "Network error during threat check")
        }
    }
}
