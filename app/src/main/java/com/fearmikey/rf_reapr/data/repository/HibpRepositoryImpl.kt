package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.domain.model.Breach
import com.fearmikey.rf_reapr.domain.model.Paste
import com.fearmikey.rf_reapr.domain.repository.HibpRepository
import com.fearmikey.rf_reapr.domain.repository.SettingsRepository
import com.fearmikey.rf_reapr.domain.service.BreachAnalyzer
import com.fearmikey.rf_reapr.domain.service.HibpParser
import com.fearmikey.rf_reapr.domain.service.PwnedPasswords
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

class HibpRepositoryImpl(
    private val okHttpClient: OkHttpClient,
    private val settingsRepository: SettingsRepository
) : HibpRepository {

    private val baseUrl = "https://haveibeenpwned.com/api/v3".toHttpUrl()
    private val userAgent = "RF-REAPR (Android security toolkit)"

    override suspend fun getBreachedAccounts(account: String): Result<List<Breach>> = query(
        // addPathSegment percent-encodes the account, so "+", "/" and spaces in an address are safe.
        baseUrl.newBuilder().addPathSegment("breachedaccount").addPathSegment(account.trim())
            .addQueryParameter("truncateResponse", "false").build(),
        HibpParser::parseBreaches
    )

    override suspend fun getPastes(account: String): Result<List<Paste>> {
        // The paste API answers 400 for anything that isn't an email address.
        if (!BreachAnalyzer.isEmail(account)) return Result.success(emptyList())
        return query(
            baseUrl.newBuilder().addPathSegment("pasteaccount").addPathSegment(account.trim()).build(),
            HibpParser::parsePastes
        )
    }

    private suspend fun <T> query(url: HttpUrl, parse: (String) -> List<T>): Result<List<T>> = withContext(Dispatchers.IO) {
        val apiKey = settingsRepository.hibpApiKey.first().trim()
        if (apiKey.isEmpty()) return@withContext Result.failure(Exception("Set your Have I Been Pwned API key under Settings → API Keys first."))
        val request = Request.Builder().url(url)
            .header("hibp-api-key", apiKey)
            .header("user-agent", userAgent)
            .build()
        try {
            val first = execute(request, parse)
            // The breach and paste lookups run back to back, which trips the per-minute limit of the
            // smaller plans. Honour one short Retry-After instead of failing the second request.
            val wait = first.retryAfterSeconds
            if (wait != null && wait <= MAX_RETRY_WAIT_SECONDS) {
                delay((wait + 1).seconds)
                execute(request, parse).result
            } else {
                first.result
            }
        } catch (e: IOException) {
            Result.failure(Exception(e.localizedMessage ?: "Could not reach Have I Been Pwned."))
        }
    }

    private class Attempt<T>(val result: Result<List<T>>, val retryAfterSeconds: Long? = null)

    private fun <T> execute(request: Request, parse: (String) -> List<T>): Attempt<T> {
        val response = okHttpClient.newCall(request).execute()
        // Closed through the body rather than Response.close(), which throws for a body-less response.
        try {
            return when (response.code) {
                200 -> Attempt(Result.success(parse(response.body?.string().orEmpty())))
                404 -> Attempt(Result.success(emptyList())) // not in any breach / paste
                400 -> Attempt(Result.failure(Exception("Have I Been Pwned did not accept that account name.")))
                401 -> Attempt(Result.failure(Exception("Have I Been Pwned rejected the API key. Check it under Settings → API Keys.")))
                403 -> Attempt(Result.failure(Exception("Request blocked by Have I Been Pwned (403).")))
                429 -> {
                    val seconds = response.header("retry-after")?.toLongOrNull()
                    Attempt(
                        Result.failure(
                            Exception("Rate limit reached for your Have I Been Pwned plan." + (seconds?.let { " Retry in $it s." } ?: " Wait a few seconds."))
                        ),
                        seconds
                    )
                }
                503 -> Attempt(Result.failure(Exception("Have I Been Pwned is temporarily unavailable (503).")))
                else -> Attempt(Result.failure(Exception("Have I Been Pwned request failed with HTTP ${response.code}.")))
            }
        } finally {
            response.body?.close()
        }
    }

    override suspend fun pwnedPasswordCount(password: String): Result<Int> = withContext(Dispatchers.IO) {
        // Hash locally; only the 5-character prefix ever leaves the device.
        val hash = PwnedPasswords.sha1Hex(password)
        val request = Request.Builder()
            .url("https://api.pwnedpasswords.com/range/${PwnedPasswords.prefix(hash)}")
            // Padding makes every response the same size, so an observer can't infer the prefix's popularity.
            .header("Add-Padding", "true")
            .header("user-agent", userAgent)
            .build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.code == 200) Result.success(PwnedPasswords.match(hash, response.body?.string().orEmpty()))
                else Result.failure(Exception("Pwned Passwords request failed with HTTP ${response.code}."))
            }
        } catch (e: IOException) {
            Result.failure(Exception(e.localizedMessage ?: "Could not reach Pwned Passwords."))
        }
    }

    private companion object {
        const val MAX_RETRY_WAIT_SECONDS = 10L
    }
}
