package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.domain.model.UsernameCheckResult
import com.fearmikey.rf_reapr.domain.model.UsernameSite
import com.fearmikey.rf_reapr.domain.model.UsernameSitesDatabase
import com.fearmikey.rf_reapr.domain.model.UsernameStatus
import com.fearmikey.rf_reapr.domain.repository.UsernameOsintRepository
import com.fearmikey.rf_reapr.domain.service.UsernameCheckEvaluator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class UsernameOsintRepositoryImpl(
    okHttpClient: OkHttpClient
) : UsernameOsintRepository {

    private val client = okHttpClient.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    override fun checkUsername(username: String): Flow<UsernameCheckResult> = channelFlow {
        // Only a validated name is ever placed in a URL.
        val name = UsernameCheckEvaluator.normalize(username) ?: return@channelFlow

        // A handful of parallel requests is plenty and keeps the sites' rate limiters calm.
        val permits = Semaphore(MAX_CONCURRENT)
        UsernameSitesDatabase.sites.forEach { site ->
            launch {
                send(
                    if (!UsernameCheckEvaluator.isAllowedFor(site, name)) {
                        UsernameCheckResult(site, name, UsernameStatus.INVALID, site.getProfileUrl(name), note = "This site doesn't allow such a username")
                    } else {
                        permits.withPermit { check(site, name) }
                    }
                )
            }
        }
    }.flowOn(Dispatchers.Default)

    private suspend fun check(site: UsernameSite, name: String): UsernameCheckResult = suspendCancellableCoroutine { continuation ->
        val profileUrl = site.getProfileUrl(name)
        val request = Request.Builder()
            .url(site.getCheckUrl(name))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.8")
            .build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) {
                    continuation.resume(
                        UsernameCheckResult(site, name, UsernameStatus.ERROR, profileUrl, note = e.localizedMessage ?: "Connection error")
                    )
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val result = response.use {
                    // Markers sit near the top of the page; never pull a whole profile page into memory.
                    val body = runCatching { it.peekBody(BODY_LIMIT).string() }.getOrDefault("")
                    val verdict = UsernameCheckEvaluator.evaluate(site, name, it.code, body, it.request.url.toString())
                    UsernameCheckResult(site, name, verdict.status, profileUrl, it.code, verdict.note)
                }
                if (continuation.isActive) continuation.resume(result)
            }
        })
    }

    private companion object {
        const val MAX_CONCURRENT = 6
        const val BODY_LIMIT = 256L * 1024
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}
