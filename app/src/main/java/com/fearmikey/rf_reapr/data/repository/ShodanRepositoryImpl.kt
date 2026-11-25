package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.domain.model.ShodanHostReport
import com.fearmikey.rf_reapr.domain.model.ShodanSearchResponse
import com.fearmikey.rf_reapr.domain.repository.SettingsRepository
import com.fearmikey.rf_reapr.domain.repository.ShodanRepository
import com.fearmikey.rf_reapr.domain.service.ShodanParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class ShodanRepositoryImpl(
    private val okHttpClient: OkHttpClient,
    private val settingsRepository: SettingsRepository
) : ShodanRepository {

    private val baseUrl = "https://api.shodan.io".toHttpUrl()

    override suspend fun getHostInfo(ip: String): Result<ShodanHostReport> = call(
        buildUrl = { key -> baseUrl.newBuilder().addPathSegments("shodan/host").addPathSegment(ip).addQueryParameter("key", key).build() },
        notFound = "Shodan has no data for $ip. It may never have been scanned, or nothing was open.",
        parse = ShodanParser::parseHost
    )

    override suspend fun search(query: String, page: Int): Result<ShodanSearchResponse> = call(
        // HttpUrl encodes the query, so operators, quotes and spaces can't break out of the parameter.
        buildUrl = { key ->
            baseUrl.newBuilder().addPathSegments("shodan/host/search")
                .addQueryParameter("key", key)
                .addQueryParameter("query", query)
                .addQueryParameter("page", page.coerceAtLeast(1).toString())
                .build()
        },
        notFound = "No results.",
        parse = ShodanParser::parseSearch
    )

    private suspend fun <T : Any> call(
        buildUrl: (key: String) -> HttpUrl,
        notFound: String,
        parse: (String) -> T?
    ): Result<T> = withContext(Dispatchers.IO) {
        val apiKey = settingsRepository.shodanApiKey.first().trim()
        if (apiKey.isEmpty()) return@withContext Result.failure(Exception("Set your Shodan API key under Settings → API Keys first."))
        try {
            okHttpClient.newCall(Request.Builder().url(buildUrl(apiKey)).build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val detail = ShodanParser.parseError(body)
                when (response.code) {
                    200 -> parse(body)?.let { Result.success(it) }
                        ?: Result.failure(Exception("Shodan returned a response that could not be read."))
                    401 -> Result.failure(Exception(detail ?: "Shodan rejected the API key."))
                    403 -> Result.failure(Exception(detail ?: "Access denied. This request needs a paid plan or more query credits."))
                    404 -> Result.failure(Exception(notFound))
                    429 -> Result.failure(Exception("Shodan rate limit reached (1 request per second). Wait a moment and retry."))
                    else -> Result.failure(Exception(detail ?: "Shodan request failed with HTTP ${response.code}."))
                }
            }
        } catch (e: IOException) {
            // The request URL carries the API key, so never surface OkHttp's message verbatim.
            Result.failure(Exception("Could not reach Shodan (${e.javaClass.simpleName})."))
        }
    }
}
