package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.domain.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class CredentialTesterRepositoryImpl(
    private val okHttpClient: OkHttpClient
) : CredentialTesterRepository {

    override fun testCredentials(
        target: String,
        protocol: AuthProtocol,
        credentials: List<CredentialPair>
    ): Flow<TestResult> = flow {
        
        val total = credentials.size
        
        credentials.forEachIndexed { index, pair ->
            emit(TestResult.Progress(pair, total, index))
            
            val success = when (protocol) {
                AuthProtocol.HTTP_BASIC -> testHttpBasic(target, pair)
                else -> {
                    delay(500) // Simulate work for other protocols
                    false
                }
            }
            
            if (success) {
                emit(TestResult.Success(pair))
                return@flow
            }
            
            delay(200) // Rate limiting
        }
        
        emit(TestResult.Finished)
    }.flowOn(Dispatchers.IO)

    private fun testHttpBasic(target: String, pair: CredentialPair): Boolean {
        val url = if (target.startsWith("http")) target else "http://$target"
        val credential = Credentials.basic(pair.username, pair.password)
        
        val request = Request.Builder()
            .url(url)
            .header("Authorization", credential)
            .build()
            
        return try {
            val response = okHttpClient.newCall(request).execute()
            // 200 or 302 usually indicates success if auth was required
            // A 401 means definitely failed.
            response.code != 401
        } catch (e: IOException) {
            false
        }
    }
}
