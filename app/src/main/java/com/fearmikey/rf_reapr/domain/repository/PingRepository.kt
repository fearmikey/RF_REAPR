package com.fearmikey.rf_reapr.domain.repository

import kotlinx.coroutines.flow.Flow

data class PingOptions(
    val host: String,
    /** Number of echo requests; null = continuous until cancelled. */
    val count: Int? = 4,
    /** Seconds between requests. Values below 0.2 require root, so they aren't offered. */
    val intervalSec: Float = 1f,
    /** ICMP payload size in bytes (56 = default, 1472 + 28 header bytes = 1500 MTU). */
    val packetSize: Int = 56,
    /** Seconds to wait for each reply. */
    val timeoutSec: Int = 2,
    /** Set the Don't Fragment bit (for path-MTU probing). */
    val dontFragment: Boolean = false
)

interface PingRepository {
    fun ping(options: PingOptions): Flow<PingStatus>

    sealed class PingStatus {
        data object Idle : PingStatus()
        data object Loading : PingStatus()
        data class Progress(val line: String) : PingStatus()
        data class Success(val fullOutput: String, val exitCode: Int = 0) : PingStatus()
        data class Error(val message: String) : PingStatus()
    }
}
