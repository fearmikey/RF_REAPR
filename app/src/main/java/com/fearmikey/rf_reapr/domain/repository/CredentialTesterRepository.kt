package com.fearmikey.rf_reapr.domain.repository

import kotlinx.coroutines.flow.Flow

interface CredentialTesterRepository {
    fun testCredentials(
        target: String,
        protocol: AuthProtocol,
        credentials: List<CredentialPair>
    ): Flow<TestResult>
}

data class CredentialPair(val username: String, val password: String)

enum class AuthProtocol {
    WEB_AUTO, HTTP_BASIC, HTTP_FORM, SSH, FTP, TELNET
}

sealed class TestResult {
    data class Progress(val current: CredentialPair, val total: Int, val index: Int) : TestResult()
    data class Success(val credential: CredentialPair) : TestResult()
    data class PairFailed(val pair: CredentialPair) : TestResult()
    data class Debug(val message: String) : TestResult()
    data class Failure(val message: String) : TestResult()
    data object Finished : TestResult()
}
