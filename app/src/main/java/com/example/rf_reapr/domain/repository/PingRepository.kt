package com.example.rf_reapr.domain.repository

import kotlinx.coroutines.flow.Flow

interface PingRepository {
    fun ping(host: String, count: Int? = 4): Flow<PingStatus>

    sealed class PingStatus {
        object Idle : PingStatus()
        object Loading : PingStatus()
        data class Progress(val line: String) : PingStatus()
        data class Success(val fullOutput: String) : PingStatus()
        data class Error(val message: String) : PingStatus()
    }
}
