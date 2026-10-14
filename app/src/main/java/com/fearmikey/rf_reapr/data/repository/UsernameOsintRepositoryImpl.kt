package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.domain.model.UsernameCheckResult
import com.fearmikey.rf_reapr.domain.model.UsernameSitesDatabase
import com.fearmikey.rf_reapr.domain.repository.UsernameOsintRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request

class UsernameOsintRepositoryImpl(
    private val okHttpClient: OkHttpClient
) : UsernameOsintRepository {

    override fun checkUsername(username: String): Flow<UsernameCheckResult> = channelFlow {
        val trimmed = username.trim()
        if (trimmed.isEmpty()) return@channelFlow

        // Limit concurrent HTTP queries to 5 to prevent socket exhaustion and aggressive rate limiting
        val semaphore = Semaphore(5)

        UsernameSitesDatabase.sites.forEach { site ->
            launch(Dispatchers.IO) {
                semaphore.withPermit {
                    val checkUrl = site.getCheckUrl(trimmed)
                    val profileUrl = site.getProfileUrl(trimmed)
                    try {
                        val request = Request.Builder()
                            .url(checkUrl)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,application/json;q=0.8,*/*;q=0.7")
                            .build()

                        okHttpClient.newCall(request).execute().use { response ->
                            val exists = when (response.code) {
                                200 -> true
                                404 -> false
                                301, 302 -> {
                                    // Handle possible redirects
                                    val location = response.header("Location") ?: ""
                                    !location.contains("login") && !location.contains("404")
                                }
                                else -> false
                            }

                            send(
                                UsernameCheckResult(
                                    site = site,
                                    username = trimmed,
                                    exists = exists,
                                    profileUrl = profileUrl,
                                    responseCode = response.code
                                )
                            )
                        }
                    } catch (e: Exception) {
                        send(
                            UsernameCheckResult(
                                site = site,
                                username = trimmed,
                                exists = false,
                                profileUrl = profileUrl,
                                error = e.localizedMessage ?: "Connection error"
                            )
                        )
                    }
                }
            }
        }
    }
}
