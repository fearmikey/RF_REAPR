package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.model.OpenPort
import com.fearmikey.rf_reapr.domain.scanner.NetworkScanner
import kotlinx.coroutines.flow.Flow

/** Connect timeout / parallelism for a TCP connect scan. */
data class PortScanOptions(
    val connectTimeoutMs: Int = 600,
    val concurrency: Int = 64,
    val grabBanners: Boolean = true
)

interface PortScannerRepository : NetworkScanner<OpenPort> {
    /**
     * Stateless scan of [ports] on [host]. Each call is independent, so the Network
     * Discovery audit and the Port Scanner screen can't overwrite each other's target.
     * Cancel by cancelling the collecting coroutine.
     */
    fun scan(host: String, ports: List<Int>, options: PortScanOptions = PortScanOptions()): Flow<NetworkScanner.ScanResult<OpenPort>>

    @Deprecated("Shared mutable config; use scan(host, ports, options)")
    fun setConfig(ipAddress: String, portRange: IntRange)

    @Deprecated("Shared mutable config; use scan(host, ports, options)")
    fun setConfig(ipAddress: String, ports: List<Int>)
}
