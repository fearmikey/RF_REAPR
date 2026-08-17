package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.domain.model.ShodanHostReport
import com.fearmikey.rf_reapr.domain.model.ShodanSearchResponse
import com.fearmikey.rf_reapr.domain.repository.SettingsRepository
import com.fearmikey.rf_reapr.domain.repository.ShodanRepository
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class ShodanRepositoryImpl(
    private val okHttpClient: OkHttpClient,
    private val settingsRepository: SettingsRepository
) : ShodanRepository {

    private val gson = Gson()
    private val baseUrl = "https://api.shodan.io"

    override suspend fun getHostInfo(ip: String): Result<ShodanHostReport> = withContext(Dispatchers.IO) {
        val apiKey = settingsRepository.shodanApiKey.first()
        if (apiKey.isBlank()) return@withContext Result.failure(Exception("Missing Shodan API Key in settings"))

        val request = Request.Builder()
            .url("$baseUrl/shodan/host/$ip?key=$apiKey")
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            when (response.code) {
                200 -> {
                    val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response body"))
                    Result.success(gson.fromJson(body, ShodanHostReport::class.java))
                }
                401 -> Result.failure(Exception("Invalid API Key or unauthorized"))
                403 -> Result.failure(Exception("Access denied (You may need a paid API plan)"))
                404 -> Result.failure(Exception("No information available for that IP"))
                429 -> Result.failure(Exception("Rate limit exceeded. Please wait."))
                else -> Result.failure(Exception("Server error: ${response.code}"))
            }
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    override suspend fun search(query: String, page: Int): Result<ShodanSearchResponse> = withContext(Dispatchers.IO) {
        val apiKey = settingsRepository.shodanApiKey.first()
        if (apiKey.isBlank()) return@withContext Result.failure(Exception("Missing Shodan API Key in settings"))

        val request = Request.Builder()
            .url("$baseUrl/shodan/host/search?key=$apiKey&query=$query&page=$page")
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            when (response.code) {
                200 -> {
                    val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response body"))
                    Result.success(gson.fromJson(body, ShodanSearchResponse::class.java))
                }
                401 -> Result.failure(Exception("Invalid API Key or unauthorized"))
                403 -> Result.failure(Exception("Access denied or insufficient query credits on your plan"))
                429 -> Result.failure(Exception("Rate limit exceeded. Please wait."))
                else -> Result.failure(Exception("Server error: ${response.code}"))
            }
        } catch (e: IOException) {
            Result.failure(e)
        }
    }
}
