package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.domain.model.InternetDbResponse
import com.fearmikey.rf_reapr.domain.repository.InternetDbRepository
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class InternetDbRepositoryImpl(
    private val okHttpClient: OkHttpClient
) : InternetDbRepository {

    private val gson = Gson()
    private val baseUrl = "https://internetdb.shodan.io"

    override suspend fun getIpInfo(ip: String): Result<InternetDbResponse> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/$ip")
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            when (response.code) {
                200 -> {
                    val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response body"))
                    Result.success(gson.fromJson(body, InternetDbResponse::class.java))
                }
                404 -> Result.failure(Exception("No information available for that IP in InternetDB"))
                else -> Result.failure(Exception("InternetDB error: ${response.code}"))
            }
        } catch (e: IOException) {
            Result.failure(e)
        }
    }
}
