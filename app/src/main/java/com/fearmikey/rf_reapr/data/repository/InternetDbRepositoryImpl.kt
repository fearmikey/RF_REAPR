package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.domain.model.InternetDbResponse
import com.fearmikey.rf_reapr.domain.repository.InternetDbRepository
import com.fearmikey.rf_reapr.domain.service.ShodanParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class InternetDbRepositoryImpl(
    private val okHttpClient: OkHttpClient
) : InternetDbRepository {

    private val baseUrl = "https://internetdb.shodan.io".toHttpUrl()

    override suspend fun getIpInfo(ip: String): Result<InternetDbResponse> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(baseUrl.newBuilder().addPathSegment(ip).build()).build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when (response.code) {
                    200 -> ShodanParser.parseInternetDb(body)?.let { Result.success(it) }
                        ?: Result.failure(Exception("InternetDB returned a response that could not be read."))
                    404 -> Result.failure(Exception("InternetDB has no data for $ip: Shodan has not seen open ports on it."))
                    429 -> Result.failure(Exception("InternetDB rate limit reached. Wait a moment and retry."))
                    else -> Result.failure(Exception(ShodanParser.parseError(body) ?: "InternetDB request failed with HTTP ${response.code}."))
                }
            }
        } catch (e: IOException) {
            Result.failure(Exception(e.localizedMessage ?: "Could not reach InternetDB."))
        }
    }
}
