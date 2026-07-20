package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.domain.model.Breach
import com.fearmikey.rf_reapr.domain.model.Paste
import com.fearmikey.rf_reapr.domain.repository.HibpRepository
import com.fearmikey.rf_reapr.domain.repository.SettingsRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class HibpRepositoryImpl(
    private val okHttpClient: OkHttpClient,
    private val settingsRepository: SettingsRepository
) : HibpRepository {

    private val gson = Gson()
    private val baseUrl = "https://haveibeenpwned.com/api/v3"

    override suspend fun getBreachedAccounts(account: String): Result<List<Breach>> = withContext(Dispatchers.IO) {
        val apiKey = settingsRepository.hibpApiKey.first()
        if (apiKey.isBlank()) return@withContext Result.failure(Exception("Missing HIBP API Key in settings"))

        val request = Request.Builder()
            .url("$baseUrl/breachedaccount/$account?truncateResponse=false")
            .addHeader("hibp-api-key", apiKey)
            .addHeader("user-agent", "RF_REAPR-Android-Toolkit")
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            when (response.code) {
                200 -> {
                    val body = response.body?.string() ?: return@withContext Result.success(emptyList())
                    val type = object : TypeToken<List<Breach>>() {}.type
                    Result.success(gson.fromJson(body, type))
                }
                404 -> Result.success(emptyList()) // No breaches found
                401 -> Result.failure(Exception("Invalid API Key"))
                429 -> Result.failure(Exception("Rate limit exceeded. Please wait."))
                else -> Result.failure(Exception("Server error: ${response.code}"))
            }
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    override suspend fun getPastes(account: String): Result<List<Paste>> = withContext(Dispatchers.IO) {
        val apiKey = settingsRepository.hibpApiKey.first()
        if (apiKey.isBlank()) return@withContext Result.failure(Exception("Missing HIBP API Key in settings"))

        val request = Request.Builder()
            .url("$baseUrl/pasteaccount/$account")
            .addHeader("hibp-api-key", apiKey)
            .addHeader("user-agent", "RF_REAPR-Android-Toolkit")
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            when (response.code) {
                200 -> {
                    val body = response.body?.string() ?: return@withContext Result.success(emptyList())
                    val type = object : TypeToken<List<Paste>>() {}.type
                    Result.success(gson.fromJson(body, type))
                }
                404 -> Result.success(emptyList()) // No pastes found
                401 -> Result.failure(Exception("Invalid API Key"))
                429 -> Result.failure(Exception("Rate limit exceeded. Please wait."))
                else -> Result.failure(Exception("Server error: ${response.code}"))
            }
        } catch (e: IOException) {
            Result.failure(e)
        }
    }
}
